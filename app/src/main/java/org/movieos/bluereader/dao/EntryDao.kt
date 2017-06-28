package org.movieos.bluereader.dao;

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import org.movieos.bluereader.model.*
import java.util.*

@Dao
abstract class EntryDao {

    @Query("SELECT * FROM Entry WHERE id = :arg0")
    abstract fun entryById(id: Int): Entry?

    @Query("SELECT createdAt FROM Entry ORDER BY createdAt DESC LIMIT 1")
    abstract fun latestEntryDate(): Date?

    @Query("SELECT id FROM Entry WHERE unread = 1 AND feedId in (:arg0) ORDER BY published DESC LIMIT 300")
    abstract fun unreadVisible(feedIds: Array<Int>): List<Int>

    @Query("SELECT id FROM Entry WHERE starred = 1 AND feedId in (:arg0) ORDER BY published DESC LIMIT 300")
    abstract fun starredVisible(feedIds: Array<Int>): List<Int>

    @Query("SELECT id FROM Entry WHERE feedId in (:arg0) ORDER BY published DESC LIMIT 300")
    abstract fun allVisible(feedIds: Array<Int>): List<Int>

    @Query("UPDATE Entry SET unread = :arg0 WHERE id in (:arg1)")
    abstract fun updateUnreadState(unread: Boolean, entryIds: Array<Int>)

    @Query("SELECT id FROM Entry WHERE unread = 1")
    abstract fun getUnreadIds(): List<Int>

    @Query("UPDATE Entry SET starred = :arg0 WHERE id in (:arg1)")
    abstract fun updateStarredState(starred: Boolean, entryIds: Array<Int>)

    @Query("SELECT id FROM Entry WHERE starred = 1")
    abstract fun getStarredIds(): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun updateEntries(entries: Array<Entry>)

    data class UnreadRow(var feedId: Int = 0, var count: Int = 0)
    @Query("SELECT feedId, COUNT(*) as count FROM Entry WHERE unread = 1 GROUP BY 1")
    abstract fun countUnread(): List<UnreadRow>

    @Query("DELETE FROM Entry")
    abstract fun wipeEntries()

    @Query("UPDATE Entry SET starred = :arg1 WHERE id = :arg0")
    abstract fun setStarredInternal(id: Int, starred: Boolean)

    @Query("UPDATE Entry SET unread = :arg1 WHERE id = :arg0")
    abstract fun setUnreadInternal(id: Int, unread: Boolean)

    fun setUnread(id: Int, unread: Boolean) {
        if (entryById(id)?.unread != unread) {
            createLocalState(LocalState(id, unread, null))
            setUnreadInternal(id, unread)
        }
    }

    fun setStarred(id: Int, starred: Boolean) {
        if (entryById(id)?.starred != starred) {
            createLocalState(LocalState(id, null, starred))
            setStarredInternal(id, starred)
        }
    }




    @Query("SELECT * FROM Subscription ORDER BY id")
    abstract fun subscriptions(): List<Subscription>

    @Query("SELECT feedId FROM Subscription")
    abstract fun subscriptionFeedIds(): Array<Int>

    @Query("SELECT * FROM Subscription WHERE feedId = :arg0")
    abstract fun subscriptionForFeed(id: Int): Subscription?

    @Query("DELETE FROM Subscription")
    abstract fun wipeSubscriptions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun updateSubscriptions(subscriptions: Array<Subscription>)




    @Query("SELECT * FROM Tagging")
    abstract fun taggings(): List<Tagging>

    @Query("DELETE FROM Tagging")
    abstract fun wipeTaggings()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun updateTaggings(taggings: Array<Tagging>)










    @Query("SELECT * FROM LocalState ORDER BY timeStamp")
    abstract fun localState(): List<LocalState>

    @Delete
    abstract fun deleteLocalState(localState: Array<LocalState>)

    @Query("DELETE FROM LocalState")
    abstract fun wipeLocalState()

    @Insert(onConflict = OnConflictStrategy.FAIL)
    abstract fun createLocalState(localState: LocalState)





    @Query("SELECT * FROM SyncState ORDER BY id LIMIT 1")
    abstract fun watchSyncState(): LiveData<SyncState>

    @Query("SELECT * FROM SyncState ORDER BY id LIMIT 1")
    abstract fun syncState(): SyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun updateSyncState(syncState: SyncState)

    @Query("DELETE FROM SyncState")
    abstract fun wipeSyncState()

}
