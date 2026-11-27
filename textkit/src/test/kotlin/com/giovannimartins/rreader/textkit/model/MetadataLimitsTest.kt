package com.giovannimartins.rreader.textkit.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** SR-13: descriptive text from untrusted books and feeds is cut to fixed bounds. */
class MetadataLimitsTest {
    @Test
    fun `keeps values within the limits as they are`() {
        assertThat(MetadataLimits.title("Dom Casmurro")).isEqualTo("Dom Casmurro")
        assertThat(MetadataLimits.author("Machado de Assis")).isEqualTo("Machado de Assis")
        assertThat(MetadataLimits.description("Uma história de ciúme.")).isEqualTo("Uma história de ciúme.")
        val longest = "a".repeat(MetadataLimits.MAX_TITLE_LENGTH)
        assertThat(MetadataLimits.title(longest)).isSameInstanceAs(longest)
    }

    @Test
    fun `cuts longer values and marks the cut with an ellipsis`() {
        val title = MetadataLimits.title("Memórias ".repeat(1_000))
        val description = MetadataLimits.description("Era uma vez. ".repeat(10_000))

        assertThat(title).hasLength(MetadataLimits.MAX_TITLE_LENGTH)
        assertThat(title).endsWith("Me…")
        assertThat(description.length).isAtMost(MetadataLimits.MAX_DESCRIPTION_LENGTH)
        assertThat(description).endsWith("…")
        assertThat(MetadataLimits.author("Autor ".repeat(1_000)).length).isAtMost(MetadataLimits.MAX_AUTHOR_LENGTH)
    }

    @Test
    fun `never splits a character outside the basic plane, and drops spaces before the ellipsis`() {
        val emoji = String(Character.toChars(BOOKS_EMOJI))
        val split = MetadataLimits.title("a".repeat(MetadataLimits.MAX_TITLE_LENGTH - 2) + emoji + "fim")
        val spaced = MetadataLimits.title("a".repeat(MetadataLimits.MAX_TITLE_LENGTH - 3) + "  fim")

        assertThat(split).isEqualTo("a".repeat(MetadataLimits.MAX_TITLE_LENGTH - 2) + "…")
        assertThat(spaced).isEqualTo("a".repeat(MetadataLimits.MAX_TITLE_LENGTH - 3) + "…")
    }

    @Test
    fun `keeps only short language tags`() {
        assertThat(MetadataLimits.language("pt")).isEqualTo("pt")
        assertThat(MetadataLimits.language("pt-BR")).isEqualTo("pt-BR")
        assertThat(MetadataLimits.language("por")).isEqualTo("por")
        listOf("", "Portuguese", "pt BR", "x-none", "pt-${"BR-".repeat(20)}x").forEach {
            assertThat(MetadataLimits.language(it)).isNull()
        }
    }

    private companion object {
        /** A character outside the Basic Multilingual Plane, two UTF-16 units long. */
        const val BOOKS_EMOJI = 0x1F4DA
    }
}
