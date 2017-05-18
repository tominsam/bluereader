package org.movieos.feeder.model

import android.content.Context
import android.os.Handler
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

        fun setStarred(context: Context, realm: Realm, entry: Entry, starred: Boolean) {
            val id = entry.id
            realm.executeTransactionAsync { r ->
                r.copyToRealm(LocalState(id, null, starred))
                // Arbitrary object change to kick UI
                byId(r, id)?.locallyUpdated = Date()
            }
            Handler().postDelayed({ SyncTask.sync(context, false, true) }, 5000)
        }

        fun setUnread(context: Context, realm: Realm, entry: Entry, unread: Boolean) {
            val id = entry.id
            realm.executeTransactionAsync { r ->
                r.copyToRealm(LocalState(id, unread, null))
                // Arbitrary object change to kick UI
                byId(r, id)?.locallyUpdated = Date()
            }
            Handler().postDelayed({ SyncTask.sync(context, false, true) }, 5000)
        }
    }
}
