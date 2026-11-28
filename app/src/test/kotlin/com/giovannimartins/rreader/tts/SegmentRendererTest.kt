package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.giovannimartins.rreader.textkit.model.TextRange
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SegmentRendererTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val voice = Voice("system:engine:pt", "engine", "Engine", "pt", Locale.forLanguageTag("pt-BR"), false, 400)
    private val neural = voice.copy(id = "neural:test", rendersAtSpeed = true)
    private val engine = CountingEngine()
    private val renderSpeeds = testRenderSpeeds()
    private val rates = SpeakingRateTracker(InMemorySpeakingRateStore(), CoroutineScope(UnconfinedTestDispatcher()))
    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000_000), ZoneOffset.UTC)
    private val text = "Era uma vez um livro que queria ser ouvido em voz alta."

    @Test
    fun `renders a segment into the cache and reuses it afterwards`() =
        runTest {
            val renderer = renderer()

            val first = renderer.render(request()) as SegmentAudio.Ready
            val second = renderer.render(request()) as SegmentAudio.Ready

            assertThat(engine.calls).isEqualTo(1)
            assertThat(second.file).isEqualTo(first.file)
            assertThat(second.durationMillis).isEqualTo(text.length * MILLIS_PER_CHAR)
            assertThat(first.file.name).isEqualTo("0-0-${text.length}.wav")
            assertThat(
                first.file.parentFile
                    ?.list()
                    ?.toList(),
            ).containsExactly(first.file.name)
        }

    @Test
    fun `a cached segment is touched so trimming keeps it`() =
        runTest {
            val renderer = renderer()
            val audio = renderer.render(request()) as SegmentAudio.Ready
            audio.file.setLastModified(1_000)

            renderer.render(request())

            assertThat(audio.file.lastModified()).isEqualTo(clock.millis())
        }

    @Test
    fun `rendered segments teach the speaking rate`() =
        runTest {
            renderer().render(request())

            val expected = SpeakingRate.DEFAULT.updatedWith(text.length, (text.length * MILLIS_PER_CHAR).milliseconds)
            assertThat(rates.rateFor(voice.id)).isEqualTo(expected)
        }

    @Test
    fun `a failed render leaves nothing in the cache`() =
        runTest {
            engine.failure = SynthesisFailure.TIMEOUT
            val renderer = renderer()

            val audio = renderer.render(request())

            assertThat(audio).isEqualTo(SegmentAudio.Failed(SynthesisFailure.TIMEOUT))
            assertThat(
                folder.root
                    .walkTopDown()
                    .filter(File::isFile)
                    .toList(),
            ).isEmpty()
        }

    @Test
    fun `a damaged cached file is rendered again`() =
        runTest {
            val renderer = renderer()
            val audio = renderer.render(request()) as SegmentAudio.Ready
            audio.file.writeText("not audio")

            renderer.render(request())

            assertThat(engine.calls).isEqualTo(2)
        }

    @Test
    fun `discarded audio is rendered again`() =
        runTest {
            val renderer = renderer()
            val audio = renderer.render(request()) as SegmentAudio.Ready

            renderer.discard(audio.file)
            renderer.render(request())

            assertThat(audio.file.exists()).isTrue()
            assertThat(engine.calls).isEqualTo(2)
        }

    @Test
    fun `audio a voice rendered before it changed its rendering is rendered again`() =
        runTest {
            val renderer = renderer()
            val before = voice.copy(renderVersion = "data-1")

            val old = renderer.render(request().copy(voice = before)) as SegmentAudio.Ready
            val new =
                renderer.render(
                    request().copy(voice = before.copy(renderVersion = "data-2")),
                ) as SegmentAudio.Ready
            renderer.render(request().copy(voice = before.copy(renderVersion = "data-2")))

            assertThat(engine.calls).isEqualTo(2)
            assertThat(new.file).isNotEqualTo(old.file)
            assertThat(before.audioKey).isEqualTo("system:engine:pt@data-1")
            assertThat(voice.audioKey).isEqualTo(voice.id)
        }

    @Test
    fun `a background render waits until the player has filled its window`() =
        runTest {
            val renderer = renderer()
            val window = CompletableDeferred<Unit>()
            launch { renderer.inForeground { window.await() } }
            runCurrent()

            val background = async { renderer.render(request(), background = true) }
            runCurrent()
            assertThat(engine.calls).isEqualTo(0)
            renderer.render(request().copy(range = TextRange(0, 10)))
            assertThat(engine.calls).isEqualTo(1)
            window.complete(Unit)

            assertThat(background.await()).isInstanceOf(SegmentAudio.Ready::class.java)
            assertThat(engine.calls).isEqualTo(2)
        }

    @Test
    fun `a voice that renders at speed renders at the listening speed`() =
        runTest {
            val audio = renderer().render(request(neural, speed = 1.3f)) as SegmentAudio.Ready

            assertThat(engine.speeds).containsExactly(1.25f)
            assertThat(audio.speed).isEqualTo(1.25f)
            assertThat(audio.file.name).isEqualTo("0-0-${text.length}@x125.wav")
            // Heard at the voice's own pace it lasts as long as at 1.0×; the file itself is a fifth shorter.
            assertThat(audio.durationMillis).isWithin(2).of(text.length * MILLIS_PER_CHAR)
            assertThat(audio.mediaMillis).isWithin(2).of(text.length * MILLIS_PER_CHAR * 4 / 5)
        }

    @Test
    fun `audio cached at another speed is reused instead of rendering again`() =
        runTest {
            val renderer = renderer()
            val atNormal = renderer.render(request(neural, speed = 1f)) as SegmentAudio.Ready

            val atFaster = renderer.render(request(neural, speed = 1.5f)) as SegmentAudio.Ready

            assertThat(engine.calls).isEqualTo(1)
            assertThat(atFaster.file).isEqualTo(atNormal.file)
            assertThat(atFaster.speed).isEqualTo(1f)
        }

    @Test
    fun `the closest cached speed wins`() =
        runTest {
            val renderer = renderer()
            renderer.render(request(neural, speed = 1f))
            val request = request(neural)
            val atDouble =
                AudioCache(folder.root).segmentFile(request.bookId, neural.audioKey, 0, 0, text.length, speed = 2f)
            TestWav.write(atDouble, text.length * MILLIS_PER_CHAR / 2)

            val audio = renderer.render(request(neural, speed = 1.75f)) as SegmentAudio.Ready

            assertThat(engine.calls).isEqualTo(1)
            assertThat(audio.file).isEqualTo(atDouble)
            assertThat(audio.speed).isEqualTo(2f)
        }

    @Test
    fun `a voice that cannot render at speed renders at 1x whatever the listening speed`() =
        runTest {
            val audio = renderer().render(request(voice, speed = 1.5f)) as SegmentAudio.Ready

            assertThat(engine.speeds).containsExactly(1f)
            assertThat(audio.speed).isEqualTo(1f)
            assertThat(audio.file.name).isEqualTo("0-0-${text.length}.wav")
        }

    @Test
    fun `the render time an engine reports teaches how fast the voice renders on this phone`() =
        runTest {
            engine.renderMillisPerChar = 100L
            val renderer = renderer()

            repeat(RenderCost.TRUSTED_SAMPLES) { index ->
                renderer.render(request(neural).copy(range = TextRange(index, text.length)))
            }

            // Twice as long to render as to hear: slower than listening at any speed.
            assertThat(
                renderSpeeds.costs.value
                    .getValue(neural.id)
                    .secondsPerSecond,
            ).isWithin(0.01).of(2.0)
            assertThat(renderSpeeds.keepsUp(neural.id, 1f)).isFalse()
            assertThat(renderSpeeds.keepsUp(voice.id, 1f)).isNull()
        }

    private fun kotlinx.coroutines.test.TestScope.renderer() =
        SegmentRenderer(
            engine,
            AudioCache(folder.root),
            rates,
            renderSpeeds,
            clock,
            UnconfinedTestDispatcher(testScheduler),
        )

    private fun request(
        voice: Voice = this.voice,
        speed: Float = 1f,
    ) = SegmentRequest(
        bookId = "00000000-0000-0000-0000-000000000001",
        voice = voice,
        chapterIndex = 0,
        range = TextRange(0, text.length),
        text = text,
        speed = speed,
    )

    /** Speaks [MILLIS_PER_CHAR] per character at 1.0×, faster or slower at other speeds. */
    private class CountingEngine : SpeechEngine {
        var calls = 0
        var failure: SynthesisFailure? = null
        val speeds = mutableListOf<Float>()

        /** The render time to report per character, or null to report none. */
        var renderMillisPerChar: Long? = null

        override suspend fun voices(): List<Voice> = emptyList()

        override suspend fun synthesize(
            text: String,
            voice: Voice,
            output: File,
            speed: Float,
        ): SynthesisResult {
            calls++
            speeds += speed
            val failure = failure
            return if (failure != null) {
                SynthesisResult.Failed(failure)
            } else {
                val millis = (text.length * MILLIS_PER_CHAR / speed).toLong()
                TestWav.write(output, millis)
                SynthesisResult.Rendered(millis, renderMillisPerChar?.let { it * text.length })
            }
        }
    }

    private companion object {
        const val MILLIS_PER_CHAR = 50L
    }
}
