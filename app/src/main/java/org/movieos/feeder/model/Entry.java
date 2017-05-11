
package org.movieos.feeder.model;

import android.support.annotation.UiThread;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
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
    boolean mStarred;

    Subscription mSubscription;

    public enum ViewType {
        UNREAD,
        STARRED,
        ALL,
    }

    public static RealmResults<Entry> entries(Realm realm, ViewType viewType) {
        switch (viewType) {
            case UNREAD:
                return realm.where(Entry.class).equalTo("mUnread", true).findAllSorted("mPublished", Sort.ASCENDING);
            case STARRED:
                return realm.where(Entry.class).equalTo("mStarred", true).findAllSorted("mPublished", Sort.DESCENDING);
            case ALL:
                return realm.where(Entry.class).findAllSorted("mCreatedAt", Sort.DESCENDING);
        }
        throw new AssertionError("bad view type");
    }

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

    public void setUnreadFromServer(boolean unread) {
        mUnread = unread;
    }

    public void setStarredFromServer(boolean starred) {
        mStarred = starred;
    }

    public Subscription getSubscription() {
        return mSubscription;
    }

    public void setSubscription(Subscription subscription) {
        mSubscription = subscription;
    }

    public boolean isLocallyStarred() {
        Realm realm = Realm.getDefaultInstance();
        boolean starred = mStarred;
        for (LocalState local : LocalState.forEntry(realm, this)) {
            if (local.getMarkStarred() != null) {
                starred = local.getMarkStarred();
            }
        }
        realm.close();
        return starred;
    }

    public boolean isLocallyUnread() {
        Realm realm = Realm.getDefaultInstance();
        boolean unread = mUnread;
        for (LocalState local : LocalState.forEntry(realm, this)) {
            if (local.getMarkUnread() != null) {
                unread = local.getMarkUnread();
            }
        }
        realm.close();
        return unread;
    }


    @UiThread
    public static void setStarred(Realm realm, int entryId, boolean starred) {
        realm.executeTransactionAsync(r -> {
            r.copyToRealm(new LocalState(entryId, null, starred));
        });
    }

    @UiThread
    public static void setUnread(Realm realm, int entryId, boolean unread) {
        realm.executeTransactionAsync(r -> {
            r.copyToRealm(new LocalState(entryId, unread, null));
        });
    }


}
