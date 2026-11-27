import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    // Exported schemas are committed so migrations can be tested.
    schemaDirectory("$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

dependencies {
    implementation(project(":textkit"))

    implementation(libs.androidx.core.ktx)
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

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.media3.test.utils)
    testImplementation(libs.androidx.media3.test.utils.robolectric)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
