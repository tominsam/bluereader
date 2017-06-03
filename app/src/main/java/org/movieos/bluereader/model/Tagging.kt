package org.movieos.bluereader.model

import com.google.gson.annotations.SerializedName

import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey

open class Tagging : RealmObject() {

    @PrimaryKey
    @SerializedName("id")
    var id: Int = 0

    @Index
    @SerializedName("feed_id")
    var feedId: Int = 0

    @SerializedName("name")
    var name: String? = null

    var subscription: Subscription? = null
}
