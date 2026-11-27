package com.giovannimartins.rreader.tts.neural

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * The espeak-ng data the app ships must be closed under language switches: a compiled dictionary marks a switch as
 * the byte 0x15 (phonSWITCH), a language name and a NUL, and espeak-ng crashes in native code when it switches to a
 * dictionary that is missing (it did on the phone with only the Portuguese one).
 */
class EspeakDictionariesTest {
    private val data = File("src/main/assets/espeak-pt")

    @Test
    fun `every language a shipped dictionary switches to is shipped`() {
        val shipped = dictionaries().keys

        dictionaries().forEach { (language, file) ->
            assertWithMessage("dictionaries ${language}_dict switches to that are not shipped")
                .that(switchTargets(file) - shipped)
                .isEmpty()
        }
        assertThat(shipped).containsAtLeast("pt", "en")
    }

    @Test
    fun `the files copied to the phone are exactly the shipped ones`() {
        val assets =
            data
                .walkTopDown()
                .filter(File::isFile)
                .map { it.relativeTo(data).invariantSeparatorsPath }
                .toSet()

        assertThat(EspeakData.FILES.toSet()).isEqualTo(assets)
    }

    private fun dictionaries(): Map<String, File> =
        data
            .listFiles { file -> file.name.endsWith("_dict") }
            .orEmpty()
            .associateBy { it.name.removeSuffix("_dict") }

    /** The languages a compiled dictionary switches to (its region, as in "en-us", does not matter). */
    private fun switchTargets(file: File): Set<String> {
        val bytes = file.readBytes()
        val targets = mutableSetOf<String>()
        for (start in bytes.indices) {
            if (bytes[start] != PHON_SWITCH) continue
            val name = StringBuilder()
            var index = start + 1
            while (index < bytes.size && name.length <= MAX_NAME && bytes[index].toInt().toChar().isNameChar()) {
                name.append(bytes[index].toInt().toChar())
                index++
            }
            if (index < bytes.size && bytes[index] == NUL && name.length >= 2) {
                targets += name.toString().substringBefore('-')
            }
        }
        return targets
    }

    private fun Char.isNameChar() = this in 'a'..'z' || this == '-'

    private companion object {
        const val PHON_SWITCH: Byte = 0x15
        const val NUL: Byte = 0
        const val MAX_NAME = 12
    }
}
