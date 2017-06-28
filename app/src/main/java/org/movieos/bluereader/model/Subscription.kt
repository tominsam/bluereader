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
open class Subscription {

    @SerializedName("id")
    @PrimaryKey
    var id: Int = 0

    @SerializedName("created_at")
    var createdAt: Date? = null

    @SerializedName("feed_id")
    var feedId: Int = 0

    @SerializedName("title")
    var title: String? = null

    @SerializedName("feed_url")
    var feedUrl: String? = null

    @SerializedName("site_url")
    var siteUrl: String? = null

    // This is locally generated, not fetched from the server
    var unreadCount: Int = 0
}
