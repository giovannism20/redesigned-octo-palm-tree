package com.giovannimartins.rreader.tts

import com.giovannimartins.rreader.data.settings.SpeakingRatePreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** The phone's own text-to-speech engines, for screens that list or try them apart from the neural voices. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PhoneEngine

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    @Binds
    @PhoneEngine
    abstract fun phoneEngine(engine: SystemTtsEngine): SpeechEngine

    /** The phone's voices and the offline neural voices (ADR 0002, ADR 0018). */
    @Binds
    abstract fun speechEngine(engine: RoutingSpeechEngine): SpeechEngine

    /** Learned speaking rates are kept in their own preferences file. */
    @Binds
    abstract fun speakingRateStore(store: SpeakingRatePreferences): SpeakingRateStore
}
