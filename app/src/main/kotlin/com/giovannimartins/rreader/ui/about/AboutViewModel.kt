package com.giovannimartins.rreader.ui.about

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * A component of the app and its licence: shown by [name] (a plain name, or [nameRes] when it needs translating),
 * the licence's short [licence] name, and where its terms are: a text shipped in `assets/licenses/` ([asset]) or a
 * web page ([url]).
 */
data class AboutComponent(
    val name: String = "",
    @param:StringRes val nameRes: Int = 0,
    val licence: String,
    val asset: String? = null,
    val url: String? = null,
)

/** What the About and licences screen lists. */
object AboutContent {
    /** The public repository with the source code (the GPL's corresponding source, ADR 0018). */
    const val SOURCE_URL = "https://github.com/giovannism20/redesigned-octo-palm-tree"

    private const val APACHE = "Apache License 2.0"
    private const val APACHE_TEXT = "Apache-2.0.txt"

    val components: List<AboutComponent> =
        listOf(
            AboutComponent(nameRes = R.string.about_component_app, licence = "MIT License", asset = "MIT-r-reader.txt"),
            AboutComponent(
                nameRes = R.string.about_component_phonemizer,
                licence = "GNU GPL 3.0",
                asset = "GPL-3.0.txt",
            ),
            AboutComponent(name = "sherpa-onnx", licence = APACHE, asset = APACHE_TEXT),
            AboutComponent(name = "ONNX Runtime", licence = "MIT License", asset = "MIT-onnxruntime.txt"),
            AboutComponent(nameRes = R.string.about_component_androidx, licence = APACHE, asset = APACHE_TEXT),
            AboutComponent(
                nameRes = R.string.about_component_commons,
                licence = APACHE,
                asset = "Apache-2.0-apache-commons.txt",
            ),
            AboutComponent(name = "PdfBox-Android", licence = APACHE, asset = "Apache-2.0-pdfbox-android.txt"),
            AboutComponent(name = "jsoup", licence = "MIT License", asset = "MIT-jsoup.txt"),
            AboutComponent(name = "Bouncy Castle", licence = "MIT License", asset = "MIT-bouncy-castle.txt"),
            AboutComponent(
                nameRes = R.string.about_component_mlkit,
                licence = "ML Kit Terms of Service",
                url = "https://developers.google.com/ml-kit/terms",
            ),
            AboutComponent(
                name = "Atkinson Hyperlegible Next",
                licence = "SIL Open Font License 1.1",
                asset = "OFL-1.1-atkinson-hyperlegible-next.txt",
            ),
        )

    /** The terms of the voice models (ADR 0018), each with where to read them. */
    val voiceTerms: List<Pair<Int, AboutComponent>> =
        listOf(
            R.string.about_terms_kokoro to AboutComponent(name = "Kokoro", licence = APACHE, asset = APACHE_TEXT),
            R.string.about_terms_piper_cc0 to
                AboutComponent(
                    name = "Cadu, Faber, Jeff",
                    licence = "CC0 1.0",
                    url = "https://creativecommons.org/publicdomain/zero/1.0/",
                ),
            // The README in the package the app downloads gives CC BY-NC-SA 4.0; the author's model card gives
            // CC BY-NC-ND 4.0 today. Both are named, and the stricter, current one is linked.
            R.string.about_terms_piper_nc to
                AboutComponent(
                    name = "Dii, Miro",
                    licence = "CC BY-NC-SA 4.0, CC BY-NC-ND 4.0",
                    url = "https://creativecommons.org/licenses/by-nc-nd/4.0/",
                ),
            R.string.about_terms_edresson to
                AboutComponent(
                    name = "Edresson",
                    licence = "CC BY 4.0",
                    url = "https://creativecommons.org/licenses/by/4.0/",
                ),
        )
}

/** A licence text opened for reading: its component and its paragraphs (empty while it loads). */
data class OpenLicence(
    val component: AboutComponent,
    val paragraphs: List<String> = emptyList(),
    val failed: Boolean = false,
)

/** Reads the licence texts shipped in `assets/licenses/`. */
class LicenceTexts
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        /** The paragraphs of licence text [asset]. @throws IOException when it cannot be read. */
        suspend fun paragraphs(asset: String): List<String> =
            withContext(io) {
                context.assets
                    .open("licenses/$asset")
                    .bufferedReader()
                    .use { it.readText() }
                    .split(PARAGRAPH_BREAK)
                    .map(String::trim)
                    .filter(String::isNotEmpty)
            }

        private companion object {
            val PARAGRAPH_BREAK = Regex("""\n\s*\n""")
        }
    }

/** State of the About and licences screen: the licence being read, if any. */
@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        private val texts: LicenceTexts,
    ) : ViewModel() {
        private val opened = MutableStateFlow<OpenLicence?>(null)

        /** The licence text being read, or null while the list is shown. */
        val licence: StateFlow<OpenLicence?> = opened.asStateFlow()

        /** Opens the licence text of [component], which must have an [AboutComponent.asset]. */
        fun read(component: AboutComponent) {
            val asset = component.asset ?: return
            opened.value = OpenLicence(component)
            viewModelScope.launch {
                val text =
                    try {
                        OpenLicence(component, texts.paragraphs(asset))
                    } catch (e: IOException) {
                        Diagnostics.recordHandled("read licence text", e)
                        OpenLicence(component, failed = true)
                    }
                if (opened.value?.component == component) opened.value = text
            }
        }

        /** Back from a licence text to the list. */
        fun closeLicence() {
            opened.value = null
        }
    }
