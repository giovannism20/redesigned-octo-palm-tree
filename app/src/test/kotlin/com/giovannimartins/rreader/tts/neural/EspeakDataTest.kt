package com.giovannimartins.rreader.tts.neural

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The espeak-ng data copied from the app's assets to the folder espeak-ng reads, under Robolectric for the assets. */
@RunWith(RobolectricTestRunner::class)
class EspeakDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `copies every file whole and marks the copy complete`() {
        val folder = EspeakData.install(context)

        EspeakData.FILES.forEach { name -> assertThat(File(folder, name).readBytes()).isEqualTo(asset(name)) }
        assertThat(File(folder, ".complete").isFile).isTrue()
    }

    @Test
    fun `a copy that was never marked complete is copied again`() {
        val folder = EspeakData.install(context)
        File(folder, ".complete").delete()
        File(folder, "pt_dict").writeBytes(ByteArray(3))

        val again = EspeakData.install(context)

        assertThat(File(again, "pt_dict").readBytes()).isEqualTo(asset("pt_dict"))
        assertThat(File(again, ".complete").isFile).isTrue()
    }

    private fun asset(name: String): ByteArray = context.assets.open("espeak-pt/$name").use { it.readBytes() }
}
