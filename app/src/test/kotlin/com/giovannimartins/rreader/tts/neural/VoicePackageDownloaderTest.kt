package com.giovannimartins.rreader.tts.neural

import com.giovannimartins.rreader.net.FakeTransport
import com.giovannimartins.rreader.net.FakeTransport.Companion.breakingBody
import com.giovannimartins.rreader.net.FakeTransport.Companion.ok
import com.giovannimartins.rreader.net.FakeTransport.Companion.partial
import com.giovannimartins.rreader.net.FakeTransport.Companion.redirect
import com.giovannimartins.rreader.tts.neural.TestArchives.MODEL
import com.giovannimartins.rreader.tts.neural.TestArchives.TOKENS
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** SR-5 and SR-6 for voice packages: where they come from, what is accepted, resumed and installed. */
@RunWith(RobolectricTestRunner::class)
class VoicePackageDownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val transport = FakeTransport()
    private val archive = TestArchives.tarBz2(TestArchives.packageEntries())
    private var pkg = TestArchives.pkg(archive)
    private var freeBytes = Long.MAX_VALUE
    private val store by lazy {
        VoicePackageStore(File(folder.root, "voices"), Dispatchers.Unconfined, { freeBytes }, listOf(pkg))
    }
    private val downloader by lazy {
        VoicePackageDownloader(VoiceModelHttp(transport, Dispatchers.Unconfined), store, Dispatchers.Unconfined)
    }
    private val progress = mutableListOf<VoiceDownloadProgress>()

    @Test
    fun `downloads the pinned archive through GitHub's redirect, verifies it and installs the kept files`() {
        answerWith { ok(archive, TYPE) }

        val outcome = download()

        assertThat(outcome).isEqualTo(VoiceDownloadOutcome.Installed)
        assertThat(store.file(pkg, "model.onnx").readBytes()).isEqualTo(MODEL)
        assertThat(store.file(pkg, "tokens.txt").readBytes()).isEqualTo(TOKENS)
        assertThat(runBlocking { store.installedNow() }).containsExactly(pkg.id)
        assertThat(store.partialFile(pkg).exists()).isFalse()
        assertThat(store.stagingDirectory(pkg).exists()).isFalse()
        assertThat(transport.requests.map { it.address }).containsExactly(pkg.url, ASSET).inOrder()
        assertThat(progress).contains(VoiceDownloadProgress.Downloading(1f))
        assertThat(progress.last()).isEqualTo(VoiceDownloadProgress.Installing)
    }

    @Test
    fun `refuses an archive whose SHA-256 is not the pinned one, and keeps nothing of it`() {
        pkg = TestArchives.pkg(archive, sha256 = "0".repeat(64))
        answerWith { ok(archive, TYPE) }

        val outcome = download()

        assertThat(outcome).isEqualTo(VoiceDownloadOutcome.Failed(VoiceDownloadFailure.DAMAGED, retryable = false))
        assertThat(store.isComplete(pkg)).isFalse()
        assertThat(store.partialFile(pkg).exists()).isFalse()
        assertThat(store.directory(pkg).exists()).isFalse()
    }

    @Test
    fun `refuses an archive larger than the pinned size without reading it`() {
        val body = FakeTransport.Companion.CountingBody(archive.size + 1)
        answerWith { ok(body, TYPE, contentLength = archive.size + 1L) }

        val outcome = download()

        assertThat(outcome).isEqualTo(VoiceDownloadOutcome.Failed(VoiceDownloadFailure.UNAVAILABLE, retryable = false))
        assertThat(body.read).isEqualTo(0)
        assertThat(store.partialFile(pkg).exists()).isFalse()
    }

    @Test
    fun `resumes an interrupted download where it stopped`() {
        val half = archive.size / 2
        store.partialFile(pkg).apply { parentFile?.mkdirs() }.writeBytes(archive.copyOfRange(0, half))
        answerWith {
            val range = transport.requests.last().headers["Range"]
            if (range == "bytes=$half-") {
                val rest = archive.copyOfRange(half, archive.size)
                partial(rest, TYPE, "bytes $half-${archive.size - 1}/${archive.size}")
            } else {
                ok(archive, TYPE)
            }
        }

        val outcome = download()

        assertThat(outcome).isEqualTo(VoiceDownloadOutcome.Installed)
        assertThat(transport.requests.map { it.headers["Range"] }).containsExactly("bytes=$half-", "bytes=$half-")
        assertThat(store.file(pkg, "model.onnx").readBytes()).isEqualTo(MODEL)
    }

    @Test
    fun `a resumed archive whose first bytes are corrupt is refused as damaged, and nothing of it is kept`() {
        val half = archive.size / 2
        store.partialFile(pkg).apply { parentFile?.mkdirs() }.writeBytes(ByteArray(half) { 0x55 })
        answerWith {
            val rest = archive.copyOfRange(half, archive.size)
            partial(rest, TYPE, "bytes $half-${archive.size - 1}/${archive.size}")
        }

        val outcome = download()

        assertThat(outcome).isEqualTo(VoiceDownloadOutcome.Failed(VoiceDownloadFailure.DAMAGED, retryable = false))
        assertThat(transport.requests.last().headers["Range"]).isEqualTo("bytes=$half-")
        assertThat(store.partialFile(pkg).exists()).isFalse()
        assertThat(store.stagingDirectory(pkg).exists()).isFalse()
        assertThat(store.isComplete(pkg)).isFalse()
    }

    @Test
    fun `a partial archive is named after its pin, so one of an earlier pin is deleted and never resumed`() {
        val before = pkg
        store.partialFile(before).apply { parentFile?.mkdirs() }.writeBytes(archive.copyOfRange(0, archive.size / 2))
        val legacy = File(store.partialFile(before).parentFile, "${before.id}.part").apply { writeBytes(ByteArray(7)) }
        val newModel = MODEL.reversedArray()
        val newArchive = TestArchives.tarBz2(TestArchives.packageEntries(model = newModel))
        pkg = TestArchives.pkg(newArchive, model = newModel)
        answerWith { ok(newArchive, TYPE) }

        val outcome = download()

        assertThat(store.partialFile(pkg).name).isEqualTo("test-${pkg.sha256.take(16)}.part")
        assertThat(store.partialFile(pkg)).isNotEqualTo(store.partialFile(before))
        assertThat(outcome).isEqualTo(VoiceDownloadOutcome.Installed)
        assertThat(transport.requests.map { it.headers["Range"] }.toSet()).containsExactly(null)
        assertThat(store.file(pkg, "model.onnx").readBytes()).isEqualTo(newModel)
        assertThat(store.partialFile(before).exists()).isFalse()
        assertThat(legacy.exists()).isFalse()
    }

    @Test
    fun `starts over when the server sends the whole file instead of the rest`() {
        store.partialFile(pkg).apply { parentFile?.mkdirs() }.writeBytes(ByteArray(10) { 9 })
        answerWith { ok(archive, TYPE) }

        val outcome = download()

        assertThat(outcome).isEqualTo(VoiceDownloadOutcome.Installed)
        val ranges = transport.requests.filter { it.address == ASSET }.map { it.headers["Range"] }
        assertThat(ranges).containsExactly("bytes=10-", null).inOrder()
    }

    @Test
    fun `a broken connection keeps what arrived, for the next attempt to resume`() {
        answerWith { ok(breakingBody(1_000), TYPE, contentLength = archive.size.toLong()) }

        val outcome = download()

        assertThat(outcome).isEqualTo(VoiceDownloadOutcome.Failed(VoiceDownloadFailure.NETWORK, retryable = true))
        assertThat(store.partialFile(pkg).length()).isEqualTo(1_000)
        assertThat(store.isComplete(pkg)).isFalse()
    }

    @Test
    fun `a redirect to another host is refused without contacting it`() {
        transport.answer(pkg.url) { redirect("https://mirror.example/vits-piper-test.tar.bz2") }

        val outcome = download()

        assertThat(outcome).isEqualTo(VoiceDownloadOutcome.Failed(VoiceDownloadFailure.UNAVAILABLE, retryable = false))
        assertThat(transport.requests.map { it.address }).containsExactly(pkg.url)
    }

    @Test
    fun `does not start without room for the archive and the unpacked files`() {
        freeBytes = pkg.archiveBytes + pkg.installedBytes

        val outcome = download()

        assertThat(outcome).isEqualTo(VoiceDownloadOutcome.Failed(VoiceDownloadFailure.STORAGE, retryable = false))
        assertThat(transport.requests).isEmpty()
    }

    @Test
    fun `an installed package is not downloaded again, and discarding keeps it`() {
        answerWith { ok(archive, TYPE) }
        download()
        transport.requests.clear()

        assertThat(download()).isEqualTo(VoiceDownloadOutcome.Installed)
        store.partialFile(pkg).writeBytes(ByteArray(3))
        runBlocking { downloader.discard(pkg) }

        assertThat(transport.requests).isEmpty()
        assertThat(store.partialFile(pkg).exists()).isFalse()
        assertThat(store.isComplete(pkg)).isTrue()
    }

    @Test
    fun `what a download left after its package was put in place is deleted when the package is found installed`() {
        answerWith { ok(archive, TYPE) }
        download()
        // The process died after the package was moved into place, before its archive was deleted.
        store.partialFile(pkg).writeBytes(archive)
        File(store.stagingDirectory(pkg), "model.onnx").apply { parentFile?.mkdirs() }.writeBytes(MODEL)
        transport.requests.clear()

        assertThat(download()).isEqualTo(VoiceDownloadOutcome.Installed)

        assertThat(transport.requests).isEmpty()
        assertThat(store.partialFile(pkg).exists()).isFalse()
        assertThat(store.stagingDirectory(pkg).exists()).isFalse()
        assertThat(store.isComplete(pkg)).isTrue()
    }

    @Test
    fun `deleting a package removes its files and its partial download`() {
        answerWith { ok(archive, TYPE) }
        download()
        store.partialFile(pkg).writeBytes(ByteArray(3))

        runBlocking { store.delete(pkg) }

        assertThat(store.directory(pkg).exists()).isFalse()
        assertThat(store.partialFile(pkg).exists()).isFalse()
        assertThat(runBlocking { store.installedNow() }).isEmpty()
    }

    /** The release address redirects to the asset host, as GitHub does, which answers with [answer]. */
    private fun answerWith(answer: () -> com.giovannimartins.rreader.net.HttpResponse) {
        transport.answer(pkg.url) { redirect(ASSET) }
        transport.answer(ASSET, answer)
    }

    private fun download(): VoiceDownloadOutcome = runBlocking { downloader.download(pkg) { progress += it } }

    private companion object {
        const val TYPE = "application/octet-stream"
        const val ASSET = "https://release-assets.githubusercontent.com/github-production-release-asset/1/a1?sig=x"
    }
}
