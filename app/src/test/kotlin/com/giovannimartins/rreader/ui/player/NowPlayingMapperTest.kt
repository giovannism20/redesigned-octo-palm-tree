package com.giovannimartins.rreader.ui.player

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.giovannimartins.rreader.playback.BookMediaItems
import com.giovannimartins.rreader.playback.PlaybackErrors
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class NowPlayingMapperTest {
    private val bookId = "00000000-0000-0000-0000-000000000001"

    @Test
    fun `maps the facade's chapter playlist into what the players show`() {
        val player = FixedPlayer(state(playWhenReady = true, playbackState = Player.STATE_BUFFERING))

        val nowPlaying = checkNotNull(player.toNowPlaying(player.chapterTitles()))

        assertThat(nowPlaying.bookId).isEqualTo(bookId)
        assertThat(nowPlaying.title).isEqualTo("Iracema")
        assertThat(nowPlaying.authors).isEqualTo("José de Alencar")
        assertThat(nowPlaying.coverFile).isEqualTo(File("/data/cover"))
        assertThat(nowPlaying.chapterIndex).isEqualTo(1)
        assertThat(nowPlaying.chapterTitle).isEqualTo("II")
        assertThat(nowPlaying.chapterTitles).containsExactly("I", "II").inOrder()
        assertThat(nowPlaying.isPlaying).isTrue()
        assertThat(nowPlaying.isBuffering).isTrue()
        assertThat(nowPlaying.positionMs).isEqualTo(30_000)
        assertThat(nowPlaying.durationMs).isEqualTo(120_000)
        assertThat(nowPlaying.progress).isEqualTo(0.25f)
        assertThat(nowPlaying.speed).isEqualTo(1.5f)
        assertThat(nowPlaying.problem).isNull()
    }

    @Test
    fun `an ended or failed player offers to play, with the reason`() {
        val ended = FixedPlayer(state(playWhenReady = true, playbackState = Player.STATE_ENDED))
        val failed =
            FixedPlayer(
                state(playWhenReady = true, playbackState = Player.STATE_IDLE)
                    .buildUpon()
                    .setPlayerError(PlaybackErrors.noVoice())
                    .build(),
            )

        assertThat(ended.toNowPlaying(emptyList())!!.isPlaying).isFalse()
        assertThat(ended.toNowPlaying(emptyList())!!.isEnded).isTrue()
        assertThat(failed.toNowPlaying(emptyList())!!.isPlaying).isFalse()
        assertThat(failed.toNowPlaying(emptyList())!!.problem).isEqualTo(PlaybackProblem.NO_VOICE)
        assertThat(problemFor(PlaybackErrors.SPEECH_FAILED)).isEqualTo(PlaybackProblem.SPEECH_FAILED)
        assertThat(problemFor(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)).isEqualTo(PlaybackProblem.OTHER)
    }

    @Test
    fun `nothing is open without a book in the player`() {
        assertThat(FixedPlayer(SimpleBasePlayer.State.Builder().build()).toNowPlaying(emptyList())).isNull()
    }

    private fun problemFor(errorCode: Int): PlaybackProblem? =
        FixedPlayer(
            state(playWhenReady = false, playbackState = Player.STATE_IDLE)
                .buildUpon()
                .setPlayerError(PlaybackException("test", null, errorCode))
                .build(),
        ).toNowPlaying(emptyList())?.problem

    private fun state(
        playWhenReady: Boolean,
        playbackState: Int,
    ): SimpleBasePlayer.State {
        val playlist =
            listOf("I", "II").mapIndexed { index, chapter ->
                val id = BookMediaItems.chapterId(bookId, index)
                val metadata =
                    MediaMetadata
                        .Builder()
                        .setTitle("Iracema")
                        .setArtist("José de Alencar")
                        .setSubtitle(chapter)
                        .setArtworkUri(Uri.fromFile(File("/data/cover")))
                        .build()
                SimpleBasePlayer.MediaItemData
                    .Builder(id)
                    .setMediaItem(
                        MediaItem
                            .Builder()
                            .setMediaId(id)
                            .setMediaMetadata(metadata)
                            .build(),
                    ).setDurationUs(120_000_000)
                    .build()
            }
        return SimpleBasePlayer.State
            .Builder()
            .setAvailableCommands(
                Player.Commands
                    .Builder()
                    .addAllCommands()
                    .build(),
            ).setPlaylist(playlist)
            .setCurrentMediaItemIndex(1)
            .setContentPositionMs(30_000)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setPlaybackParameters(PlaybackParameters(1.5f))
            .build()
    }

    /** A player that only reports [fixed]. */
    private class FixedPlayer(
        private val fixed: State,
    ) : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState(): State = fixed
    }
}
