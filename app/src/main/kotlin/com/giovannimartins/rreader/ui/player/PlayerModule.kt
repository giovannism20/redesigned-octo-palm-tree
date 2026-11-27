package com.giovannimartins.rreader.ui.player

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    @Binds
    abstract fun playbackControls(connection: PlaybackConnection): PlaybackControls
}
