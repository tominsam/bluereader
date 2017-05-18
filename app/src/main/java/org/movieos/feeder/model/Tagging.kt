package org.movieos.feeder.model

import com.google.gson.annotations.SerializedName

import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey

open class Tagging : RealmObject(), IntegerPrimaryKey {

    @PrimaryKey
    @SerializedName("id")
    override var id: Int = 0

    @Index
    @SerializedName("feed_id")
    var feedId: Int = 0
        internal set

    @SerializedName("name")
    var name: String? = null
        internal set
}
