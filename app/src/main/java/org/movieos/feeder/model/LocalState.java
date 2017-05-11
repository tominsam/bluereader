
package org.movieos.feeder.model;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.Index;

public class LocalState extends RealmObject {
    Date mTimeStamp;

    @Index
    int mEntryId;

    Boolean mMarkUnread;
    Boolean mMarkStarred;

    public static RealmResults<LocalState> forEntry(Realm realm, Entry entry) {
        return realm.where(LocalState.class).equalTo("mEntryId", entry.mId).findAllSorted("mTimeStamp");
    }

    public static RealmResults<LocalState> all(Realm realm) {
        return realm.where(LocalState.class).findAllSorted("mTimeStamp");
    }

    public LocalState() {
    }

    public LocalState(int entryId, Boolean markUnread, Boolean markStarred) {
        mEntryId = entryId;
        mMarkUnread = markUnread;
        mMarkStarred = markStarred;
        mTimeStamp = new Date();
    }

    public int getEntryId() {
        return mEntryId;
    }

    public Boolean getMarkUnread() {
        return mMarkUnread;
    }

    public Boolean getMarkStarred() {
        return mMarkStarred;
    }

    public Date getTimeStamp() {
        return mTimeStamp;
    }
}
