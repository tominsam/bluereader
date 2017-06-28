package org.movieos.bluereader.dao

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverter
import android.arch.persistence.room.TypeConverters
import org.movieos.bluereader.model.*
import java.util.*


@Database(
        entities = arrayOf(
                Entry::class,
                Subscription::class,
                Tagging::class,
                SyncState::class,
                LocalState::class
        ),
        version = 1,
        exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MainDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return if (value == null) null else Date(value)
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.getTime()
    }
}
