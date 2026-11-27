package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.data.files.BookFileStore
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SR-4: the note of the native call an import is making. Under Robolectric for the Android log. */
@RunWith(RobolectricTestRunner::class)
class NativeCallMarkersTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val files by lazy { BookFileStore(folder.root) }
    private val markers by lazy { NativeCallMarkers(files) }
    private val marker by lazy { files.nativeCallFile(BOOK_ID) }

    @Test
    fun `notes a native call on disk only while it runs, also when it throws`() {
        val call = NativeCall(NativeStage.OCR_PAGE, 7)
        val guard = markers.forRun(BOOK_ID)

        val noted = guard.run(call) { marker.readText() }

        assertThat(noted).isEqualTo("OCR_PAGE 7 0")
        assertThat(marker.exists()).isFalse()
        assertThrows(IllegalStateException::class.java) { guard.run(call) { error("the renderer failed") } }
        assertThat(marker.exists()).isFalse()
    }

    @Test
    fun `skips a call only once the process died in it twice in a row`() {
        val page = NativeCall(NativeStage.OCR_PAGE, 3)

        dieIn(page)
        assertThat(markers.forRun(BOOK_ID).shouldSkip(page)).isFalse()
        dieIn(page)
        val guard = markers.forRun(BOOK_ID)

        assertThat(guard.shouldSkip(page)).isTrue()
        assertThat(guard.shouldSkip(NativeCall(NativeStage.OCR_PAGE, 4))).isFalse()
        assertThat(guard.shouldSkip(NativeCall(NativeStage.COVER))).isFalse()
    }

    @Test
    fun `a death in another call starts the count again`() {
        dieIn(NativeCall(NativeStage.OCR_PAGE, 3))
        dieIn(NativeCall(NativeStage.COVER))

        val guard = markers.forRun(BOOK_ID)

        assertThat(guard.shouldSkip(NativeCall(NativeStage.COVER))).isFalse()
        assertThat(guard.shouldSkip(NativeCall(NativeStage.OCR_PAGE, 3))).isFalse()
    }

    @Test
    fun `ignores a marker that is damaged or too large`() {
        val call = NativeCall(NativeStage.OCR_PAGE, 1)
        listOf(
            "",
            "OCR_PAGE",
            "OCR_PAGE x 9",
            "DRAW 1 9",
            "OCR_PAGE -1 9",
            "OCR_PAGE 1 9 9",
            "OCR_PAGE 1 ${"9".repeat(80)}",
        ).forEach { text ->
            marker.parentFile!!.mkdirs()
            marker.writeText(text)

            assertThat(markers.forRun(BOOK_ID).shouldSkip(call)).isFalse()
        }
    }

    /** A run that dies inside [call]: the note it wrote stays on disk, as after a crash in native code. */
    private fun dieIn(call: NativeCall) {
        var left = ByteArray(0)
        assertThrows(ProcessDeath::class.java) {
            markers.forRun(BOOK_ID).run(call) {
                left = marker.readBytes()
                throw ProcessDeath()
            }
        }
        marker.writeBytes(left)
    }

    /** Stands for the process being killed by a crash in native code: nothing in the app catches it. */
    private class ProcessDeath : Error("process died in native code")

    private companion object {
        const val BOOK_ID = "00000000-0000-4000-8000-000000000001"
    }
}
