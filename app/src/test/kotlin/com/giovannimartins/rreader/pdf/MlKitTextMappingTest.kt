package com.giovannimartins.rreader.pdf

import android.graphics.Point
import android.graphics.Rect
import com.giovannimartins.rreader.textkit.layout.RecognizedBlock
import com.giovannimartins.rreader.textkit.layout.RecognizedLine
import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.text.Text
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** How ML Kit's result becomes recognized blocks; recognizing real pages needs a device (see androidTest). */
@RunWith(RobolectricTestRunner::class)
class MlKitTextMappingTest {
    @Test
    fun `keeps ML Kit's blocks and lines in order with their pixel boxes`() {
        val text =
            Text(
                "CAPÍTULO I\nUma noite destas,\nvindo da cidade",
                listOf(
                    block(line("CAPÍTULO I", Rect(600, 400, 1100, 470))),
                    block(
                        line("Uma noite destas,", Rect(280, 600, 1540, 660)),
                        line("vindo da cidade", Rect(210, 662, 1200, 722)),
                    ),
                ),
            )

        assertThat(text.toBlocks())
            .containsExactly(
                RecognizedBlock(listOf(RecognizedLine("CAPÍTULO I", 600f, 400f, 1100f, 470f))),
                RecognizedBlock(
                    listOf(
                        RecognizedLine("Uma noite destas,", 280f, 600f, 1540f, 660f),
                        RecognizedLine("vindo da cidade", 210f, 662f, 1200f, 722f),
                    ),
                ),
            ).inOrder()
    }

    @Test
    fun `an empty result has no blocks`() {
        assertThat(Text("", emptyList<Text.TextBlock>()).toBlocks()).isEmpty()
    }

    private fun block(vararg lines: Text.Line): Text.TextBlock {
        val box = Rect(lines.minOf { it.boundingBox!!.left }, 0, lines.maxOf { it.boundingBox!!.right }, 1)
        return Text.TextBlock(lines.joinToString("\n") { it.text }, box, noCorners(), LANGUAGE, null, lines.toList())
    }

    private fun line(
        text: String,
        box: Rect,
    ): Text.Line = Text.Line(text, box, noCorners(), LANGUAGE, null, emptyList<Text.Element>(), 0f, 1f)

    private fun noCorners() = emptyList<Point>()

    private companion object {
        const val LANGUAGE = "pt"
    }
}
