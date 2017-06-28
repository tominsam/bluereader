package org.movieos.bluereader.model

import android.arch.persistence.room.Embedded
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity
class Tagging {

    @PrimaryKey
    @SerializedName("id")
    var id: Int = 0

    @SerializedName("feed_id")
    var feedId: Int = 0

    @SerializedName("name")
    var name: String? = null

    @Embedded(prefix = "subscription_")
    var subscription: Subscription? = null
}
