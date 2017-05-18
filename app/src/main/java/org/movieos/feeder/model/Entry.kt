package org.movieos.feeder.model

import android.content.Context
import android.os.Handler
import com.google.gson.annotations.SerializedName
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.annotations.Required
import org.movieos.feeder.utilities.SyncTask
import java.util.*

open class Entry : RealmObject(), IntegerPrimaryKey {
    @PrimaryKey
    @SerializedName("id")
    override var id: Int = 0

    @Index
    @SerializedName("feed_id")
    var feedId: Int = 0
        internal set

    @SerializedName("title")
    var title: String? = null
        internal set

    @SerializedName("url")
    var url: String? = null
        internal set

    @SerializedName("author")
    var author: String? = null
        internal set

    @SerializedName("content")
    var content: String? = null
        internal set

    @SerializedName("summary")
    var summary: String? = null
        internal set

    @Required
    @SerializedName("created_at")
    var createdAt: Date? = null
        internal set

    @Required
    @SerializedName("published")
    var published: Date? = null
        internal set

    var unreadFromServer: Boolean = false

    var starredFromServer: Boolean = false

    var subscription: Subscription? = null

    var locallyUpdated: Date? = null

    enum class ViewType {
        UNREAD,
        STARRED,
        ALL
    }

    val displayAuthor: String
        get() {
            if (subscription == null) {
                return ""
            } else if (author == null) {
                return subscription!!.title!!
            } else {
                return String.format("%s - %s", subscription!!.title, author)
            }
        }

    val locallyStarred: Boolean
        get() {
            Realm.getDefaultInstance().use { r ->
                var localStarred = starredFromServer
                LocalState.forEntry(r, this)
                        .filter { it.markStarred != null }
                        .forEach { localStarred = it.markStarred!! }
                return localStarred;
            }
        }

    val locallyUnread: Boolean
        get() {
            Realm.getDefaultInstance().use { r ->
                var localUnread = unreadFromServer
                LocalState.forEntry(r, this)
                        .filter { it.markUnread != null }
                        .forEach { localUnread = it.markUnread!! }
                return localUnread
            }
        }

    override fun toString(): String {
        return String.format(Locale.US, "<Entry %d %s>", id, title)
    }

    companion object {

        fun byId(realm: Realm, id: Int): Entry? {
            return realm.where(Entry::class.java).equalTo("id", id).findFirst()
        }

        fun setStarred(context: Context, realm: Realm, entry: Entry, starred: Boolean) {
            val id = entry.id
            realm.executeTransactionAsync { r ->
                r.copyToRealm(LocalState(id, null, starred))
                // Arbitrary object change to kick UI
                byId(r, id)?.locallyUpdated = Date()
                if (starred) {
                    // If the object is starred, add it to the starred query immediately,
                    // otherwise we won't update the local state till the server syncs
                    byId(r, id)?.starredFromServer = starred
                }
            }
            // Wait longer when unstarring things
            Handler().postDelayed({ SyncTask.sync(context, false, true) }, if (starred) 5_000L else 20_000L)
        }

        fun setUnread(context: Context, realm: Realm, entry: Entry, unread: Boolean) {
            val id = entry.id
            realm.executeTransactionAsync { r ->
                r.copyToRealm(LocalState(id, unread, null))
                // Arbitrary object change to kick UI
                byId(r, id)?.locallyUpdated = Date()
                if (unread) {
                    // If the object is unread, add it to the unread query immediately,
                    // otherwise we won't update the local state till the server syncs
                    byId(r, id)?.unreadFromServer = unread
                }
            }
            // wait longer when reading things (as it's a passive action)
            Handler().postDelayed({ SyncTask.sync(context, false, true) }, if (unread) 5_000L else 20_000L)
        }
    }
}
