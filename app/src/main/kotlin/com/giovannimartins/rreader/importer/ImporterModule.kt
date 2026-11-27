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
     * Parser per format. EPUB and PDF parsers are wired in as they land
     * (roadmap Phases 1 and 2); until then the import fails with a clear reason.
     */
    @Provides
    fun parsers(): Map<BookFormat, @JvmSuppressWildcards BookParser> =
        BookFormat.entries.associateWith {
            BookParser {
                _,
                _,
                ->
                ParseOutcome.Failed(ImportFailure.FORMAT_NOT_YET_SUPPORTED)
            }
        }
}
