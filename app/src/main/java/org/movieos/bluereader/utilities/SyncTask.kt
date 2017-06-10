package org.movieos.bluereader.utilities

import android.content.Context
import android.os.AsyncTask
import io.realm.Realm
import io.realm.Sort
import org.movieos.bluereader.MainApplication
import org.movieos.bluereader.api.Feedbin
import org.movieos.bluereader.api.PageLinks
import org.movieos.bluereader.model.Entry
import org.movieos.bluereader.model.LocalState
import org.movieos.bluereader.model.Subscription
import org.movieos.bluereader.model.SyncState
import retrofit2.Response
import timber.log.Timber
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class SyncTask private constructor(
        val context: Context,
        val pushOnly: Boolean
) : AsyncTask<Void, String, SyncTask.SyncStatus>() {

    private fun start() {
        executeOnExecutor(SYNC_EXECUTOR)
    }

    override fun doInBackground(vararg params: Void): SyncStatus {
        onProgressUpdate("Syncing...")
        val api = Feedbin(context)
        val realm = Realm.getDefaultInstance()
        try {
            // Push state. We store the date we pushed up until.
            pushState(api, realm)

            if (!pushOnly) {
                // Pull server state
                getSubscriptions(api, realm)
                getTaggings(api, realm)
                getEntries(api, realm)

                // update last sync date to be "now"
                realm.executeTransaction { it.copyToRealmOrUpdate(SyncState(1, Date())) }
            }

        } catch (e: Throwable) {
            Timber.e(e)
            if (pushOnly) {
                return SyncStatus(true, "Failed")
            } else {
                return SyncStatus(e)
            }
        } finally {
            realm.close()
        }
        return SyncStatus(true, "Done")
    }

    override fun onProgressUpdate(vararg values: String) {
        for (value in values) {
            MainApplication.bus.post(SyncStatus(false, value))
        }
    }

    override fun onPostExecute(syncStatus: SyncStatus) {
        MainApplication.bus.post(syncStatus)
    }

    private fun pushState(api: Feedbin, realm: Realm) {
        publishProgress("Pushing local state")
        val addStarred = HashSet<Int>()
        val removeStarred = HashSet<Int>()
        val addUnread = HashSet<Int>()
        val removeUnread = HashSet<Int>()

        // make list solid here so that later changes are predictable
        val localStates = ArrayList(LocalState.all(realm))

        // We'll roll through the list of local state changes and build
        // authoritative lists of "last touch" state - if you star something
        // and unstar it in the same loop, we'll only record the last change
        // you made.
        for (localState in localStates) {
            val id = localState.entryId
            val markStarred = localState.markStarred
            if (markStarred != null) {
                addStarred.remove(id)
                removeStarred.remove(id)
                if (markStarred) {
                    addStarred.add(id)
                } else {
                    removeStarred.add(id)
                }
            }
            val markUnread = localState.markUnread
            if (markUnread != null) {
                addUnread.remove(id)
                removeUnread.remove(id)
                if (markUnread) {
                    addUnread.add(id)
                } else {
                    removeUnread.add(id)
                }
            }
        }

        // Push state to the server
        if (!addStarred.isEmpty()) {
            api.addStarred(addStarred).execute()
        }
        if (!removeStarred.isEmpty()) {
            api.removeStarred(removeStarred).execute()
        }
        if (!addUnread.isEmpty()) {
            api.addUnread(addUnread).execute()
        }
        if (!removeUnread.isEmpty()) {
            api.removeUnread(removeUnread).execute()
        }

        // and delete the local state objects that we've handled
        if (!localStates.isEmpty()) {
            realm.executeTransaction {
                for (localState in localStates) {
                    localState.deleteFromRealm()
                }
            }
        }
    }

    private fun getSubscriptions(api: Feedbin, realm: Realm) {
        // TODO remove subscriptions
        publishProgress("Syncing subscriptions")
        val subscriptions = api.subscriptions(null).execute()
        realm.executeTransaction { it.copyToRealmOrUpdate(subscriptions.body()) }
    }

    private fun getTaggings(api: Feedbin, realm: Realm) {
        // TODO remove tags
        publishProgress("Syncing tags")
        val taggings = api.taggings().execute()
        for (tagging in taggings.body()) {
            tagging.subscription = realm.where(Subscription::class.java).equalTo("feedId", tagging.feedId).findFirst()
        }

        realm.executeTransaction { it.copyToRealmOrUpdate(taggings.body()) }
    }

    private fun getEntries(api: Feedbin, realm: Realm) {
        // We need these first so we can add new entries in the right state
        publishProgress("Syncing unread state")
        val unread = api.unread().execute().body()
        publishProgress("Syncing starred state")
        val starred = api.starred().execute().body()

        publishProgress("Syncing entries")
        val latestEntry = realm.where(Entry::class.java).findAllSorted("createdAt", Sort.DESCENDING).first(null)
        var entriesSince: Date? = latestEntry?.createdAt
        if (SyncState.latest(realm).findFirst() == null) {
            // ignore the incremental sync stuff until we have at least one successful sync
            entriesSince = null
        }

        var entries = api.entries(entriesSince).execute()
        var entryCount = 0
        while (true) {
            insertEntries(realm, unread, starred, entries)

            // Is there another page?
            val links = PageLinks(entries.raw())
            if (links.next == null) break

            // Post progress count so the user feels ok about this
            entryCount += entries.body().size
            if (entryCount >= MAX_ENTRIES_COUNT) {
                // Ok, we've fetched enough. Probably.
                break
            }
            publishProgress(String.format(Locale.getDefault(), "Syncing entries (%d)", entryCount))

            // Fetch next page and loop again
            entries = api.entriesPaginate(links.next!!).execute()
        }

        // Now we need to update everything in the database - the server doesn't return entries
        // new in the sync list just because their unread state changed.
        //
        // We'll also count global unread entries at this point
        realm.executeTransaction { r ->
            val unreadCounts = mutableMapOf<Int, Int>()
            for (entry in r.where(Entry::class.java).findAll()) {
                entry.unread = unread.contains(entry.id)
                entry.starred = starred.contains(entry.id)
                if (entry.unread) {
                    unreadCounts[entry.feedId] = (unreadCounts[entry.feedId] ?: 0) + 1
                }
            }
            for (subscription in r.where(Subscription::class.java).findAll()) {
                subscription.unreadCount = unreadCounts[subscription.feedId] ?: 0
            }
        }

        // Finally, if there's anything in the unread or starred lists we haven't seen,
        // fetch those explicitly (eg, unread or starred entries more than MAX_ENTRIES_COUNT
        // into the past - we want those lists to be complete no matter how far back we need
        // to go
        var missing: List<Int> = listOf()
        missing += unread.filter { Entry.byId(realm, it).findFirst() == null }
        missing += starred.filter { Entry.byId(realm, it).findFirst() == null }
        missing = missing.distinct()

        while (!missing.isEmpty()) {
            publishProgress("Backfilling " + missing.size + " entries")
            val page = missing.sliceSafely(0, CATCHUP_SIZE)
            missing = missing.sliceSafely(CATCHUP_SIZE, missing.size)
            val missingEntries = api.entries(page).execute()
            insertEntries(realm, unread, starred, missingEntries)
        }

    }

    private fun insertEntries(realm: Realm, unread: List<Int>, starred: List<Int>, response: Response<List<Entry>>) {
        for (entry in response.body()) {
            // Connect entries to their subscriptions
            entry.subscription = realm.where(Subscription::class.java).equalTo("feedId", entry.feedId).findFirst()
            // Set the right read/unread state
            entry.starred = starred.contains(entry.id)
            entry.unread = unread.contains(entry.id)
        }
        // Insert all entries at once
        realm.executeTransaction { it.copyToRealmOrUpdate(response.body()) }
    }

    class SyncStatus {
        var isComplete: Boolean = false
            internal set
        var status: String
            internal set
        var exception: Throwable? = null
            internal set

        constructor(complete: Boolean, status: String) {
            isComplete = complete
            this.status = status
            exception = null
        }

        constructor(exception: Throwable) {
            isComplete = true
            this.exception = exception
            status = exception.message ?: exception.toString()
        }
    }

    companion object {

        val SYNC_EXECUTOR: Executor = Executors.newSingleThreadExecutor()

        private val CATCHUP_SIZE = 20
        private val MAX_ENTRIES_COUNT = 10_000

        fun sync(context: Context, force: Boolean, pushOnly: Boolean) {
            if (Settings.getCredentials(context) == null) {
                return
            }
            val realm = Realm.getDefaultInstance()
            val state = SyncState.latest(realm).findFirst()
            if (state == null || state.isStale || force || pushOnly) {
                Timber.i("Syncing")
                SyncTask(context.applicationContext, pushOnly).start()
            } else {
                Timber.i("Not syncing")
            }
            realm.close()
        }
    }
}
