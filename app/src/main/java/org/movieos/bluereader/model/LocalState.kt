package org.movieos.bluereader.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import java.util.*

@Entity
data class LocalState(
        @PrimaryKey(autoGenerate = true)
        var id: Int?,
        var entryId: Int,
        var timeStamp: Date,
        var markUnread: Boolean?,
        var markStarred: Boolean?
)