package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.tts.VoiceIds
import com.giovannimartins.rreader.ui.voices.VoiceLooks
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/** The catalogue of ADR 0018: order, names, packages pinned for SR-6, and what each voice needs to be shown. */
class NeuralVoicesTest {
    @Test
    fun `nine voices with distinct names, most recommended first`() {
        assertThat(NeuralVoices.all.map { it.name })
            .containsExactly("Dora", "Alex", "Cadu", "Santa", "Faber", "Dii", "Jeff", "Miro", "Edresson")
            .inOrder()
        assertThat(NeuralVoices.all.map { it.id }).containsNoDuplicates()
        assertThat(NeuralVoices.all.map { VoiceIds.neuralKey(it.id) }).isEqualTo(NeuralVoices.all.map { it.key })
        assertThat(NeuralVoices.byId("neural:santa")?.speakerId).isEqualTo(44)
        assertThat(NeuralVoices.byId("system:x:y")).isNull()
    }

    @Test
    fun `the Kokoro voices share one package, every Piper voice has its own`() {
        assertThat(NeuralVoices.packages).hasSize(7)
        assertThat(NeuralVoices.voicesOf(NeuralVoices.KOKORO).map { it.name }).containsExactly("Dora", "Alex", "Santa")
        assertThat(
            NeuralVoices.all
                .filter { it.slowToRender }
                .map { it.pkg }
                .toSet(),
        ).containsExactly(NeuralVoices.KOKORO)
        assertThat(
            NeuralVoices.all
                .filter {
                    it.pkg.family == ModelFamily.PIPER
                }.map { it.speakerId }
                .toSet(),
        ).containsExactly(0)
    }

    @Test
    fun `SR-6 every package is an official release archive pinned by size and SHA-256`() {
        NeuralVoices.packages.forEach { pkg ->
            assertThat(pkg.sha256).matches("[0-9a-f]{64}")
            assertThat(pkg.archiveBytes).isGreaterThan(pkg.installedBytes / 3)
            assertThat(pkg.url).startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/")
            assertThat(VoiceModelHosts.check(pkg.url)).isNotNull()
            assertThat(pkg.files.map { it.name }).containsAtLeast("model.onnx", "tokens.txt")
            assertThat(pkg.files.map { it.name }).containsNoDuplicates()
            assertThat(pkg.files.all { it.entry.startsWith(pkg.archive.removeSuffix(".tar.bz2") + "/") }).isTrue()
        }
        assertThat(NeuralVoices.packages.map { it.sha256 }).containsNoDuplicates()
        assertThat(NeuralVoices.KOKORO.files.map { it.name }).contains("voices.bin")
    }

    @Test
    fun `every voice has a bundled sample and a look of its own`() {
        NeuralVoices.all.forEach { voice ->
            val sample = File("src/main/assets/voices/samples/${voice.key}.ogg")
            assertThat(sample.isFile).isTrue()
            assertThat(sample.length()).isIn(10_000L..60_000L)
        }
        val looks = NeuralVoices.all.map(VoiceLooks::of) + VoiceLooks.PHONE
        assertThat(looks.map { it.shape }).containsNoDuplicates()
        assertThat(looks.map { it.description }).containsNoDuplicates()
        assertThat(
            File("src/main/assets/voices/samples").listFiles().orEmpty().sumOf { it.length() },
        ).isLessThan(600_000L)
    }
}
