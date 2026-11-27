package com.giovannimartins.rreader.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File

/**
 * Builds PDFs whose A5 pages are pictures of text drawn at 300 dpi, as a scanner makes them: no text layer at all.
 * Lines are drawn in a 12-point serif font from x = [LEFT] points, the first baseline at [FIRST_BASELINE] points.
 */
internal object ScannedPdf {
    const val PAGE_WIDTH = 420f
    const val PAGE_HEIGHT = 595f
    const val LEFT = 50f
    const val FIRST_BASELINE = 90f
    const val BODY_SIZE = 12f
    private const val LEADING = 18f
    private const val SCALE = 300f / 72f

    val PARAGRAPH =
        listOf(
            "Uma noite destas, vindo da cidade para o",
            "Engenho Novo, encontrei no trem um rapaz",
            "aqui do bairro, que eu conheço de vista.",
        )

    /** Writes a document with one page per entry of [pages], each the lines drawn on it, to [file]. */
    fun write(
        file: File,
        pages: List<List<String>>,
    ): File {
        PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        PDDocument().use { document ->
            pages.forEach { lines ->
                val page = PDPage(PDRectangle.A5)
                document.addPage(page)
                val picture = picture(lines)
                val image = LosslessFactory.createFromImage(document, picture)
                picture.recycle()
                PDPageContentStream(document, page).use { stream ->
                    stream.drawImage(image, 0f, 0f, PAGE_WIDTH, PAGE_HEIGHT)
                }
            }
            document.save(file)
        }
        return file
    }

    private fun picture(lines: List<String>): Bitmap {
        val bitmap = createBitmap((PAGE_WIDTH * SCALE).toInt(), (PAGE_HEIGHT * SCALE).toInt())
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = BODY_SIZE * SCALE
                typeface = Typeface.SERIF
            }
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, LEFT * SCALE, (FIRST_BASELINE + index * LEADING) * SCALE, paint)
        }
        return bitmap
    }
}
