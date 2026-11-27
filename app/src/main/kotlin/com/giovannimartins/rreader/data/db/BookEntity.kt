package com.giovannimartins.rreader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A book in the library. Text lives in files (see BookFileStore); this row holds metadata and state. */
@Entity(
    tableName = "books",
    indices = [Index(value = ["source_sha256"], unique = true)],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: List<String>,
    val language: String?,
    val description: String?,
    val format: String,
    @ColumnInfo(name = "source_sha256") val sourceSha256: String,
    @ColumnInfo(name = "source_size") val sourceSize: Long,
    val status: BookStatus,
    @ColumnInfo(name = "failure_reason") val failureReason: ImportFailure?,
    @ColumnInfo(name = "total_chars") val totalChars: Long,
    @ColumnInfo(name = "chapter_count") val chapterCount: Int,
    @ColumnInfo(name = "has_cover") val hasCover: Boolean,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
    val archived: Boolean,
    /** Per-book voice override; null means the default voice from settings. */
    @ColumnInfo(name = "voice_id") val voiceId: String?,
)

/** Lifecycle of an imported book. */
enum class BookStatus {
    IMPORTING,
    READY,
    FAILED,
}

/** Why an import failed; each value maps to a user-facing message. */
enum class ImportFailure {
    UNSUPPORTED_FORMAT,
    FORMAT_NOT_YET_SUPPORTED,
    DRM_PROTECTED,
    CORRUPTED,
    TOO_LARGE,
    NO_READABLE_TEXT,
    STORAGE_ERROR,

    /** A PDF whose pages are pictures of text, which needs OCR (roadmap Phase 4). */
    SCANNED_PDF,

    /** A PDF that needs a password to open. */
    PASSWORD_PROTECTED,

    /** A PDF over the page limit of the extractor (SR-4). */
    TOO_MANY_PAGES,

    /** A PDF that exceeded the extractor's time, text or memory limits (SR-4). */
    TOO_COMPLEX,
}
