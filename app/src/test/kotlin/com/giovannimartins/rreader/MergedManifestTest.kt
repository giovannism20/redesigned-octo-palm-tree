package com.giovannimartins.rreader

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.playback.PlaybackService
import com.giovannimartins.rreader.tts.neural.VoicePackageStore
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

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
    fun `SR-9 the app is out of Android backup, which would stop playback, and keeps its device-transfer rules`() {
        val info = context.applicationInfo

        assertThat(info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
        assertThat(mergedApplicationAttribute("dataExtractionRules")).isEqualTo("@xml/data_extraction_rules")
    }

    @Test
    fun `SR-9 device transfer carries books, positions and settings but not the voice packages`() {
        val rules = dataExtractionRules()

        assertThat(rules.filter { it.section == DEVICE_TRANSFER })
            .containsExactly(
                Rule(DEVICE_TRANSFER, "include", "file", "."),
                Rule(DEVICE_TRANSFER, "exclude", "file", VoicePackageStore.FOLDER),
                Rule(DEVICE_TRANSFER, "include", "database", "."),
                Rule(DEVICE_TRANSFER, "include", "sharedpref", "."),
            )
        assertThat(rules.filter { it.section == CLOUD_BACKUP }.map { it.kind }.toSet()).containsExactly("exclude")
        assertThat(rules.filter { it.section == CLOUD_BACKUP }.map { it.path }.toSet()).containsExactly(".")
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

    /**
     * An attribute of `<application>` in the merged manifest file that the Android Gradle plugin hands to unit
     * tests, for attributes Robolectric does not copy into [ApplicationInfo].
     */
    private fun mergedApplicationAttribute(name: String): String {
        val config = Properties()
        javaClass.classLoader!!.getResourceAsStream(TEST_CONFIG)!!.use(config::load)
        val manifest = File(config.getProperty("android_merged_manifest"))
        val application =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(manifest)
                .getElementsByTagName("application")
                .item(0) as Element
        return application.getAttributeNS(ANDROID_NAMESPACE, name)
    }

    /** One rule of `data_extraction_rules.xml`: its [section], whether it includes or excludes, and what. */
    private data class Rule(
        val section: String,
        val kind: String,
        val domain: String?,
        val path: String?,
    )

    /** The rules of the data extraction rules resource, in order, as the platform reads them. */
    private fun dataExtractionRules(): List<Rule> {
        val rules = mutableListOf<Rule>()
        context.resources.getXml(R.xml.data_extraction_rules).use { parser ->
            var section = ""
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    CLOUD_BACKUP, DEVICE_TRANSFER -> {
                        section = parser.name
                    }

                    "include", "exclude" -> {
                        rules +=
                            Rule(
                                section,
                                parser.name,
                                parser.getAttributeValue(null, "domain"),
                                parser.getAttributeValue(null, "path"),
                            )
                    }
                }
            }
        }
        return rules
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

        const val CLOUD_BACKUP = "cloud-backup"
        const val DEVICE_TRANSFER = "device-transfer"

        /** Written by the Android Gradle plugin for unit tests; holds the merged manifest's path. */
        const val TEST_CONFIG = "com/android/tools/test_config.properties"
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

        /** The permission only the system's job scheduler holds; not in the SDK's `Manifest.permission`. */
        const val BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE"

        const val TRANSPORT_BACKEND_DISCOVERY =
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery"
    }
}
