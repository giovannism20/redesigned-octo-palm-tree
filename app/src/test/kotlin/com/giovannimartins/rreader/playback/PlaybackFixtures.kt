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
) = PlaybackBook(
    id = BOOK_ID,
    title = "Dom Casmurro",
    authors = listOf("Machado de Assis"),
    cover = null,
    chapters = texts.mapIndexed { index, text -> PlaybackChapter("Capítulo ${index + 1}", text.length) },
    voice = voice,
    position = position,
    speed = speed,
)

/** Writes silent WAV files lasting [millisPerChar] per character of the text. */
internal class FakeSpeechEngine(
    private val millisPerChar: Long = 10,
) : SpeechEngine {
    val spoken = mutableListOf<String>()
    var failure: SynthesisFailure? = null

    /** When set, renders after the first [rendersBeforeGate] wait until it completes. */
    var gate: CompletableDeferred<Unit>? = null
    var rendersBeforeGate = Int.MAX_VALUE

    override suspend fun voices(): List<Voice> = listOf(voice())

    override suspend fun synthesize(
        text: String,
        voice: Voice,
        output: File,
    ): SynthesisResult {
        if (spoken.size >= rendersBeforeGate) gate?.await()
        spoken += text
        val failure = failure
        return if (failure != null) {
            SynthesisResult.Failed(failure)
        } else {
            val duration = text.length * millisPerChar
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
}
