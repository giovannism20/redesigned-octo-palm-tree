package com.giovannimartins.rreader

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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
    fun `SR-5 the app asks for no network access, which libraries such as ML Kit would add`() {
        val permissions =
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions

        assertThat(permissions.orEmpty().toList()).doesNotContain(Manifest.permission.INTERNET)
    }

    @Test
    fun `ML Kit usage logs have no backend that would upload them`() {
        val discovery = ComponentName(context, TRANSPORT_BACKEND_DISCOVERY)

        val backends = packageManager.getServiceInfo(discovery, PackageManager.GET_META_DATA).metaData

        assertThat(backends?.keySet().orEmpty().filter { it.startsWith("backend:") }).isEmpty()
    }

    private companion object {
        const val TRANSPORT_BACKEND_DISCOVERY =
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery"
    }
}
