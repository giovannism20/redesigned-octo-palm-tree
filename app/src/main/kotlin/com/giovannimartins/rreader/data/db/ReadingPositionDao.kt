package com.giovannimartins.rreader.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReadingPositionDao {
    @Query("SELECT * FROM reading_positions WHERE book_id = :bookId")
    suspend fun find(bookId: String): ReadingPositionEntity?

    @Upsert
    suspend fun save(position: ReadingPositionEntity)
}
