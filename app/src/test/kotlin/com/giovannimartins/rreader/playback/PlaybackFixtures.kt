package com.giovannimartins.rreader.playback

import com.giovannimartins.rreader.textkit.model.ReadingPosition
import com.giovannimartins.rreader.tts.SpeechEngine
import com.giovannimartins.rreader.tts.SynthesisFailure
import com.giovannimartins.rreader.tts.SynthesisResult
import com.giovannimartins.rreader.tts.TestWav
import com.giovannimartins.rreader.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.Locale
import kotlin.time.Duration

internal const val BOOK_ID = "00000000-0000-0000-0000-000000000001"

internal fun voice(
    id: String = "test:engine:pt-br",
    locale: Locale = Locale.forLanguageTag("pt-BR"),
    requiresNetwork: Boolean = false,
) = Voice(
    id = id,
    engine = "engine",
    engineLabel = "Engine",
    name = id,
    locale = locale,
    requiresNetwork = requiresNetwork,
    quality = 400,
)

/** A chapter of [count] sentences of about 40 characters each, so each sentence is its own segment. */
internal fun chapterText(
    chapter: Int,
    count: Int,
): String = (1..count).joinToString(" ") { "Esta é a frase número $it do capítulo ${chapter + 1}." }

internal fun book(
    texts: List<String>,
    voice: Voice? = voice(),
    position: ReadingPosition = ReadingPosition.START,
    speed: Float = 1f,
    pausedFor: Duration? = null,
) = PlaybackBook(
    id = BOOK_ID,
    title = "Dom Casmurro",
    authors = listOf("Machado de Assis"),
    cover = null,
    chapters = texts.mapIndexed { index, text -> PlaybackChapter("Capítulo ${index + 1}", text.length) },
    voice = voice,
    position = position,
    speed = speed,
    pausedFor = pausedFor,
)

/**
 * Writes silent WAV files lasting [millisPerChar] per character of the text, shorter or longer when a voice that
 * renders at speed is asked for another speed.
 */
internal class FakeSpeechEngine(
    private val millisPerChar: Long = 10,
) : SpeechEngine {
    val spoken = mutableListOf<String>()

    /** The speed each segment was asked to be rendered at, in order. */
    val speeds = mutableListOf<Float>()

    /** The voice of each segment rendered, in order. */
    val voicesUsed = mutableListOf<String>()
    var failure: SynthesisFailure? = null

    /** Voices that fail as unavailable, as a deleted voice does. */
    var unavailableVoices: Set<String> = emptySet()

    /** Voices whose engine cannot start, as a neural voice whose model does not load. */
    var engineUnavailableVoices: Set<String> = emptySet()

    /** How many of the next renders fail as if the engine could not start, whatever their voice. */
    var engineFailures = 0

    /** When set, renders after the first [rendersBeforeGate] wait until it completes. */
    var gate: CompletableDeferred<Unit>? = null
    var rendersBeforeGate = Int.MAX_VALUE

    /** Completes when a render starts waiting at the [gate]. */
    val atGate = CompletableDeferred<Unit>()

    override suspend fun voices(): List<Voice> = listOf(voice())

    private fun engineFailure(voice: Voice): SynthesisFailure? {
        val fails = voice.id in engineUnavailableVoices || engineFailures > 0
        if (engineFailures > 0) engineFailures--
        return SynthesisFailure.ENGINE_UNAVAILABLE.takeIf { fails }
    }

    override suspend fun synthesize(
        text: String,
        voice: Voice,
        output: File,
        speed: Float,
    ): SynthesisResult {
        val gate = gate?.takeIf { spoken.size >= rendersBeforeGate }
        if (gate != null) {
            atGate.complete(Unit)
            gate.await()
        }
        spoken += text
        speeds += speed
        voicesUsed += voice.id
        val failure =
            failure ?: SynthesisFailure.VOICE_UNAVAILABLE.takeIf { voice.id in unavailableVoices }
                ?: engineFailure(voice)
        return if (failure != null) {
            SynthesisResult.Failed(failure)
        } else {
            val duration = (text.length * millisPerChar / if (voice.rendersAtSpeed) speed else 1f).toLong()
            TestWav.write(output, duration)
            SynthesisResult.Rendered(duration)
        }
    }
}

/** In-memory [PlaybackContent] for one book. */
internal class FakePlaybackContent(
    var book: PlaybackBook?,
    private val texts: List<String>,
) : PlaybackContent {
    val savedPositions = mutableListOf<ReadingPosition>()
    var savedSpeed: Float? = null
    var loads = 0

    override suspend fun load(bookId: String): BookLoad {
        loads++
        return book?.takeIf { it.id == bookId }?.let(BookLoad::Loaded) ?: BookLoad.Unavailable
    }

    override suspend fun chapterText(
        bookId: String,
        chapterIndex: Int,
    ): String = texts[chapterIndex]

    override suspend fun savePosition(
        bookId: String,
        position: ReadingPosition,
    ) {
        savedPositions += position
    }

    override suspend fun saveSpeed(speed: Float) {
        savedSpeed = speed
    }

    /** The voice of [book]: tests change it (or set [voice]) to change the voice of the open book. */
    var voice: Voice? = null

    /** Answers for the next voice lookups, in order, before [voice]; a test can make one wait. */
    val voiceAnswers = ArrayDeque<suspend () -> Voice?>()

    override suspend fun voiceFor(bookId: String): Voice? =
        voiceAnswers.removeFirstOrNull()?.invoke() ?: voice ?: book?.takeIf { it.id == bookId }?.voice

    /** The phone's voice, which reads when a neural voice cannot start; null for a phone without one. */
    var phone: Voice? = null

    override suspend fun phoneVoice(): Voice? = phone
}
