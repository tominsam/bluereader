package org.movieos.bluereader.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey
import java.util.*

@Entity
open class SyncState {
    @PrimaryKey
    var id: Int = 0

    var timeStamp: Date = Date()

    constructor()

    @Ignore
    constructor(id: Int, timeStamp: Date) {
        this.id = id
        this.timeStamp = timeStamp
    }

    val isStale: Boolean
        get() = Date().time - timeStamp.time > 1000 * 60 * 5


}
