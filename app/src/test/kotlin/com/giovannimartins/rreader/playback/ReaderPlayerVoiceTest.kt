package com.giovannimartins.rreader.playback

import androidx.media3.common.Player
import androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.textkit.text.SegmentPlanner
import com.giovannimartins.rreader.tts.Voice
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/**
 * Changing the voice of the book being listened to (`ReaderCommand.RefreshVoice`, sent after the listener chose a
 * voice): it takes effect at the segment being spoken, without reopening the book or losing the place.
 */
@RunWith(RobolectricTestRunner::class)
class ReaderPlayerVoiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val texts = listOf(chapterText(0, 12), chapterText(1, 12))
    private val segments = SegmentPlanner().plan(texts[0])
    private val dora = voice(id = "neural:dora")
    private val phone = voice(id = "system:engine:pt-br")
    private val content = FakePlaybackContent(book(texts, voice = dora), texts)
    private lateinit var fixture: ReaderPlayerFixture
    private val player get() = fixture.player
    private val engine get() = fixture.engine

    @Before
    fun setUp() {
        fixture = ReaderPlayerFixture(folder.root, content)
    }

    @After
    fun tearDown() = player.release()

    @Test
    fun `a new voice takes over at the segment being spoken, keeping the place`() {
        openAt(ReadingPosition(0, segments[2].start + 3))
        content.voice = phone

        assertThat(player.handle(ReaderCommand.RefreshVoice)).isTrue()
        runMainLooperUntil { phone.id in engine.voicesUsed }

        val first = engine.voicesUsed.indexOf(phone.id)
        assertThat(engine.spoken[first]).isEqualTo(texts[0].substring(segments[2].start, segments[2].end))
        assertThat(
            fixture.status.spokenSegment.value
                ?.range,
        ).isEqualTo(segments[2])
        assertThat(content.loads).isEqualTo(1)
        player.play()
        advance(player).untilState(Player.STATE_READY)
        runMainLooperUntil { player.isPlaying }
    }

    @Test
    fun `while listening, the rest of the book is read with the new voice`() {
        fixture.open()
        player.play()
        advance(player).untilState(Player.STATE_READY)
        runMainLooperUntil {
            fixture.status.spokenSegment.value
                ?.range == segments[1]
        }
        content.voice = phone

        player.handle(ReaderCommand.RefreshVoice)
        runMainLooperUntil { engine.voicesUsed.count { it == phone.id } >= 3 }

        val switch = engine.voicesUsed.indexOf(phone.id)
        assertThat(engine.voicesUsed.drop(switch).toSet()).containsExactly(phone.id)
        assertThat(player.playWhenReady).isTrue()
        assertThat(player.playerError).isNull()
        assertThat(content.loads).isEqualTo(1)
    }

    @Test
    fun `when the voice is changed twice quickly, the last choice wins`() {
        openAt(ReadingPosition(0, segments[2].start))
        val alex = voice(id = "neural:alex")
        val slowLookup = CompletableDeferred<Voice?>()
        content.voiceAnswers += { slowLookup.await() }
        content.voiceAnswers += { phone }

        player.handle(ReaderCommand.RefreshVoice)
        ShadowLooper.idleMainLooper()
        player.handle(ReaderCommand.RefreshVoice)
        runMainLooperUntil { phone.id in engine.voicesUsed }
        slowLookup.complete(alex)
        ShadowLooper.idleMainLooper()

        assertThat(engine.voicesUsed).doesNotContain(alex.id)
        assertThat(
            fixture.status.session.value
                ?.voice,
        ).isEqualTo(phone)
    }

    @Test
    fun `the same voice changes nothing`() {
        openAt(ReadingPosition(0, segments[2].start))
        val rendered = engine.spoken.size

        player.handle(ReaderCommand.RefreshVoice)
        ShadowLooper.idleMainLooper()

        assertThat(engine.voicesUsed.toSet()).containsExactly(dora.id)
        assertThat(engine.spoken.size).isEqualTo(rendered)
    }

    @Test
    fun `when the voice in use is gone, another voice continues where the reading stopped`() {
        engine.unavailableVoices = setOf(dora.id)
        fixture.open()
        player.play()
        assertThat(advance(player).untilPlayerError().errorCode).isEqualTo(PlaybackErrors.NO_VOICE)
        content.voice = phone

        player.handle(ReaderCommand.RefreshVoice)
        advance(player).untilState(Player.STATE_READY)

        assertThat(player.playerError).isNull()
        assertThat(engine.voicesUsed.last()).isEqualTo(phone.id)
        runMainLooperUntil { player.isPlaying }
    }

    @Test
    fun `without any voice left the player asks for one`() {
        openAt(ReadingPosition.START)
        content.book = content.book?.copy(voice = null)

        player.handle(ReaderCommand.RefreshVoice)

        assertThat(advance(player).untilPlayerError().errorCode).isEqualTo(PlaybackErrors.NO_VOICE)
    }

    @Test
    fun `a voice slower than real time renders the book ahead while charging`() {
        openAt(ReadingPosition.START)
        assertThat(fixture.prerendered).isEmpty()
        content.voice = phone.copy(slowToRender = true)

        player.handle(ReaderCommand.RefreshVoice)
        ShadowLooper.idleMainLooper()

        assertThat(fixture.prerendered).containsExactly(BOOK_ID)
    }

    @Test
    fun `with no book open there is no voice to change`() {
        assertThat(player.handle(ReaderCommand.RefreshVoice)).isFalse()
    }

    /** Opens the book at [position] and waits, paused, until its audio is ready. */
    private fun openAt(position: ReadingPosition) {
        content.book = content.book?.copy(position = position)
        fixture.open()
        advance(player).untilState(Player.STATE_READY)
    }
}
