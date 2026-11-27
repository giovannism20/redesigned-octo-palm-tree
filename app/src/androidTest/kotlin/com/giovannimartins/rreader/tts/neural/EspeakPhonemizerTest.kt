package com.giovannimartins.rreader.tts.neural

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * espeak-ng on the phone with text that makes it spell letters it cannot name in Portuguese (it falls back to its
 * English dictionary), switch to German or French for a few words, or meet other alphabets. With only the
 * Portuguese data shipped, such text crashed the app's process in native code (SIGSEGV in LookupDict2).
 */
@RunWith(AndroidJUnit4::class)
class EspeakPhonemizerTest {
    @Test
    fun readsTextThatMakesEspeakSpellLettersOrSwitchLanguage() {
        listOf(
            "Pòde ser, sòmente È assim; è ò.",
            "Shakespeare, Wagner, Walter, Franklin, Andarahy, Catumby, Egypto, Olympio.",
            "Louis visitou Feuerbach na Straße.",
            "w k y W K Y æ ø œ ł ŋ ð þ",
            "Custa $ 5 • # + = & > % ™ © ® € ½ ª º §",
            "Palavras gregas λόγος e russas Привет, e chinês 中文 😀.",
        ).forEach { text ->
            assertThat(runtime.phonemize(text)).isNotEmpty()
        }
    }

    @Test
    fun textOnlyInOtherAlphabetsHasNoPhonemes() {
        assertThat(runtime.phonemize("λόγος Привет 中文")).isEmpty()
    }

    private companion object {
        /** espeak-ng starts once per process, so every test shares one runtime, as the app does (a singleton). */
        val runtime by lazy { SherpaOnnxRuntime(ApplicationProvider.getApplicationContext()) }
    }
}
