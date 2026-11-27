package com.giovannimartins.rreader.importer

import android.content.ContentResolver
import android.content.Context
import com.giovannimartins.rreader.textkit.BookFormat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ImporterModule {
    @Provides
    fun contentResolver(
        @ApplicationContext context: Context,
    ): ContentResolver = context.contentResolver

    /** Parser per format; scanned PDF pages are read with on-device text recognition (ADR 0015). */
    @Provides
    fun parsers(
        epub: EpubBookParser,
        pdf: PdfBookParser,
    ): Map<BookFormat, @JvmSuppressWildcards BookParser> =
        mapOf(
            BookFormat.EPUB to epub,
            BookFormat.PDF to pdf,
        )
}
