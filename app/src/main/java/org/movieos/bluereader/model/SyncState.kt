package org.movieos.bluereader.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import java.util.*

@Entity
data class SyncState(
        @PrimaryKey
        var id: Int,
        var timeStamp: Date
) {

    val isStale: Boolean
        get() = Date().time - timeStamp.time > 1000 * 60 * 5

}
