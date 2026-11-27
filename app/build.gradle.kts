import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.detekt)
}

/**
 * Build-time keys: every key declared in secrets.defaults.properties (committed,
 * placeholders only) becomes a BuildConfig String field whose value comes from
 * the git-ignored secrets.properties when present. See docs/runbooks/secrets.md.
 */
fun loadProperties(file: File): Properties =
    Properties().apply {
        if (file.exists()) file.inputStream().use(::load)
    }

val secretDefaults = loadProperties(rootProject.file("secrets.defaults.properties"))
val secretValues = loadProperties(rootProject.file("secrets.properties"))

/** Release signing comes from ~/.gradle/gradle.properties, never from the repo. */
val signingStoreFile = providers.gradleProperty("rreader.signing.storeFile")

android {
    namespace = "com.giovannimartins.rreader"
    // AndroidX requires compiling against API 37.1+. targetSdk 37: the primary
    // user's phone runs Android 17 (ADR 0014).
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 2
            }
    }

    defaultConfig {
        applicationId = "com.giovanni_martins.rreader"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        secretDefaults.stringPropertyNames().sorted().forEach { key ->
            val value = secretValues.getProperty(key) ?: secretDefaults.getProperty(key).orEmpty()
            buildConfigField("String", key, "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
    }

    signingConfigs {
        if (signingStoreFile.isPresent) {
            create("release") {
                storeFile = file(signingStoreFile.get())
                storePassword = providers.gradleProperty("rreader.signing.storePassword").get()
                keyAlias = providers.gradleProperty("rreader.signing.keyAlias").get()
                keyPassword = providers.gradleProperty("rreader.signing.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingStoreFile.isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
        error += "MissingTranslation"
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    // One APK per CPU architecture: ML Kit ships native code, so a universal APK is
    // about 52 MB. Phones get arm64-v8a; x86_64 is for the emulator.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    androidResources {
        // aapt's default patterns, plus the espeak-ng data of piper-phonemize for every language (19 MB): the app
        // ships only the Portuguese part and the dictionaries it switches to, in assets/espeak-pt (app/libs/README.md).
        ignoreAssetsPattern =
            "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:!<dir>espeak-ng-data"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    // Exported schemas are committed so migrations can be tested.
    schemaDirectory("$projectDir/schemas")
}

/**
 * Libraries published only as files on their GitHub releases (app/libs/README.md). They are not kept in git: the
 * build downloads each one from its release into app/libs (git-ignored) when it is missing, and refuses any file
 * whose SHA-256 differs from app/libs/SHA256SUMS.
 */
val vendoredLibraryUrls =
    mapOf(
        "sherpa-onnx-static-link-onnxruntime-2.0.3.aar" to
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3",
        "piper-phonemize-1.6.1.aar" to "https://github.com/csukuangfj/piper-phonemize/releases/download/v1.6.1",
    ).mapValues { (name, release) -> "$release/$name" }

val fetchVendoredLibraries by tasks.registering {
    description = "Downloads the AARs listed in app/libs/SHA256SUMS from their releases and verifies their SHA-256."
    val libs = layout.projectDirectory.dir("libs").asFile
    val sums = layout.projectDirectory.file("libs/SHA256SUMS").asFile
    val urls = vendoredLibraryUrls
    inputs.file(sums)
    inputs.property("urls", urls)
    outputs.files(urls.keys.map { File(libs, it) })
    doLast {
        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        val expected =
            sums.readLines().filter(String::isNotBlank).associate { line ->
                val (sha256, name) = line.trim().split(Regex("\\s+\\*?"), limit = 2)
                name to sha256.lowercase()
            }
        check(expected.keys == urls.keys) {
            "app/libs/SHA256SUMS and vendoredLibraryUrls in app/build.gradle.kts must name the same files"
        }
        expected.forEach { (name, sha256) ->
            val file = File(libs, name)
            if (file.isFile && sha256Of(file) == sha256) return@forEach
            val url = urls.getValue(name)
            val partial = File(libs, "$name.part")
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
            val actual = sha256Of(partial)
            if (actual != sha256) {
                partial.delete()
                error("$name from $url has SHA-256 $actual, but app/libs/SHA256SUMS expects $sha256")
            }
            Files.move(partial.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

tasks.named("preBuild") { dependsOn(fetchVendoredLibraries) }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

dependencies {
    implementation(project(":textkit"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.serialization.json)
    // Offline neural voices (ADR 0018): downloaded by the build with pinned SHA-256 values, see app/libs/README.md.
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-2.0.3.aar").builtBy(fetchVendoredLibraries))
    implementation(files("libs/piper-phonemize-1.6.1.aar").builtBy(fetchVendoredLibraries))
    implementation(libs.commons.compress)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.media3.test.utils)
    testImplementation(libs.androidx.media3.test.utils.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.compose.ui.test.manifest)
}
