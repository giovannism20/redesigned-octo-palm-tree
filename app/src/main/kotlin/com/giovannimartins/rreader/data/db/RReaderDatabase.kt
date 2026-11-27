package com.giovannimartins.rreader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** The app's only database. Bump [version] with a migration and a migration test for every schema change. */
@Database(
    entities = [BookEntity::class, ChapterEntity::class, ReadingPositionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun readingPositionDao(): ReadingPositionDao

    companion object {
        const val NAME = "rreader.db"
    }
}
