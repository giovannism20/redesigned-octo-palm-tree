package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.data.settings.SpeakingRatePreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    /** The phone's voices and the offline neural voices (ADR 0002, ADR 0018). */
    @Binds
    abstract fun speechEngine(engine: RoutingSpeechEngine): SpeechEngine

    /** Learned speaking rates are kept in their own preferences file. */
    @Binds
    abstract fun speakingRateStore(store: SpeakingRatePreferences): SpeakingRateStore
}
