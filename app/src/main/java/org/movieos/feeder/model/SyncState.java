package org.movieos.feeder.model;

import android.support.annotation.Nullable;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class SyncState extends RealmObject {
    @PrimaryKey
    int mId;

    Date mTimeStamp;

    @Nullable
    public static SyncState latest(Realm realm) {
        return realm.where(SyncState.class).equalTo("mId", 1).findFirst();
    }

    public SyncState() {
    }

    public SyncState(int id, Date timeStamp) {
        mId = id;
        mTimeStamp = timeStamp;
    }

    public Date getTimeStamp() {
        return mTimeStamp;
    }

}
