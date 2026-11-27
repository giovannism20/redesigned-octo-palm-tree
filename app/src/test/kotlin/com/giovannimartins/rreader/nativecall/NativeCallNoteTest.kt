package com.giovannimartins.rreader.nativecall

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** SR-4: the note of the native call being made, by key. Under Robolectric for the Android log. */
@RunWith(RobolectricTestRunner::class)
class NativeCallNoteTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "calls/native.inflight") }

    @Test
    fun `notes the call by its key while it runs, and skips it after two deaths in a row`() {
        assertThat(NativeCallNote(file).run("load kokoro") { file.readText() }).isEqualTo("load kokoro 0")
        assertThat(file.exists()).isFalse()

        dieIn("load kokoro")
        assertThat(NativeCallNote(file).shouldSkip("load kokoro")).isFalse()
        dieIn("load kokoro")

        val note = NativeCallNote(file)
        assertThat(note.shouldSkip("load kokoro")).isTrue()
        assertThat(note.shouldSkip("load cadu")).isFalse()
    }

    @Test
    fun `a forgotten call is made again, also after the note is read`() {
        repeat(NativeCallNote.MAX_DEATHS) { dieIn("load kokoro") }
        val note = NativeCallNote(file)

        note.forget("load cadu")
        assertThat(note.shouldSkip("load kokoro")).isTrue()
        note.forget("load kokoro")

        assertThat(note.shouldSkip("load kokoro")).isFalse()
        assertThat(file.exists()).isFalse()
        assertThat(NativeCallNote(file).shouldSkip("load kokoro")).isFalse()
    }

    /** A process that dies inside the call [key]: the note it wrote stays on disk, as after a crash in native code. */
    private fun dieIn(key: String) {
        var left = ByteArray(0)
        assertThrows(ProcessDeath::class.java) {
            NativeCallNote(file).run(key) {
                left = file.readBytes()
                throw ProcessDeath()
            }
        }
        file.writeBytes(left)
    }

    /** Stands for the process being killed by a crash in native code: nothing in the app catches it. */
    private class ProcessDeath : Error("process died in native code")
}
