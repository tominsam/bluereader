package org.movieos.bluereader.model

import com.google.gson.annotations.SerializedName
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.annotations.Required
import java.util.*

open class Subscription : RealmObject() {

    @SerializedName("id")
    @PrimaryKey
    var id: Int = 0

    @Required
    @SerializedName("created_at")
    var createdAt: Date? = null

    @Index
    @SerializedName("feed_id")
    var feedId: Int = 0

    @Required
    @SerializedName("title")
    var title: String? = null

    @Required
    @SerializedName("feed_url")
    var feedUrl: String? = null

    @Required
    @SerializedName("site_url")
    var siteUtl: String? = null

}
