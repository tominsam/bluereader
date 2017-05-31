package org.movieos.feeder.model

import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.*

open class SyncState : RealmObject {
    @PrimaryKey
    internal var id: Int = 0

    var timeStamp: Date = Date()
        internal set

    constructor()

    constructor(id: Int, timeStamp: Date) {
        this.id = id
        this.timeStamp = timeStamp
    }

    val isStale: Boolean
        get() = Date().time - timeStamp.time > 1000 * 60 * 5

    companion object {

        fun latest(realm: Realm): SyncState? {
            return realm.where(SyncState::class.java).equalTo("id", 1).findFirst()
        }
    }

}
