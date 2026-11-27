package com.giovannimartins.rreader.data.db

import androidx.room.TypeConverter

/** Room converters. Authors are stored joined by the ASCII unit separator, which never appears in names. */
class Converters {
    @TypeConverter
    fun authorsToColumn(authors: List<String>): String = authors.joinToString(AUTHOR_SEPARATOR)

    @TypeConverter
    fun columnToAuthors(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(AUTHOR_SEPARATOR)

    private companion object {
        const val AUTHOR_SEPARATOR = "\u001F"
    }
}
