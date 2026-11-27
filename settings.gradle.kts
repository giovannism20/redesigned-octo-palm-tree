pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle provision the JDK declared in gradle/gradle-daemon-jvm.properties.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "r-reader"

include(":app", ":textkit")

// Keep the versioned git hooks (secret guard) active in every clone.
// See docs/runbooks/secrets.md. Skipped outside a git checkout.
if (file(".git").exists()) {
    val hooksPath =
        providers
            .exec {
                commandLine("git", "config", "--get", "core.hooksPath")
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
    if (hooksPath != ".githooks") {
        providers
            .exec { commandLine("git", "config", "core.hooksPath", ".githooks") }
            .result
            .get()
    }
}
