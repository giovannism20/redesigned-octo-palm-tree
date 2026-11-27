package com.giovannimartins.rreader.ui.components

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class TimeLeftTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `rounds to the nearest minute and splits hours from minutes`() {
        assertThat(TimeLeft.of(Duration.ZERO)).isNull()
        assertThat(TimeLeft.of((-5).seconds)).isNull()
        assertThat(TimeLeft.of(29.seconds)).isEqualTo(TimeLeft(0, 0))
        assertThat(TimeLeft.of(30.seconds)).isEqualTo(TimeLeft(0, 1))
        assertThat(TimeLeft.of(3.hours + 20.minutes + 10.seconds)).isEqualTo(TimeLeft(3, 20))
        assertThat(TimeLeft.of(59.minutes + 45.seconds)).isEqualTo(TimeLeft(1, 0))
    }

    @Test
    fun `in Portuguese it says how much is left, agreeing with the number`() {
        val pt = resources(Locale.forLanguageTag("pt-BR"))

        assertThat(pt.timeLeft(R.plurals.library_time_left, 3.hours + 20.minutes)).isEqualTo("Faltam 3 h 20 min")
        assertThat(pt.timeLeft(R.plurals.library_time_left, 45.minutes)).isEqualTo("Faltam 45 min")
        assertThat(pt.timeLeft(R.plurals.library_time_left, 2.hours)).isEqualTo("Faltam 2 h")
        assertThat(pt.timeLeft(R.plurals.library_time_left, 1.hours + 5.minutes)).isEqualTo("Falta 1 h 5 min")
        assertThat(pt.timeLeft(R.plurals.library_time_left, 1.minutes)).isEqualTo("Falta 1 min")
        assertThat(pt.timeLeft(R.plurals.library_time_left, 20.seconds)).isEqualTo("Falta menos de 1 min")
        assertThat(pt.timeLeft(R.plurals.player_chapter_time_left, 4.minutes)).isEqualTo("Faltam 4 min neste capítulo")
        assertThat(pt.timeLeft(R.plurals.player_book_time_left, 2.hours)).isEqualTo("Faltam 2 h no livro")
        assertThat(pt.timeLeft(R.plurals.library_time_left, Duration.ZERO)).isNull()
    }

    @Test
    fun `in English it says how much is left`() {
        val en = resources(Locale.US)

        assertThat(en.timeLeft(R.plurals.library_time_left, 3.hours + 20.minutes)).isEqualTo("3 h 20 min left")
        assertThat(en.timeLeft(R.plurals.library_time_left, 1.minutes)).isEqualTo("1 min left")
        assertThat(en.timeLeft(R.plurals.library_time_left, 20.seconds)).isEqualTo("less than 1 min left")
        assertThat(en.timeLeft(R.plurals.player_chapter_time_left, 4.minutes)).isEqualTo("4 min left in this chapter")
        assertThat(en.timeLeft(R.plurals.player_book_time_left, 2.hours)).isEqualTo("2 h left in the book")
    }

    private fun resources(locale: Locale): Resources {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration).resources
    }
}
