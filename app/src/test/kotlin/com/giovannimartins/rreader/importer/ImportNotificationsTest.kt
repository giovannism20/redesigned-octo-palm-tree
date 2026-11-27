package com.giovannimartins.rreader.importer

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ImportNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = ImportNotifications(context)

    @Test
    fun `shows the page being recognized in a silent, ongoing progress notification`() {
        val info = notifications.foregroundInfo(BOOK_ID, ImportProgress.RecognizingText(12, 300))

        val notification = info.notification
        assertThat(info.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertThat(notification.channelId).isEqualTo(ImportNotifications.CHANNEL_ID)
        assertThat(notification.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Preparing a book")
        assertThat(notification.extras.getString(Notification.EXTRA_TEXT)).isEqualTo("Recognizing text: page 12 of 300")
        assertThat(notification.extras.getInt(Notification.EXTRA_PROGRESS)).isEqualTo(12)
        assertThat(notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX)).isEqualTo(300)
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.contentIntent).isNotNull()
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(info.channelId())
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun `speaks Portuguese`() {
        val info = notifications.foregroundInfo(BOOK_ID, ImportProgress.RecognizingText(3, 10))

        assertThat(info.notification.extras.getString(Notification.EXTRA_TEXT))
            .isEqualTo("Reconhecendo texto: página 3 de 10")
    }

    @Test
    fun `each book has its own notification, apart from the playback one`() {
        val first = ImportNotifications.notificationId(BOOK_ID)
        val second = ImportNotifications.notificationId("00000000-0000-4000-8000-000000000002")

        assertThat(first).isNotEqualTo(second)
        assertThat(listOf(first, second).all { it >= 10_000 }).isTrue()
    }

    private fun androidx.work.ForegroundInfo.channelId(): String = notification.channelId

    private companion object {
        const val BOOK_ID = "00000000-0000-4000-8000-000000000001"
    }
}
