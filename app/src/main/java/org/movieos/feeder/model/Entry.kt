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

    var unread: Boolean = false

    var starred: Boolean = false

    var subscription: Subscription? = null

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
                byId(r, id)?.starred = starred
            }
            // Wait longer when unstarring things
            Handler().postDelayed({ SyncTask.sync(context, false, true) }, if (starred) 5_000L else 20_000L)
        }

        fun setUnread(context: Context, realm: Realm, entry: Entry, unread: Boolean) {
            val id = entry.id
            realm.executeTransactionAsync { r ->
                r.copyToRealm(LocalState(id, unread, null))
                byId(r, id)?.unread = unread
            }
            // wait longer when reading things (as it's a passive action)
            Handler().postDelayed({ SyncTask.sync(context, false, true) }, if (unread) 5_000L else 20_000L)
        }
    }
}
