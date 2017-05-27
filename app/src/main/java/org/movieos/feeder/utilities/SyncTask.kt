package org.movieos.feeder.utilities

import android.content.Context
import android.os.AsyncTask
import io.realm.Realm
import io.realm.Sort
import org.movieos.feeder.FeederApplication
import org.movieos.feeder.api.Feedbin
import org.movieos.feeder.api.PageLinks
import org.movieos.feeder.model.Entry
import org.movieos.feeder.model.LocalState
import org.movieos.feeder.model.Subscription
import org.movieos.feeder.model.SyncState
import retrofit2.Response
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class SyncTask private constructor(internal val context: Context, internal val pushOnly: Boolean) : AsyncTask<Void, String, SyncTask.SyncStatus>() {

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
            FeederApplication.bus.post(SyncStatus(false, value))
        }
    }

    override fun onPostExecute(syncStatus: SyncStatus) {
        FeederApplication.bus.post(syncStatus)
    }

    @Throws(IOException::class)
    private fun pushState(api: Feedbin, realm: Realm) {
        publishProgress("Pushing local state")
        val addStarred = HashSet<Int>()
        val removeStarred = HashSet<Int>()
        val addUnread = HashSet<Int>()
        val removeUnread = HashSet<Int>()

        // make list solid here so that later changes are predictable
        val localStates = ArrayList(LocalState.all(realm))

        realm.executeTransaction {
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
        }

        if (!addStarred.isEmpty()) {
            api.addStarred(addStarred).execute()
            realm.executeTransaction { r ->
                for (entry in r.where(Entry::class.java).`in`("id", addStarred.toTypedArray()).findAll()) {
                    entry.starred = true
                }
            }
        }
        if (!removeStarred.isEmpty()) {
            api.removeStarred(removeStarred).execute()
            realm.executeTransaction { r ->
                for (entry in r.where(Entry::class.java).`in`("id", removeStarred.toTypedArray()).findAll()) {
                    entry.starred = false
                }
            }
        }
        if (!addUnread.isEmpty()) {
            api.addUnread(addUnread).execute()
            realm.executeTransaction { r ->
                for (entry in r.where(Entry::class.java).`in`("id", addUnread.toTypedArray()).findAll()) {
                    entry.unread = true
                }
            }
        }
        if (!removeUnread.isEmpty()) {
            api.removeUnread(removeUnread).execute()
            realm.executeTransaction { r ->
                for (entry in r.where(Entry::class.java).`in`("id", removeUnread.toTypedArray()).findAll()) {
                    entry.unread = false
                }
            }
        }

        if (!localStates.isEmpty()) {
            realm.executeTransaction {
                for (localState in localStates) {
                    localState.deleteFromRealm()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun getSubscriptions(api: Feedbin, realm: Realm) {
        publishProgress("Syncing subscriptions")
//        val latestSubscription = realm.where(Subscription::class.java).findAllSorted("createdAt", Sort.DESCENDING).first(null)
//        val subscriptionsSince = latestSubscription?.createdAt
        val subscriptions = api.subscriptions(null).execute()
        realm.executeTransaction { it.copyToRealmOrUpdate(subscriptions.body()) }
    }

    @Throws(IOException::class)
    private fun getTaggings(api: Feedbin, realm: Realm) {
        publishProgress("Syncing tags")
        val taggings = api.taggings().execute()
        realm.executeTransaction { it.copyToRealmOrUpdate(taggings.body()) }
    }

    @Throws(IOException::class)
    private fun getEntries(api: Feedbin, realm: Realm) {
        // We need these first so we can add new entries in the right state
        publishProgress("Syncing unread state")
        val unread = api.unread().execute().body()
        publishProgress("Syncing starred state")
        val starred = api.starred().execute().body()

        var entryCount = 0
        publishProgress("Syncing entries")
        val latestEntry = realm.where(Entry::class.java).findAllSorted("createdAt", Sort.DESCENDING).first(null)
        var entriesSince: Date? = latestEntry?.createdAt
        if (SyncState.latest(realm) == null) {
            // ignore the incremental sync stuff until we have at least one successful sync
            entriesSince = null
        }
        var entries = api.entries(entriesSince).execute()
        while (true) {
            val finalResponse = entries
            insertEntries(realm, unread, starred, finalResponse)
            val links = PageLinks(entries.raw())
            if (links.next == null) {
                break
            }
            entryCount += entries.body().size
            publishProgress(String.format(Locale.getDefault(), "Syncing entries (%d)", entryCount))
            if (links.next == null) {
                break
            }
            entries = api.entriesPaginate(links.next!!).execute()
            if (entryCount >= MAX_ENTRIES_COUNT) {
                // TODO handle better
                break
            }
        }

        // Now we need to re-up everything in the database, because there's no way of knowing
        // if the server changed the starred / unread state of something - we won't have seen
        // it in the API response if it's not new.
        realm.executeTransaction { r ->
            for (entry in r.where(Entry::class.java).findAll()) {
                entry.unread = unread.contains(entry.id)
                entry.starred = starred.contains(entry.id)
            }
        }

        // Finally, if there's anything in the unread or starred lists we haven't seen,
        // fetch those explicitly.
        var missing: List<Int> = ArrayList()
        for (integer in unread) {
            if (Entry.byId(realm, integer) == null) {
                missing += integer
            }
        }
        for (integer in starred) {
            if (Entry.byId(realm, integer) == null) {
                missing += integer
            }
        }
        while (!missing.isEmpty()) {
            publishProgress("Backfilling " + missing.size + " entries")
            val page = missing.sliceSafely(0, CATCHUP_SIZE)
            missing = missing.sliceSafely(CATCHUP_SIZE, missing.size)
            val missingEntries = api.entries(page).execute()
            insertEntries(realm, unread, starred, missingEntries)
        }

    }

    private fun insertEntries(realm: Realm, unread: List<Int>, starred: List<Int>, finalResponse: Response<List<Entry>>) {
        for (entry in finalResponse.body()) {
            // Connect entries to their subscriptions
            entry.subscription = realm.where(Subscription::class.java).equalTo("feedId", entry.feedId).findFirst()
            // Create entries with the right read/unread state
            entry.starred = starred.contains(entry.id)
            entry.unread = unread.contains(entry.id)
        }
        realm.executeTransaction { it.copyToRealmOrUpdate(finalResponse.body()) }
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

        private val CATCHUP_SIZE = 10
        private val MAX_ENTRIES_COUNT = 1000

        fun sync(context: Context, force: Boolean, pushOnly: Boolean) {
            val realm = Realm.getDefaultInstance()
            val state = SyncState.latest(realm)
            if (state == null || state.isStale || force || pushOnly) {
                SyncTask(context, pushOnly).start()
            }
            realm.close()
        }
    }
}
