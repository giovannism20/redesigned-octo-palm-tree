package com.giovannimartins.rreader

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks the manifest merged from the app and its libraries against docs/security.md and ADR 0012. */
@RunWith(RobolectricTestRunner::class)
class MergedManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = context.packageManager

    @Test
    fun `SR-5 the app may use the network, for the catalogue, and knows when it is offline`() {
        val permissions =
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions

        assertThat(permissions.orEmpty().toList())
            .containsAtLeast(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE)
    }

    @Test
    fun `long imports run in WorkManager's foreground service as dataSync work`() {
        val service = ComponentName(context, "androidx.work.impl.foreground.SystemForegroundService")
        val permissions =
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions

        assertThat(packageManager.getServiceInfo(service, 0).foregroundServiceType)
            .isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertThat(permissions.orEmpty().toList())
            .containsAtLeast(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC, Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `ML Kit usage logs have no backend that would upload them, although the app has network access`() {
        val discovery = ComponentName(context, TRANSPORT_BACKEND_DISCOVERY)

        val backends = packageManager.getServiceInfo(discovery, PackageManager.GET_META_DATA).metaData

        assertThat(backends?.keySet().orEmpty().filter { it.startsWith("backend:") }).isEmpty()
    }

    private companion object {
        const val TRANSPORT_BACKEND_DISCOVERY =
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery"
    }
}
