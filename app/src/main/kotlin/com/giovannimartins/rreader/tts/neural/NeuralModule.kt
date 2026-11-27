package com.giovannimartins.rreader.tts.neural

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NeuralModule {
    @Binds
    abstract fun voiceDownloads(downloads: WorkManagerVoiceDownloads): VoiceDownloads

    @Binds
    abstract fun runtime(runtime: SherpaOnnxRuntime): NeuralRuntime
}
