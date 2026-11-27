package com.giovannimartins.rreader.net

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NetModule {
    @Binds
    abstract fun transport(transport: UrlConnectionTransport): HttpTransport
}
