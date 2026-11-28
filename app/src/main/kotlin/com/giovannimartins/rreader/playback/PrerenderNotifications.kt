package com.giovannimartins.rreader.playback

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
import kotlin.math.roundToInt

/**
 * The notification of the job that renders a book ahead of listening in a `dataSync` foreground service (ADR 0024):
 * silent, low importance, with how much of the hour ahead is ready; tapping it opens the app. It names no book, so
 * nothing private shows on the lock screen. Without the notification permission (Android 13+), the service still
 * runs and the system shows it only in its list of active apps.
 */
class PrerenderNotifications
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** What the job shows when [fraction] (0 to 1) of the audio it renders ahead is ready. */
        fun foregroundInfo(fraction: Float): ForegroundInfo {
            createChannel()
            val percent = (fraction.coerceIn(0f, 1f) * PERCENT).roundToInt()
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.prerender_notification_title))
                    .setContentText(context.getString(R.string.prerender_notification_text))
                    .setProgress(PERCENT, percent, false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setContentIntent(openApp())
                    .build()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(NOTIFICATION_ID, notification)
            }
        }

        private fun createChannel() {
            val channel =
                NotificationChannelCompat
                    .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.prerender_notification_channel))
                    .setDescription(context.getString(R.string.prerender_notification_channel_description))
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
            const val CHANNEL_ID: String = "prerender"
            private const val REQUEST_OPEN_APP = 2
            private const val PERCENT = 100

            /** One job at a time, away from the playback and import notifications' ids. */
            private const val NOTIFICATION_ID = 9_000
        }
    }
