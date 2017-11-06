package org.movieos.bluereader.model

import android.arch.persistence.room.Embedded
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity
data class Tagging(
        @PrimaryKey
        @SerializedName("id")
        var id: Int,

        @SerializedName("feed_id")
        var feedId: Int,

        @SerializedName("name")
        var name: String?,

        @Embedded(prefix = "subscription_")
        var subscription: Subscription?
)