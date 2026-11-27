package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.data.settings.SpeakingRatePreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    /** The Android system voices; on-device neural voices join later (ADR 0002). */
    @Binds
    abstract fun speechEngine(engine: SystemTtsEngine): SpeechEngine

    /** Learned speaking rates are kept in their own preferences file. */
    @Binds
    abstract fun speakingRateStore(store: SpeakingRatePreferences): SpeakingRateStore
}
