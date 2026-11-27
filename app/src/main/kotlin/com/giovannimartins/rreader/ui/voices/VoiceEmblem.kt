// Emblem shapes are drawn as coordinates in a unit square, one function per shape; naming every number or grouping
// the shapes would not make them clearer.
@file:Suppress("MagicNumber", "TooManyFunctions")

package com.giovannimartins.rreader.ui.voices

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.tts.neural.NeuralVoice
import com.giovannimartins.rreader.ui.theme.Surface
import com.giovannimartins.rreader.ui.theme.VoiceEmblemColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The abstract marks of the voices: a shape of its own for each voice, never a face or a likeness. */
enum class EmblemShape {
    FLOWER,
    WAVES,
    MOUNTAIN,
    MOON,
    DIAMOND,
    RINGS,
    BARS,
    STAR,
    LEAF,
    PHONE,
}

/** How a voice is presented: its emblem ([shape], in [VoiceEmblemColors] number [colorIndex]) and its description. */
data class VoiceLook(
    val shape: EmblemShape,
    val colorIndex: Int,
    @param:StringRes val description: Int,
)

/** The look of every voice of the catalogue, by key, and of the phone's voice. */
object VoiceLooks {
    private const val SEAL = 0
    private const val PAPER = 1
    private const val BLUE = 2
    private const val CORAL = 3
    private const val SAND = 4

    val PHONE = VoiceLook(EmblemShape.PHONE, CORAL, R.string.voices_phone_description)

    private val looks =
        mapOf(
            "dora" to VoiceLook(EmblemShape.FLOWER, SEAL, R.string.voice_description_dora),
            "alex" to VoiceLook(EmblemShape.WAVES, PAPER, R.string.voice_description_alex),
            "cadu" to VoiceLook(EmblemShape.MOUNTAIN, BLUE, R.string.voice_description_cadu),
            "santa" to VoiceLook(EmblemShape.MOON, SAND, R.string.voice_description_santa),
            "faber" to VoiceLook(EmblemShape.DIAMOND, CORAL, R.string.voice_description_faber),
            "dii" to VoiceLook(EmblemShape.RINGS, SEAL, R.string.voice_description_dii),
            "jeff" to VoiceLook(EmblemShape.BARS, PAPER, R.string.voice_description_jeff),
            "miro" to VoiceLook(EmblemShape.STAR, BLUE, R.string.voice_description_miro),
            "edresson" to VoiceLook(EmblemShape.LEAF, SAND, R.string.voice_description_edresson),
        )

    /** The look of [voice]; every voice of the catalogue has its own (checked by tests). */
    fun of(voice: NeuralVoice): VoiceLook = looks.getValue(voice.key)
}

/** A voice's emblem: its shape on a night-blue disc. Decorative: the voice's name says which voice it is. */
@Composable
fun VoiceEmblem(
    look: VoiceLook,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val color = VoiceEmblemColors[look.colorIndex % VoiceEmblemColors.size]
    Canvas(modifier = modifier.size(size).clearAndSetSemantics {}) {
        drawCircle(Surface)
        when (look.shape) {
            EmblemShape.FLOWER -> flower(color)
            EmblemShape.WAVES -> waves(color)
            EmblemShape.MOUNTAIN -> mountain(color)
            EmblemShape.MOON -> moon(color)
            EmblemShape.DIAMOND -> diamond(color)
            EmblemShape.RINGS -> rings(color)
            EmblemShape.BARS -> bars(color)
            EmblemShape.STAR -> star(color)
            EmblemShape.LEAF -> leaf(color)
            EmblemShape.PHONE -> phone(color)
        }
    }
}

// Shapes are drawn in a unit square: (0, 0) is the top left corner of the emblem and (1, 1) the bottom right.

private fun DrawScope.at(
    x: Float,
    y: Float,
) = Offset(x * size.width, y * size.height)

private fun DrawScope.unit(value: Float) = value * size.minDimension

private fun DrawScope.polygon(
    color: Color,
    vararg points: Pair<Float, Float>,
) {
    val path = Path()
    points.forEachIndexed { index, (x, y) ->
        val point = at(x, y)
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.flower(color: Color) {
    repeat(PETALS) { petal ->
        val angle = 2 * PI * petal / PETALS
        drawCircle(color, unit(0.12f), at(0.5f + 0.17f * cos(angle).toFloat(), 0.5f + 0.17f * sin(angle).toFloat()))
    }
    drawCircle(Surface, unit(0.08f), at(0.5f, 0.5f))
}

private fun DrawScope.waves(color: Color) {
    listOf(0.36f to 0.16f, 0.5f to 0.24f, 0.64f to 0.16f).forEach { (y, halfWidth) ->
        val path = Path()
        val start = at(0.5f - halfWidth, y)
        path.moveTo(start.x, start.y)
        val bump = unit(0.06f)
        val step = unit(halfWidth) / 2
        repeat(4) { index ->
            val direction = if (index % 2 == 0) -1 else 1
            path.relativeQuadraticTo(step / 2, direction * bump, step, 0f)
        }
        drawPath(path, color, style = Stroke(width = unit(0.05f), cap = StrokeCap.Round))
    }
}

private fun DrawScope.mountain(color: Color) {
    polygon(color, 0.2f to 0.72f, 0.46f to 0.3f, 0.72f to 0.72f)
    polygon(color, 0.5f to 0.72f, 0.64f to 0.48f, 0.8f to 0.72f)
    drawCircle(color, unit(0.06f), at(0.7f, 0.3f))
}

private fun DrawScope.moon(color: Color) {
    drawCircle(color, unit(0.27f), at(0.5f, 0.5f))
    drawCircle(Surface, unit(0.23f), at(0.62f, 0.42f))
}

private fun DrawScope.diamond(color: Color) {
    polygon(color, 0.5f to 0.2f, 0.8f to 0.5f, 0.5f to 0.8f, 0.2f to 0.5f)
    polygon(Surface, 0.5f to 0.32f, 0.68f to 0.5f, 0.5f to 0.68f, 0.32f to 0.5f)
    polygon(color, 0.5f to 0.42f, 0.58f to 0.5f, 0.5f to 0.58f, 0.42f to 0.5f)
}

private fun DrawScope.rings(color: Color) {
    drawCircle(color, unit(0.27f), at(0.5f, 0.5f), style = Stroke(width = unit(0.05f)))
    drawCircle(color, unit(0.16f), at(0.5f, 0.5f), style = Stroke(width = unit(0.05f)))
    drawCircle(color, unit(0.06f), at(0.5f, 0.5f))
}

private fun DrawScope.bars(color: Color) {
    listOf(0.3f to 0.22f, 0.43f to 0.42f, 0.56f to 0.32f, 0.69f to 0.18f).forEach { (x, height) ->
        drawRoundRect(
            color = color,
            topLeft = at(x - 0.04f, 0.5f - height / 2),
            size = Size(unit(0.08f), unit(height)),
            cornerRadius = CornerRadius(unit(0.04f)),
        )
    }
}

private fun DrawScope.star(color: Color) {
    val points =
        (0 until 2 * STAR_POINTS).map { index ->
            val radius = if (index % 2 == 0) 0.29f else 0.12f
            val angle = PI * index / STAR_POINTS - PI / 2
            (0.5f + radius * cos(angle).toFloat()) to (0.53f + radius * sin(angle).toFloat())
        }
    polygon(color, *points.toTypedArray())
}

private fun DrawScope.leaf(color: Color) {
    val path = Path()
    val tip = at(0.74f, 0.26f)
    val stem = at(0.26f, 0.74f)
    path.moveTo(stem.x, stem.y)
    at(0.22f, 0.32f).let { control -> path.quadraticTo(control.x, control.y, tip.x, tip.y) }
    at(0.68f, 0.78f).let { control -> path.quadraticTo(control.x, control.y, stem.x, stem.y) }
    path.close()
    drawPath(path, color)
    drawLine(Surface, at(0.3f, 0.7f), at(0.64f, 0.36f), strokeWidth = unit(0.035f), cap = StrokeCap.Round)
}

private fun DrawScope.phone(color: Color) {
    drawRoundRect(
        color = color,
        topLeft = at(0.33f, 0.22f),
        size = Size(unit(0.3f), unit(0.56f)),
        cornerRadius = CornerRadius(unit(0.06f)),
        style = Stroke(width = unit(0.05f)),
    )
    drawCircle(color, unit(0.035f), at(0.48f, 0.68f))
    drawArc(
        color = color,
        startAngle = -45f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = at(0.56f, 0.34f),
        size = Size(unit(0.24f), unit(0.24f)),
        style = Stroke(width = unit(0.045f), cap = StrokeCap.Round),
    )
}

private const val PETALS = 6
private const val STAR_POINTS = 5
