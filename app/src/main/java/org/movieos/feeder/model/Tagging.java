
package org.movieos.feeder.model;

import com.google.gson.annotations.SerializedName;

import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

public class Tagging extends RealmObject implements IntegerPrimaryKey {
    @PrimaryKey
    @SerializedName("id")
    int mId;

    @Index
    @SerializedName("feed_id")
    int mFeedId;

    @SerializedName("name")
    String mName;

    public Tagging() {
    }

    public int getId() {
        return mId;
    }

    public int getFeedId() {
        return mFeedId;
    }

    public String getName() {
        return mName;
    }
}
