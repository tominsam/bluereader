package org.movieos.feeder.sync;

import com.google.gson.annotations.SerializedName;
import io.realm.RealmObject;

import java.util.Date;

public class Subscription extends RealmObject {
    @SerializedName("id")

    String mId;
    @SerializedName("created_at")
    Date mCreatedAt;
    @SerializedName("feed_id")
    String mFeedId;
    @SerializedName("title")
    String mTitle;
    @SerializedName("feed_url")
    String mFeedUrl;
    @SerializedName("site_url")
    String mSiteUtl;

    public Subscription() {
    }

    public String getId() {
        return mId;
    }

    public Date getCreatedAt() {
        return mCreatedAt;
    }

    public String getFeedId() {
        return mFeedId;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getFeedUrl() {
        return mFeedUrl;
    }

    public String getSiteUtl() {
        return mSiteUtl;
    }
}
