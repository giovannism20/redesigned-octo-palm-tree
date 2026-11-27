package com.giovannimartins.rreader.playback

import androidx.media3.common.MediaMetadata
import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookMediaItemsTest {
    private val labeler = ChapterLabeler { index -> "Chapter ${index + 1}" }

    @Test
    fun `parses book ids with an optional chapter and rejects anything else`() {
        assertThat(BookMediaItems.parse(BOOK_ID)).isEqualTo(BookRequest(BOOK_ID, null))
        assertThat(BookMediaItems.parse("$BOOK_ID/3")).isEqualTo(BookRequest(BOOK_ID, 3))
        assertThat(BookMediaItems.parse(BookMediaItems.chapterId(BOOK_ID, 12))).isEqualTo(BookRequest(BOOK_ID, 12))
        assertThat(BookMediaItems.parse("../../databases/rreader.db")).isNull()
        assertThat(BookMediaItems.parse("$BOOK_ID/-1")).isNull()
        assertThat(BookMediaItems.parse("$BOOK_ID/1/2")).isNull()
        assertThat(BookMediaItems.parse("")).isNull()
    }

    @Test
    fun `the playlist has one item per chapter with estimated durations and book metadata`() {
        val titled = book(listOf(chapterText(0, 2), chapterText(1, 3)))
        val book = titled.copy(chapters = listOf(titled.chapters[0].copy(title = null), titled.chapters[1]))
        val clock = ChapterClock(SpeakingRate(charsPerSecond = 10.0))

        val playlist = BookMediaItems.playlist(book, clock, labeler)

        assertThat(playlist.map { it.mediaItem.mediaId }).containsExactly("$BOOK_ID/0", "$BOOK_ID/1").inOrder()
        assertThat(playlist[1].durationUs).isEqualTo(clock.millisFor(book.chapters[1].length) * 1_000)
        val metadata = playlist[0].mediaItem.mediaMetadata
        assertThat(metadata.title.toString()).isEqualTo("Dom Casmurro")
        assertThat(metadata.artist.toString()).isEqualTo("Machado de Assis")
        assertThat(metadata.subtitle.toString()).isEqualTo("Chapter 1")
        assertThat(metadata.mediaType).isEqualTo(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
        assertThat(
            playlist[1]
                .mediaItem.mediaMetadata.subtitle
                .toString(),
        ).isEqualTo("Capítulo 2")
    }
}
