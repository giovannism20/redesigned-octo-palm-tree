package com.giovannimartins.rreader.pdf

import com.giovannimartins.rreader.data.files.BookFileStore
import com.giovannimartins.rreader.textkit.layout.LayoutLine
import com.giovannimartins.rreader.textkit.layout.PageLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
class OcrPageStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val files by lazy { BookFileStore(folder.root) }
    private val store by lazy { OcrPageStore(files) }
    private val page =
        PageLayout(
            index = 4,
            width = 420f,
            height = 595f,
            lines =
                listOf(
                    LayoutLine("CAPÍTULO IV", 150f, 100f, 270f, 118f, 25.7f),
                    LayoutLine("Ação, coração e \"aspas\".", 50f, 140f, 370f, 152f, 12f),
                ),
        )

    @Test
    fun `gives back a stored page and the time it took`() {
        store.write(BOOK_ID, page, 850.milliseconds)

        assertThat(store.read(BOOK_ID, 4)).isEqualTo(StoredOcrPage(page, 850.milliseconds))
        assertThat(files.ocrPageFile(BOOK_ID, 4).isFile).isTrue()
    }

    @Test
    fun `knows no page that was not stored, or that belongs to another book`() {
        store.write(BOOK_ID, page, 1.milliseconds)

        assertThat(store.read(BOOK_ID, 5)).isNull()
        assertThat(store.read(OTHER_BOOK_ID, 4)).isNull()
    }

    @Test
    fun `ignores page files it cannot trust, so their pages are recognized again`() {
        val file = files.ocrPageFile(BOOK_ID, 4)
        file.parentFile!!.mkdirs()
        val valid = """{"version":1,"index":4,"width":420.0,"height":595.0,"elapsedMillis":5,"lines":[]}"""

        listOf(
            "{ not json",
            valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"index\":4", "\"index\":3"),
            valid.replace("\"elapsedMillis\":5", "\"elapsedMillis\":-5"),
            valid.replace(
                "\"lines\":[]",
                """"lines":[{"text":"x","left":9.0,"top":0.0,"right":1.0,"bottom":1.0,"fontSize":1.0}]""",
            ),
            " ".repeat(5 * 1024 * 1024),
        ).forEach { content ->
            file.writeText(content)
            assertThat(store.read(BOOK_ID, 4)).isNull()
        }
        file.writeText(valid)
        assertThat(store.read(BOOK_ID, 4))
            .isEqualTo(StoredOcrPage(PageLayout(4, 420f, 595f, emptyList()), 5.milliseconds))
    }

    @Test
    fun `the pages of a book can be deleted together, keeping its other files`() {
        store.write(BOOK_ID, page, 1.milliseconds)
        store.write(BOOK_ID, page.copy(index = 5), 1.milliseconds)
        files.coverFile(BOOK_ID).writeText("capa")

        files.deleteOcrPages(BOOK_ID)

        assertThat(store.read(BOOK_ID, 4)).isNull()
        assertThat(store.read(BOOK_ID, 5)).isNull()
        assertThat(files.coverFile(BOOK_ID).isFile).isTrue()
    }

    private companion object {
        const val BOOK_ID = "00000000-0000-4000-8000-000000000001"
        const val OTHER_BOOK_ID = "00000000-0000-4000-8000-000000000002"
    }
}
