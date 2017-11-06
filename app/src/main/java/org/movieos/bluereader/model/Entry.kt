package org.movieos.bluereader.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import android.content.Context
import com.google.gson.annotations.SerializedName
import org.movieos.bluereader.api.Mercury
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
data class Entry(
        @PrimaryKey
        @SerializedName("id")
        val id: Int,

        @SerializedName("feed_id")
        val feedId: Int,

        @SerializedName("title")
        val title: String?,

        @SerializedName("url")
        val url: String?,

        @SerializedName("author")
        val author: String?,

        @SerializedName("content")
        val content: String?,

        @SerializedName("summary")
        val summary: String?,

        @SerializedName("created_at")
        val createdAt: Date?,

        @SerializedName("published")
        val published: Date?,

        // these properties are denormalized locally and stored to make the UI state easier to handle
        var unread: Boolean,
        var starred: Boolean
) {

    val excerpt: String
        get() = summary?.substring(0, Math.min(summary.length, 200)) ?: ""

    fun displayAuthor(subscription: Subscription?): String {
        return when {
            subscription == null ->
                ""
            author == null ->
                subscription.title ?: ""
            else ->
                String.format("%s - %s", subscription.title, author)
        }
    }

    fun hasMercuryContent(context: Context): Boolean {
        return Mercury(context).contentFor(this) != null
    }

}
