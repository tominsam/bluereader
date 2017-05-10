package org.movieos.feeder.sync;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

public class Subscription extends RealmObject implements IntegerPrimaryKey {
    @SerializedName("id")
    @PrimaryKey
    int mId;

    @Required
    @SerializedName("created_at")
    Date mCreatedAt;

    @Index
    @SerializedName("feed_id")
    int mFeedId;

    @Required
    @SerializedName("title")
    String mTitle;

    @Required
    @SerializedName("feed_url")
    String mFeedUrl;

    @Required
    @SerializedName("site_url")
    String mSiteUtl;

    public Subscription() {
    }

    public int getId() {
        return mId;
    }

    public Date getCreatedAt() {
        return mCreatedAt;
    }

    public int getFeedId() {
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
