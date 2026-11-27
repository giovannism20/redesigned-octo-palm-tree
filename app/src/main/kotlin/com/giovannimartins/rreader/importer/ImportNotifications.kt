package com.giovannimartins.rreader.importer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.giovannimartins.rreader.MainActivity
import com.giovannimartins.rreader.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The notification of an import whose text recognition runs in a `dataSync` foreground service (ADR 0015): silent,
 * low importance, with the page being recognized and a progress bar; tapping it opens the app. It names no book, so
 * nothing private shows on the lock screen. Without the notification permission (Android 13+), the service still
 * runs and the system shows it only in its list of active apps.
 */
class ImportNotifications
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** What the import of [bookId] shows while it reports [progress]. */
        fun foregroundInfo(
            bookId: String,
            progress: ImportProgress,
        ): ForegroundInfo {
            createChannel()
            val builder =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.import_notification_title))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setContentIntent(openApp())
            when (progress) {
                is ImportProgress.RecognizingText -> {
                    builder
                        .setContentText(progressText(context, progress))
                        .setProgress(progress.pageCount, progress.page, false)
                }
            }
            val id = notificationId(bookId)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(id, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(id, builder.build())
            }
        }

        private fun createChannel() {
            val channel =
                NotificationChannelCompat
                    .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.import_notification_channel))
                    .setDescription(context.getString(R.string.import_notification_channel_description))
                    .setShowBadge(false)
                    .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        private fun openApp(): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        companion object {
            const val CHANNEL_ID: String = "book_import"
            private const val REQUEST_OPEN_APP = 1
            private const val FIRST_NOTIFICATION_ID = 10_000
            private const val NOTIFICATION_ID_BITS = 0xFFFF

            /** One notification per book, away from the playback notification's id. */
            fun notificationId(bookId: String): Int =
                FIRST_NOTIFICATION_ID + (bookId.hashCode() and NOTIFICATION_ID_BITS)

            /** "Recognizing text: page X of Y", for the notification and the library. */
            fun progressText(
                context: Context,
                progress: ImportProgress.RecognizingText,
            ): String = context.getString(R.string.import_recognizing_text, progress.page, progress.pageCount)
        }
    }
