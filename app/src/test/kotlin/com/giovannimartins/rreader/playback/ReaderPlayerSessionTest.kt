package com.giovannimartins.rreader.playback

import androidx.media3.common.Player
import androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/**
 * The listening session the player publishes (book, voice, listening or not), and pre-rendering asked for only while
 * the book is not being listened to, so the job never competes with the listener for the voice.
 */
@RunWith(RobolectricTestRunner::class)
class ReaderPlayerSessionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val texts = listOf(chapterText(0, 12), chapterText(1, 12))
    private val dora = voice(id = "neural:dora").copy(slowToRender = true)
    private val content = FakePlaybackContent(book(texts, voice = dora), texts)
    private lateinit var fixture: ReaderPlayerFixture
    private val player get() = fixture.player
    private val session get() = fixture.status.session.value

    @Before
    fun setUp() {
        fixture = ReaderPlayerFixture(folder.root, content)
    }

    @After
    fun tearDown() = player.release()

    @Test
    fun `publishes the open book, its voice and whether it is listened to`() {
        assertThat(session).isNull()
        fixture.open()
        advance(player).untilState(Player.STATE_READY)
        assertThat(session).isEqualTo(ListeningSession(BOOK_ID, dora, listening = false))

        player.play()
        runMainLooperUntil { session?.listening == true }
        player.pause()
        ShadowLooper.idleMainLooper()
        assertThat(session?.listening).isFalse()

        player.release()
        assertThat(session).isNull()
        fixture = ReaderPlayerFixture(folder.root, content)
    }

    @Test
    fun `a slow voice renders ahead only while the book is not listened to`() {
        fixture.open()
        advance(player).untilState(Player.STATE_READY)
        assertThat(fixture.prerendered).containsExactly(BOOK_ID)

        player.play()
        runMainLooperUntil { player.isPlaying }
        assertThat(fixture.prerendered).containsExactly(BOOK_ID)

        player.pause()
        ShadowLooper.idleMainLooper()
        assertThat(fixture.prerendered).containsExactly(BOOK_ID, BOOK_ID)
    }

    @Test
    fun `a book opened to be listened to at once is not rendered ahead until listening stops`() {
        fixture.open()
        player.play()
        runMainLooperUntil { player.isPlaying }

        assertThat(fixture.prerendered).isEmpty()
        player.stop()
        ShadowLooper.idleMainLooper()
        assertThat(fixture.prerendered).containsExactly(BOOK_ID)
    }
}
