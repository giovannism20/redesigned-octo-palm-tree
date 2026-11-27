package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.textkit.estimate.SpeakingRate
import com.giovannimartins.rreader.textkit.model.TextRange
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val engine = CountingEngine()
    private val rates = SpeakingRateTracker()
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
            assertThat(File(first.file.parentFile, "${first.file.name}.part").exists()).isFalse()
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

    private fun kotlinx.coroutines.test.TestScope.renderer() =
        SegmentRenderer(engine, AudioCache(folder.root), rates, clock, UnconfinedTestDispatcher(testScheduler))

    private fun request() =
        SegmentRequest(
            bookId = "00000000-0000-0000-0000-000000000001",
            voice = voice,
            chapterIndex = 0,
            range = TextRange(0, text.length),
            text = text,
        )

    private class CountingEngine : SpeechEngine {
        var calls = 0
        var failure: SynthesisFailure? = null

        override suspend fun voices(): List<Voice> = emptyList()

        override suspend fun synthesize(
            text: String,
            voice: Voice,
            output: File,
        ): SynthesisResult {
            calls++
            val failure = failure
            return if (failure != null) {
                SynthesisResult.Failed(failure)
            } else {
                TestWav.write(output, text.length * MILLIS_PER_CHAR)
                SynthesisResult.Rendered(text.length * MILLIS_PER_CHAR)
            }
        }
    }

    private companion object {
        const val MILLIS_PER_CHAR = 50L
    }
}
