plugins {
    alias(libs.plugins.android.application)
    // NOTE: org.jetbrains.kotlin.android is deliberately NOT applied - AGP 9 has
    // built-in Kotlin and applying it fails with "Cannot add extension with name 'kotlin'".
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.fand1l.pixelfloat"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.fand1l.pixelfloat"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-stage1a"
    }

    signingConfigs {
        getByName("debug") {
            // Fixed key committed to the repo. Without it every CI build is signed with a
            // fresh throwaway key, Android refuses the update, and reinstalling wipes the
            // database, the settings AND both permission grants. Never regenerate this file.
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            // No applicationIdSuffix, ever: the notification-listener and accessibility
            // grants are keyed on the ComponentName.
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // With built-in Kotlin, kotlin.compilerOptions.jvmTarget defaults to
    // android.compileOptions.targetCompatibility, so it is not set again here.

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room schema export without the Room Gradle plugin - one less plugin on the
// unproven AGP 9 surface.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
