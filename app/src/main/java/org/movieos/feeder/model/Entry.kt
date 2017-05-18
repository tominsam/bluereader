package org.movieos.feeder.model

import android.content.Context
import android.os.Handler
import android.support.annotation.UiThread
import com.google.gson.annotations.SerializedName
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.Sort
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

    val isLocallyStarred: Boolean
        get() {
            val realm = Realm.getDefaultInstance()
            var localStarred = starredFromServer
            for (local in LocalState.forEntry(realm, this)) {
                if (local.markStarred != null) {
                    localStarred = local.markStarred!!
                }
            }
            realm.close()
            return localStarred
        }

    val isLocallyUnread: Boolean
        get() {
            val realm = Realm.getDefaultInstance()
            var localUnread = unreadFromServer
            for (local in LocalState.forEntry(realm, this)) {
                if (local.markUnread != null) {
                    localUnread = local.markUnread!!
                }
            }
            realm.close()
            return localUnread
        }

    override fun toString(): String {
        return String.format(Locale.US, "<Entry %d %s>", id, title)
    }

    companion object {

        fun byId(id: Int): Entry? {
            val realm = Realm.getDefaultInstance()
            try {
                return realm.where(Entry::class.java).equalTo("id", id).findFirst()
            } finally {
                realm.close()
            }
        }

        fun entries(realm: Realm, viewType: ViewType): RealmResults<Entry> {
            when (viewType) {
                Entry.ViewType.UNREAD ->
                    return realm.where(Entry::class.java).equalTo("unreadFromServer", true).findAllSorted("published", Sort.ASCENDING)
                Entry.ViewType.STARRED ->
                    return realm.where(Entry::class.java).equalTo("starredFromServer", true).findAllSorted("published", Sort.DESCENDING)
                Entry.ViewType.ALL ->
                    return realm.where(Entry::class.java).findAllSorted("createdAt", Sort.DESCENDING)
            }
        }


        @UiThread
        fun setStarred(context: Context, realm: Realm, entry: Entry, starred: Boolean) {
            val id = entry.id
            realm.executeTransactionAsync { r ->
                r.copyToRealm(LocalState(id, null, starred))
                // update "server" value - this kicks the object state for observes
                r.where(Entry::class.java).equalTo("id", id).findFirst().starredFromServer = starred;
            }
            Handler().postDelayed({ SyncTask.sync(context, false, true) }, 5000)
        }

        @UiThread
        fun setUnread(context: Context, realm: Realm, entry: Entry, unread: Boolean) {
            val id = entry.id
            realm.executeTransactionAsync { r ->
                r.copyToRealm(LocalState(id, unread, null))
                // update "server" value - this kicks the object state for observes
                r.where(Entry::class.java).equalTo("id", id).findFirst().unreadFromServer = unread;
            }
            Handler().postDelayed({ SyncTask.sync(context, false, true) }, 5000)
        }
    }
}
