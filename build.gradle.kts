plugins {
    alias(libs.plugins.android.application) apply false
    // Declaring the Kotlin plugin pins the Kotlin Gradle Plugin version used by
    // AGP's built-in Kotlin support as well as by the JVM module.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:${libs.versions.compose.rules.get()}"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    format("misc") {
        target("**/*.md", "**/*.toml", "**/*.yml", "**/.gitignore", "**/*.properties")
        targetExclude("**/build/**", ".claude/agents/**", "local.properties")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
