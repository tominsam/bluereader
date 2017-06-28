package org.movieos.bluereader.utilities

import android.content.Context
import android.os.AsyncTask
import org.movieos.bluereader.BuildConfig
import org.movieos.bluereader.MainApplication
import org.movieos.bluereader.api.Feedbin
import org.movieos.bluereader.api.PageLinks
import org.movieos.bluereader.dao.MainDatabase
import org.movieos.bluereader.model.Entry
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

    val database: MainDatabase
        get() = (context.applicationContext as MainApplication).database

    private fun start() {
        executeOnExecutor(SYNC_EXECUTOR)
    }

    override fun doInBackground(vararg params: Void): SyncStatus {
        val api = Feedbin(context)
        try {
            // Push state. We store the date we pushed up until.
            pushState(api)

            if (!pushOnly) {
                // Pull server state
                getSubscriptions(api)
                getTaggings(api)
                getEntries(api)

                // update last sync date to be "now"
                database.entryDao().updateSyncState(SyncState(1, Date()))
            }

        } catch (e: Throwable) {
            Timber.e(e)
            if (pushOnly) {
                return SyncStatus(true, "Failed")
            } else {
                return SyncStatus(e)
            }
        }
        return SyncStatus(true, "Done")
    }

    override fun onProgressUpdate(vararg values: String) {
        for (value in values) {
            Timber.i(value)
            MainApplication.bus.post(SyncStatus(false, value))
        }
    }

    override fun onPostExecute(syncStatus: SyncStatus) {
        Timber.i("complete")
        MainApplication.bus.post(syncStatus)
    }

    private fun pushState(api: Feedbin) {
        val addStarred = HashSet<Int>()
        val removeStarred = HashSet<Int>()
        val addUnread = HashSet<Int>()
        val removeUnread = HashSet<Int>()

        val localStates = database.entryDao().localState()
        if (localStates.isEmpty()) {
            Timber.i("No local state to push")
            return
        }

        publishProgress("Pushing local state")

        // We'll roll through the list of local state changes and build
        // authoritative lists of "last touch" state - if you star something
        // and unstar it in the same loop, we'll only record the last change
        // you made.
        for (localState in localStates) {
            Timber.i("pushing $localState")
            val id = localState.entryId
            val markStarred = localState.markStarred
            if (markStarred != null) {
                if (markStarred) {
                    addStarred.add(id)
                    removeStarred.remove(id)
                } else {
                    addStarred.remove(id)
                    removeStarred.add(id)
                }
            }
            val markUnread = localState.markUnread
            if (markUnread != null) {
                if (markUnread) {
                    addUnread.add(id)
                    removeUnread.remove(id)
                } else {
                    addUnread.remove(id)
                    removeUnread.add(id)
                }
            }
        }

        // Push state to the server
        if (!addStarred.isEmpty()) {
            Timber.i("addStarred $addStarred")
            api.addStarred(addStarred).execute()
        }
        if (!removeStarred.isEmpty()) {
            Timber.i("removeStarred $removeStarred")
            api.removeStarred(removeStarred).execute()
        }
        if (!addUnread.isEmpty()) {
            Timber.i("addUnread $addUnread")
            api.addUnread(addUnread).execute()
        }
        if (!removeUnread.isEmpty()) {
            Timber.i("removeUnread $removeUnread")
            api.removeUnread(removeUnread).execute()
        }

        // and delete the local state objects that we've handled
        if (!localStates.isEmpty()) {
            database.entryDao().deleteLocalState(localStates.toTypedArray())
        }
    }

    private fun getSubscriptions(api: Feedbin) {
        publishProgress("Syncing subscriptions")
        val subscriptions = api.subscriptions().execute()
        database.entryDao().wipeSubscriptions()
        database.entryDao().updateSubscriptions(subscriptions.body().toTypedArray())
    }

    private fun getTaggings(api: Feedbin) {
        publishProgress("Syncing tags")
        val taggings = api.taggings().execute()
        for (tagging in taggings.body()) {
            tagging.subscription = database.entryDao().subscriptionForFeed(tagging.feedId)
        }
        database.entryDao().wipeTaggings()
        database.entryDao().updateTaggings(taggings.body().toTypedArray())
    }

    private fun getEntries(api: Feedbin) {
        // We need these first so we can add new entries in the right state
        publishProgress("Syncing unread state")
        val unread = api.unread().execute().body()

        publishProgress("Syncing starred state")
        val starred = api.starred().execute().body()

        publishProgress("Syncing entries")
        var latestEntry = database.entryDao().latestEntryDate()
        if (database.entryDao().syncState() == null) {
            // ignore the incremental sync stuff until we have at least one successful sync
            latestEntry = null
        }

        var entries = api.entries(latestEntry).execute()
        var entryCount = 0
        while (true) {
            insertEntries(unread, starred, entries)

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
        publishProgress("Updating unread state")
        // super tiresome SQL here for speed purposes.
        database.entryDao().updateUnreadState(true, unread.toTypedArray())
        database.entryDao().updateUnreadState(false, (database.entryDao().getUnreadIds() - unread).toTypedArray())
        publishProgress("Updating starred state")
        database.entryDao().updateStarredState(true, starred.toTypedArray())
        database.entryDao().updateStarredState(false, (database.entryDao().getStarredIds() - starred).toTypedArray())

        // Update the unread count of each subscription object
        publishProgress("Updating unread counts")
        val unreadCounts = mutableMapOf<Int, Int>()
        for ((feedId, count) in database.entryDao().countUnread()) {
            unreadCounts.put(feedId, count)
        }
        for (subscription in database.entryDao().subscriptions()) {
            subscription.unreadCount = unreadCounts[subscription.feedId] ?: 0
            database.entryDao().updateSubscriptions(arrayOf(subscription))
        }

        // Finally, if there's anything in the unread or starred lists we haven't seen,
        // fetch those explicitly (eg, unread or starred entries more than MAX_ENTRIES_COUNT
        // into the past - we want those lists to be complete no matter how far back we need
        // to go
        publishProgress("Backfilling")
        var missing: List<Int> = listOf()
        missing += unread.filter { database.entryDao().entryById(it) == null }
        missing += starred.filter { database.entryDao().entryById(it) == null }
        missing = missing.distinct()

        while (!missing.isEmpty()) {
            publishProgress("Backfilling " + missing.size + " entries")
            val page = missing.sliceSafely(0, CATCHUP_SIZE)
            missing = missing.sliceSafely(CATCHUP_SIZE, missing.size)
            val missingEntries = api.entries(page).execute()
            insertEntries(unread, starred, missingEntries)
        }

    }

    private fun insertEntries(unread: List<Int>, starred: List<Int>, response: Response<List<Entry>>) {
        for (entry in response.body()) {
            // Set the right read/unread state
            entry.starred = starred.contains(entry.id)
            entry.unread = unread.contains(entry.id)
        }
        // Insert all entries at once
        database.entryDao().updateEntries(response.body().toTypedArray())
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

        override fun toString(): String {
            return "<SyncStatus $isComplete $status $exception>"
        }
    }

    companion object {

        val SYNC_EXECUTOR: Executor = Executors.newSingleThreadExecutor()

        private val CATCHUP_SIZE = 20
        private val MAX_ENTRIES_COUNT = if (BuildConfig.DEBUG) 10_000 else 2_000

        fun sync(context: Context, force: Boolean, pushOnly: Boolean) {
            if (Settings.getCredentials(context) == null) {
                return
            }
            val state = (context.applicationContext as MainApplication).database.entryDao().syncState()
            if (state == null || state.isStale || force || pushOnly) {
                Timber.i("Syncing")
                SyncTask(context.applicationContext, pushOnly).start()
            } else {
                Timber.i("Not syncing")
            }
        }
    }
}
