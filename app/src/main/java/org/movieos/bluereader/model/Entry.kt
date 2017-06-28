package org.movieos.bluereader.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.*

@Entity(
        indices = arrayOf(
                Index("feedId", "published"),
                Index("feedId", "published", "unread"),
                Index("feedId", "published", "starred"),
                Index("published"),
                Index("feedId")
            )
)
class Entry {
    @PrimaryKey
    @SerializedName("id")
    var id: Int = 0

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

    @SerializedName("created_at")
    var createdAt: Date? = null

    @SerializedName("published")
    var published: Date? = null

    var unread: Boolean = false

    var starred: Boolean = false

    enum class ViewType {
        FEEDS,
        UNREAD,
        STARRED,
        ALL
    }

    fun displayAuthor(subscription: Subscription?): String {
        if (subscription == null) {
            return ""
        } else if (author == null) {
            return subscription.title ?: ""
        } else {
            return String.format("%s - %s", subscription.title, author)
        }
    }

    override fun toString(): String {
        return String.format(Locale.US, "<Entry %d %s>", id, title)
    }

    override fun equals(other: Any?): Boolean {
        return other is Entry && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}
