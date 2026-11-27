package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.tts.VoiceIds

/** How a voice package's model is loaded and how its phonemes are prepared. */
enum class ModelFamily {
    /** Kokoro v1.0 (int8): several speakers in one model; slower than real time on many phones. */
    KOKORO,

    /** Piper (VITS): one speaker per model; much faster than real time. */
    PIPER,
}

/** One file kept from a package archive: [entry] in the archive is stored as [name], and must be [bytes] long. */
data class PackageFile(
    val entry: String,
    val name: String,
    val bytes: Long,
)

/**
 * A downloadable voice package: one archive of the official sherpa-onnx releases, pinned by size and SHA-256 (SR-6).
 * Only [files] are kept from it; the rest of the archive (other languages, the espeak-ng data the app already has,
 * READMEs) is skipped.
 */
data class VoicePackage(
    /** Stable id, also the name of the package's folder (`filesDir/voices/{id}`). */
    val id: String,
    val family: ModelFamily,
    /** The archive's file name in the `tts-models` release. */
    val archive: String,
    val archiveBytes: Long,
    /** Lower-case hex SHA-256 of the archive. */
    val sha256: String,
    val files: List<PackageFile>,
) {
    /** Where the archive is downloaded from (HTTPS, `github.com`, which redirects to its release asset host). */
    val url: String get() = RELEASES + archive

    /** Disk space the kept files take once installed. */
    val installedBytes: Long get() = files.sumOf(PackageFile::bytes)

    private companion object {
        const val RELEASES = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
    }
}

/**
 * An offline neural voice of ADR 0018. Several voices can share a [pkg] (the Kokoro voices); [speakerId] picks the
 * voice inside its model. [gainDb] brings the voice to the common speech level measured in the evaluation, so every
 * voice sounds about as loud.
 */
data class NeuralVoice(
    /** Stable key, part of the voice id (`neural:{key}`) and the name of its bundled sample. */
    val key: String,
    /** The voice's name, the same in every language. */
    val name: String,
    val pkg: VoicePackage,
    val speakerId: Int,
    val gainDb: Float,
) {
    val id: String get() = VoiceIds.neural(key)

    /** Whether the voice may render slower than real time on a phone, so chapters are rendered ahead (ADR 0018). */
    val slowToRender: Boolean get() = pkg.family == ModelFamily.KOKORO
}

/**
 * The nine voices of ADR 0018, in the order they are recommended, and their packages. Sizes and SHA-256 values are
 * those of the official `tts-models` release of sherpa-onnx (checked against downloads on 2026-11-27, matching the
 * digests GitHub publishes); speaker ids and gains come from the voice evaluation (docs/design/voices-evaluation.md).
 */
object NeuralVoices {
    private const val TOKENS = "tokens.txt"
    private const val MODEL = "model.onnx"

    val KOKORO =
        VoicePackage(
            id = "kokoro",
            family = ModelFamily.KOKORO,
            archive = "kokoro-int8-multi-lang-v1_0.tar.bz2",
            archiveBytes = 132_353_410,
            sha256 = "72c347230107ef8a31098ec4d468a605244beef5f1ed00db97efa13518800653",
            files =
                listOf(
                    PackageFile("kokoro-int8-multi-lang-v1_0/model.int8.onnx", MODEL, 114_202_993),
                    PackageFile("kokoro-int8-multi-lang-v1_0/voices.bin", "voices.bin", 28_200_960),
                    PackageFile("kokoro-int8-multi-lang-v1_0/tokens.txt", TOKENS, 687),
                ),
        )

    val CADU =
        piper(
            name = "cadu",
            quality = "medium",
            archiveBytes = 67_207_562,
            sha256 = "aba78157d4b89acc17ddef15a70f4b2474f4c189d4de6035002ac7fec9d5d303",
            modelBytes = 62_950_207,
            tokensBytes = 968,
        )
    val FABER =
        piper(
            name = "faber",
            quality = "medium",
            archiveBytes = 67_183_065,
            sha256 = "7add3f923ad6bc25ca8a192805fd1a64d1b3893e4611c4a9719545a825039a83",
            modelBytes = 63_201_457,
            tokensBytes = 907,
        )
    val JEFF =
        piper(
            name = "jeff",
            quality = "medium",
            archiveBytes = 67_207_052,
            sha256 = "da4c870fc7b20600c74261b3e1dd1c816da2ce063e18c46e1f2cd86dea88f3f0",
            modelBytes = 62_950_207,
            tokensBytes = 968,
        )
    val DII =
        piper(
            name = "dii",
            quality = "high",
            archiveBytes = 67_238_016,
            sha256 = "76bdf009bb29e69b12336de833d0d219ffa558f4281330eb141e4a0569c108a2",
            modelBytes = 63_511_201,
            tokensBytes = 954,
        )
    val MIRO =
        piper(
            name = "miro",
            quality = "high",
            archiveBytes = 67_207_707,
            sha256 = "7f30d1c3421362a60381807543aeafd7e1cd0acbcb92c349a29908526c6f4e6f",
            modelBytes = 63_511_201,
            tokensBytes = 954,
        )
    val EDRESSON =
        piper(
            name = "edresson",
            quality = "low",
            archiveBytes = 67_081_775,
            sha256 = "670569e755cc48cda0a2f6a3ccba0698815566297069961edcd5f7daaf3d47a5",
            modelBytes = 63_104_689,
            tokensBytes = 763,
        )

    /** Every voice, most recommended first. */
    val all: List<NeuralVoice> =
        listOf(
            NeuralVoice("dora", "Dora", KOKORO, speakerId = 42, gainDb = 4.9f),
            NeuralVoice("alex", "Alex", KOKORO, speakerId = 43, gainDb = 4.1f),
            NeuralVoice("cadu", "Cadu", CADU, speakerId = 0, gainDb = 1.1f),
            NeuralVoice("santa", "Santa", KOKORO, speakerId = 44, gainDb = 4.2f),
            NeuralVoice("faber", "Faber", FABER, speakerId = 0, gainDb = -0.8f),
            NeuralVoice("dii", "Dii", DII, speakerId = 0, gainDb = -2.9f),
            NeuralVoice("jeff", "Jeff", JEFF, speakerId = 0, gainDb = 1.1f),
            NeuralVoice("miro", "Miro", MIRO, speakerId = 0, gainDb = 12.2f),
            NeuralVoice("edresson", "Edresson", EDRESSON, speakerId = 0, gainDb = 11.0f),
        )

    /** Every package, in the order of the first voice that needs it. */
    val packages: List<VoicePackage> = all.map(NeuralVoice::pkg).distinct()

    /** The voice with [voiceId] (`neural:{key}`), or null when it is not a neural voice of this catalogue. */
    fun byId(voiceId: String?): NeuralVoice? =
        VoiceIds.neuralKey(voiceId)?.let { key -> all.firstOrNull { it.key == key } }

    /** The package with [id], or null. */
    fun packageById(id: String?): VoicePackage? = packages.firstOrNull { it.id == id }

    /** The voices a package provides, in the recommended order. */
    fun voicesOf(pkg: VoicePackage): List<NeuralVoice> = all.filter { it.pkg == pkg }

    /** A Piper pt-BR voice package: one model and its tokens, from `vits-piper-pt_BR-{name}-{quality}.tar.bz2`. */
    private fun piper(
        name: String,
        quality: String,
        archiveBytes: Long,
        sha256: String,
        modelBytes: Long,
        tokensBytes: Long,
    ): VoicePackage {
        val folder = "vits-piper-pt_BR-$name-$quality"
        return VoicePackage(
            id = name,
            family = ModelFamily.PIPER,
            archive = "$folder.tar.bz2",
            archiveBytes = archiveBytes,
            sha256 = sha256,
            files =
                listOf(
                    PackageFile("$folder/pt_BR-$name-$quality.onnx", MODEL, modelBytes),
                    PackageFile("$folder/tokens.txt", TOKENS, tokensBytes),
                ),
        )
    }
}
