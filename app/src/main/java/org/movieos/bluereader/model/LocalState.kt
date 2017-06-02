package org.movieos.bluereader.model

import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.Index
import java.util.*

open class LocalState : RealmObject {
    @Index
    var entryId: Int = 0
        internal set

    var timeStamp: Date = Date()
        internal set

    var markUnread: Boolean? = null
        internal set

    var markStarred: Boolean? = null
        internal set

    constructor()

    constructor(entryId: Int, markUnread: Boolean?, markStarred: Boolean?) {
        this.entryId = entryId
        this.markUnread = markUnread
        this.markStarred = markStarred
        timeStamp = Date()
    }

    companion object {

        fun forEntry(realm: Realm, entry: Entry): RealmResults<LocalState> {
            return realm.where(LocalState::class.java).equalTo("entryId", entry.id).findAllSorted("timeStamp")
        }

        fun all(realm: Realm): RealmResults<LocalState> {
            return realm.where(LocalState::class.java).findAllSorted("timeStamp")
        }
    }
}
