package com.giovannimartins.rreader.importer

import android.content.ContentResolver
import android.content.Context
import com.giovannimartins.rreader.data.db.ImportFailure
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

    /**
     * Parser per format. The PDF parser arrives in roadmap Phase 2; until then a
     * PDF import fails with a clear "not supported yet" reason.
     */
    @Provides
    fun parsers(epub: EpubBookParser): Map<BookFormat, @JvmSuppressWildcards BookParser> =
        mapOf(
            BookFormat.EPUB to epub,
            BookFormat.PDF to BookParser { _, _ -> ParseOutcome.Failed(ImportFailure.FORMAT_NOT_YET_SUPPORTED) },
        )
}
