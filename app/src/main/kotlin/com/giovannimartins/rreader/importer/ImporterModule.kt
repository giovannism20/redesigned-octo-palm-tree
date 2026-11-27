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

    /** Parser per format. PDFs need a text layer; scanned ones are explained until OCR arrives (Phase 4). */
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
