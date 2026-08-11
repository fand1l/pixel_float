buildscript {
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
    dependencies {
        // AGP 9 ships built-in Kotlin and would otherwise pick its own bundled KGP/KSP.
        // Per the AGP docs this classpath declaration is the only supported way to raise
        // those versions. The version catalog is not visible inside buildscript {}, so the
        // literals below must stay in sync with gradle/libs.versions.toml.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.11")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
