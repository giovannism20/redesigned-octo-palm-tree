package com.giovannimartins.rreader.ui.library

import com.giovannimartins.rreader.data.db.BookStatus
import com.giovannimartins.rreader.data.db.ImportFailure
import com.giovannimartins.rreader.data.library.LibraryBook
import com.giovannimartins.rreader.data.settings.AppSettings
import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.giovannimartins.rreader.tts.SpeakingRates
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LibraryTimeLeftTest {
    private val rates =
        SpeakingRates(
            mapOf("own" to SpeakingRate(10.0), "default" to SpeakingRate(20.0), "last" to SpeakingRate(25.0)),
            lastVoiceId = "last",
        )

    /** 36,000 characters are left to hear. */
    private val book =
        LibraryBook(
            id = "1",
            title = "Iracema",
            authors = emptyList(),
            status = BookStatus.READY,
            failure = null,
            coverFile = null,
            charsRead = 1_000,
            totalChars = 37_000,
        )

    @Test
    fun `uses the book's own voice, else the default voice, else the voice that read last`() {
        val withDefault = AppSettings(defaultVoiceId = "default")

        assertThat(LibraryTimeLeft.of(book.copy(voiceId = "own"), withDefault, rates)).isEqualTo(1.hours)
        assertThat(LibraryTimeLeft.of(book, withDefault, rates)).isEqualTo(30.minutes)
        assertThat(LibraryTimeLeft.of(book, AppSettings(), rates)).isEqualTo(24.minutes)
        assertThat(LibraryTimeLeft.of(book, AppSettings(), SpeakingRates())).isEqualTo((36_000 / 14.5).seconds)
    }

    @Test
    fun `listening faster leaves less time`() {
        val fast = AppSettings(defaultVoiceId = "default", playbackSpeed = 2f)

        assertThat(LibraryTimeLeft.of(book, fast, rates)).isEqualTo(15.minutes)
    }

    @Test
    fun `finished books and books that are not ready have no time left`() {
        val settings = AppSettings()

        assertThat(LibraryTimeLeft.of(book.copy(charsRead = 37_000), settings, rates)).isNull()
        assertThat(LibraryTimeLeft.of(book.copy(status = BookStatus.IMPORTING), settings, rates)).isNull()
        val failed = book.copy(status = BookStatus.FAILED, failure = ImportFailure.CORRUPTED, totalChars = 0)
        assertThat(LibraryTimeLeft.of(failed, settings, rates)).isNull()
    }
}
