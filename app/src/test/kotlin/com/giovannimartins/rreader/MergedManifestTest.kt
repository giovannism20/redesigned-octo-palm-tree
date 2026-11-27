package com.giovannimartins.rreader

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.playback.PlaybackService
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

    @Test
    fun `SR-7 only the intake activity and the playback service are exported without a system permission`() {
        val exported = exportedComponents()

        assertThat(exported.filterValues { it == null }.keys - DEBUG_AND_TEST_ONLY)
            .containsExactly(MainActivity::class.java.name, PlaybackService::class.java.name)
        assertThat(exported.filterValues { it != null })
            .containsExactly(
                "androidx.media3.session.BluetoothValidationActivity",
                Manifest.permission.BLUETOOTH_PRIVILEGED,
                "androidx.work.impl.background.systemjob.SystemJobService",
                BIND_JOB_SERVICE,
                "androidx.work.impl.diagnostics.DiagnosticsReceiver",
                Manifest.permission.DUMP,
                "androidx.profileinstaller.ProfileInstallReceiver",
                Manifest.permission.DUMP,
            )
    }

    /** Every exported component of the merged manifest, by class name, with the permission that guards it, if any. */
    private fun exportedComponents(): Map<String, String?> {
        val flags =
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS
        val info = packageManager.getPackageInfo(context.packageName, flags)
        val components: List<ComponentInfo> =
            info.activities.orEmpty().toList() + info.receivers.orEmpty() + info.services.orEmpty() +
                info.providers.orEmpty()
        return components.filter { it.exported }.associate { it.name to it.guardingPermission() }
    }

    private fun ComponentInfo.guardingPermission(): String? =
        when (this) {
            is ActivityInfo -> permission
            is ServiceInfo -> permission
            is ProviderInfo -> readPermission ?: writePermission
            else -> null
        }

    private companion object {
        /**
         * Exported components that only the debug build and the unit tests merge in: Compose's preview and test
         * activities (debugImplementation) and AndroidX Test's activity launcher. The release manifest has none of
         * them.
         */
        val DEBUG_AND_TEST_ONLY =
            setOf(
                "androidx.compose.ui.tooling.PreviewActivity",
                "androidx.activity.ComponentActivity",
                "androidx.test.core.app.InstrumentationActivityInvoker\$BootstrapActivity",
                "androidx.test.core.app.InstrumentationActivityInvoker\$EmptyActivity",
                "androidx.test.core.app.InstrumentationActivityInvoker\$EmptyFloatingActivity",
            )

        /** The permission only the system's job scheduler holds; not in the SDK's `Manifest.permission`. */
        const val BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE"

        const val TRANSPORT_BACKEND_DISCOVERY =
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery"
    }
}
