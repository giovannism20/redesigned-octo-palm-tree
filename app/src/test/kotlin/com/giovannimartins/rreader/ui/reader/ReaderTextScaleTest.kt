package com.giovannimartins.rreader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderTextScaleTest {
    @Test
    fun `steps up and down through the sizes and stops at both ends`() {
        assertThat(ReaderTextScale.larger(1f)).isEqualTo(1.15f)
        assertThat(ReaderTextScale.smaller(1f)).isEqualTo(0.85f)
        assertThat(ReaderTextScale.larger(2f)).isNull()
        assertThat(ReaderTextScale.smaller(0.85f)).isNull()
    }

    @Test
    fun `a size between steps moves to the neighbouring step`() {
        assertThat(ReaderTextScale.larger(1.2f)).isEqualTo(1.3f)
        assertThat(ReaderTextScale.smaller(1.2f)).isEqualTo(1.15f)
        assertThat(ReaderTextScale.larger(1.1500001f)).isEqualTo(1.3f)
    }
}
