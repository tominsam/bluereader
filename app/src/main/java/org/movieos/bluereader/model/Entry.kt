package org.movieos.bluereader.model

import android.content.Context
import android.os.Handler
import com.google.gson.annotations.SerializedName
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.annotations.Required
import org.movieos.bluereader.utilities.SyncTask
import java.util.*

open class Entry : RealmObject() {
    @PrimaryKey
    @SerializedName("id")
    var id: Int = 0

    @Index
    @SerializedName("feed_id")
    var feedId: Int = 0

    @SerializedName("title")
    var title: String? = null

    @SerializedName("url")
    var url: String? = null

    @SerializedName("author")
    var author: String? = null

    @SerializedName("content")
    var content: String? = null

    @SerializedName("summary")
    var summary: String? = null

    val excerpt: String
        get() = summary?.substring(0, Math.min(summary?.length ?: 0, 200)) ?: ""

    @Required
    @SerializedName("created_at")
    var createdAt: Date? = null

    @Required
    @SerializedName("published")
    var published: Date? = null

    var unread: Boolean = false

    var starred: Boolean = false

    var subscription: Subscription? = null

    enum class ViewType {
        FEEDS,
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

        fun byId(realm: Realm, id: Int): RealmQuery<Entry> {
            return realm.where(Entry::class.java).equalTo("id", id)
        }

        fun setStarred(context: Context, realm: Realm, entry: Entry, starred: Boolean) {
            val id = entry.id
            realm.executeTransactionAsync { r ->
                r.copyToRealm(LocalState(id, null, starred))
                byId(r, id).findFirst()?.starred = starred
            }
            // Wait longer when unstarring things
            Handler().postDelayed({ SyncTask.sync(context, false, true) }, if (starred) 5_000L else 20_000L)
        }

        fun setUnread(context: Context, realm: Realm, entry: Entry, unread: Boolean) {
            val id = entry.id
            realm.executeTransactionAsync { r ->
                r.copyToRealm(LocalState(id, unread, null))
                byId(r, id).findFirst()?.unread = unread
            }
            // wait longer when reading things (as it's a passive action)
            Handler().postDelayed({ SyncTask.sync(context, false, true) }, if (unread) 5_000L else 20_000L)
        }
    }
}
