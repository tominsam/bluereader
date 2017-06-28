package org.movieos.bluereader.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey
import java.util.*

@Entity
open class LocalState {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null

    var entryId: Int = 0

    var timeStamp: Date = Date()

    var markUnread: Boolean? = null

    var markStarred: Boolean? = null

    constructor()

    @Ignore
    constructor(entryId: Int, markUnread: Boolean?, markStarred: Boolean?) {
        this.entryId = entryId
        this.markUnread = markUnread
        this.markStarred = markStarred
        timeStamp = Date()
    }

    override fun toString(): String {
        return "<LocalState $entryId $markUnread $markStarred>"
    }
}
