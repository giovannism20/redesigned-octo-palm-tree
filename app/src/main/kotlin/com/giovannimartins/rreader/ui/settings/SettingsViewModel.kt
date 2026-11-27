package com.giovannimartins.rreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giovannimartins.rreader.Diagnostics
import com.giovannimartins.rreader.data.settings.Appearance
import com.giovannimartins.rreader.data.settings.ReadingFont
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.data.settings.TextSize
import com.giovannimartins.rreader.data.settings.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * The listener's appearance choices: the app's root reads them to draw every screen with them, and the Ajustes tab
 * shows and changes them. A change is saved in the settings and reaches every screen at once, without a restart.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
    ) : ViewModel() {
        /**
         * The appearance, or null until the settings are read (the launch screen waits for it, so the first screen
         * already has the listener's theme and text size). Settings that cannot be read give the defaults.
         */
        val appearance: StateFlow<Appearance?> =
            settings.appearance
                .catch { e ->
                    if (e !is IOException) throw e
                    Diagnostics.recordHandled("read the appearance", e)
                    emit(Appearance())
                }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        fun setTextSize(size: TextSize) = save("save the text size") { settings.setTextSize(size) }

        fun setTheme(theme: ThemeChoice) = save("save the theme") { settings.setTheme(theme) }

        fun setReadingFont(font: ReadingFont) = save("save the reading font") { settings.setReadingFont(font) }

        private fun save(
            what: String,
            change: suspend () -> Unit,
        ) {
            viewModelScope.launch {
                try {
                    change()
                } catch (e: IOException) {
                    Diagnostics.recordHandled(what, e)
                }
            }
        }
    }
