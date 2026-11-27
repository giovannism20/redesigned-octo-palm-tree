package com.giovannimartins.rreader.ui.player

import androidx.compose.runtime.Immutable
import com.giovannimartins.rreader.data.library.LibraryRepository
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.tts.VoiceIds
import com.giovannimartins.rreader.tts.neural.NeuralVoice
import com.giovannimartins.rreader.tts.neural.NeuralVoices
import com.giovannimartins.rreader.tts.neural.VoicePackageStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** A voice the player's chooser offers: a neural voice ready on the phone ([neural]), or the phone's voice (null). */
data class VoiceChoice(
    val voiceId: String,
    val neural: NeuralVoice?,
    val inUse: Boolean,
)

/** The voices the chooser lists, as one stable value for the screen. */
@Immutable
data class VoiceMenu(
    val choices: List<VoiceChoice> = emptyList(),
)

/**
 * The voices a book can be read with right away (the installed neural voices, most recommended first, and the phone's
 * voice) and the choice of one, for this book only or for every book.
 */
class VoiceChoices
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val library: LibraryRepository,
        private val store: VoicePackageStore,
    ) {
        /**
         * The choices for book [bookId], the one that reads it marked as in use: its own voice, else the default voice,
         * skipping a neural voice that is not installed, else the phone's voice (as `VoiceSelector` decides). A phone
         * voice chosen by name is shown as the phone's voice.
         */
        fun forBook(bookId: String): Flow<VoiceMenu> =
            combine(settings.settings, library.voiceOf(bookId), store.installed) { prefs, own, installed ->
                val ready = NeuralVoices.all.filter { it.pkg.id in installed }
                val usable = { id: String? -> id != null && (NeuralVoices.byId(id)?.let { it in ready } ?: true) }
                val chosen = listOf(own, prefs.defaultVoiceId).firstOrNull(usable)
                val inUse = ready.firstOrNull { it.id == chosen }
                VoiceMenu(
                    ready.map {
                        VoiceChoice(
                            it.id,
                            it,
                            it == inUse,
                        )
                    } + VoiceChoice(VoiceIds.PHONE, null, inUse == null),
                )
            }

        /**
         * Reads book [bookId] with [voiceId]: only this book, or every book ([forAllBooks]: it becomes the default
         * voice and this book follows the default again).
         */
        suspend fun choose(
            bookId: String,
            voiceId: String,
            forAllBooks: Boolean,
        ) {
            if (forAllBooks) {
                settings.setDefaultVoice(voiceId)
                library.setVoice(bookId, null)
            } else {
                library.setVoice(bookId, voiceId)
            }
        }
    }
