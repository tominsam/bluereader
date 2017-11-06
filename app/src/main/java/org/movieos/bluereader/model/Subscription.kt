package org.movieos.bluereader.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.*

@Entity(
        indices = arrayOf(
                Index("feedId")
        )
)
data class Subscription(
        @SerializedName("id")
        @PrimaryKey
        var id: Int,

        @SerializedName("created_at")
        var createdAt: Date?,

        @SerializedName("feed_id")
        var feedId: Int,

        @SerializedName("title")
        var title: String?,

        @SerializedName("feed_url")
        var feedUrl: String?,

        @SerializedName("site_url")
        var siteUrl: String?,

        // This is locally generated, not fetched from the server
        var unreadCount: Int
)
