package com.giovannimartins.rreader.catalog

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogModule {
    @Binds
    abstract fun transport(transport: UrlConnectionTransport): HttpTransport
}
