
package org.movieos.feeder.model;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import com.google.gson.annotations.SerializedName;

import org.movieos.feeder.utilities.SyncTask;

import java.util.Date;
import java.util.Locale;

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

    @Nullable
    public static Entry byId(int id) {
        Realm realm = Realm.getDefaultInstance();
        try {
            return realm.where(Entry.class).equalTo("mId", id).findFirst();
        } finally {
            realm.close();
        }
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

    public String getDisplayAuthor() {
        if (mAuthor == null) {
            return mSubscription.getTitle();
        } else {
            return String.format("%s - %s", mSubscription.getTitle(), mAuthor);
        }
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
    public static void setStarred(Context context, Realm realm, Entry entry, boolean starred) {
        int id = entry.getId();
        realm.executeTransactionAsync(r -> {
            r.copyToRealm(new LocalState(id, null, starred));
            // update "server" value - this kicks the object state for observes
            r.where(Entry.class).equalTo("mId", id).findFirst().setStarredFromServer(starred);
        });
        new Handler().postDelayed(() -> SyncTask.sync(context, false, true), 5000);
    }

    @UiThread
    public static void setUnread(@NonNull Context context, @NonNull Realm realm, @NonNull Entry entry, boolean unread) {
        int id = entry.getId();
        realm.executeTransactionAsync(r -> {
            r.copyToRealm(new LocalState(id, unread, null));
            // update "server" value - this kicks the object state for observes
            r.where(Entry.class).equalTo("mId", id).findFirst().setUnreadFromServer(unread);
        });
        new Handler().postDelayed(() -> SyncTask.sync(context, false, true), 5000);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "<Entry %d %s>", getId(), getTitle());
    }
}
