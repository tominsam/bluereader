
package org.movieos.feeder.sync;

import android.support.annotation.UiThread;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

public class Entry extends RealmObject implements IntegerPrimaryKey {
    @PrimaryKey
    @SerializedName("id")
    int mId;

    @Index
    @SerializedName("feed_id")
    int mFeedId;

    @SerializedName("title")
    String mTitle;
    @SerializedName("url")
    String mUrl;
    @SerializedName("author")
    String mAuthor;
    @SerializedName("content")
    String mContent;
    @SerializedName("summary")
    String mSummary;

    @Required
    @SerializedName("created_at")
    Date mCreatedAt;
    @Required
    @SerializedName("published")
    Date mPublished;

    boolean mUnread;
    boolean mUnreadDirty;
    boolean mStarred;
    boolean mStarredDirty;

    Subscription mSubscription;

    public Entry() {
    }

    public int getId() {
        return mId;
    }

    public int getFeedId() {
        return mFeedId;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getAuthor() {
        return mAuthor;
    }

    public String getContent() {
        return mContent;
    }

    public String getSummary() {
        return mSummary;
    }

    public Date getCreatedAt() {
        return mCreatedAt;
    }

    public Date getPublished() {
        return mPublished;
    }

    public boolean isUnread() {
        return mUnread;
    }

    public boolean isUnreadDirty() {
        return mUnreadDirty;
    }

    public boolean isStarred() {
        return mStarred;
    }

    public boolean isStarredDirty() {
        return mStarredDirty;
    }

    public void setUnread(boolean unread, boolean dirty) {
        mUnread = unread;
        mUnreadDirty = dirty;
    }

    public void setStarred(boolean starred, boolean dirty) {
        mStarred = starred;
        mStarredDirty = dirty;
    }

    public Subscription getSubscription() {
        return mSubscription;
    }

    public void setSubscription(Subscription subscription) {
        mSubscription = subscription;
    }

    @UiThread
    public static void setStarred(Entry entry, boolean starred) {
        int id = entry.getId();
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(r -> {
            r.where(Entry.class).equalTo("mId", id).findFirst().setStarred(starred, true);
        });
        realm.close();
    }


}
