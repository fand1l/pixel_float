# Gradle & Build Configuration

Build system patterns following NowInAndroid's convention plugins approach.

## Table of Contents
1. [Project Structure](#project-structure)
2. [Version Catalog](#version-catalog)
3. [Convention Plugins](#convention-plugins)
4. [Module Build Files](#module-build-files)
5. [Build Variants](#build-variants)

## Project Structure

```
project-root/
├── app/
│   └── build.gradle.kts
├── feature/
│   └── */
│       └── build.gradle.kts
├── core/
│   └── */
│       └── build.gradle.kts
├── build-logic/
│   ├── convention/
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/
│   │       └── *.convention.gradle.kts
│   └── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
└── settings.gradle.kts
```

## Version Catalog

`gradle/libs.versions.toml`:

```toml
[versions]
# SDK versions
# API 36 (Android 16) is the newest stable platform and the Play target
# level required for new apps and updates since 31 Aug 2026.
compileSdk = "36"
minSdk = "24"
targetSdk = "36"

# Kotlin & Compose
kotlin = "2.4.10"
kotlinxCoroutines = "1.11.0"
kotlinxSerializationJson = "1.11.0"
kotlinxDatetime = "0.8.0"

# AndroidX
androidxCore = "1.19.0"
androidxLifecycle = "2.11.0"
androidxActivity = "1.13.0"
androidxNavigation = "2.9.8"
androidxComposeBom = "2026.06.01"
androidxHilt = "1.4.0"

# Data
room = "2.8.4"
dataStore = "1.2.1"
protobuf = "4.35.1"

# DI
hilt = "2.60.1"

# Networking
retrofit = "3.0.0"
okhttp = "5.4.0"

# Build
# AGP 9.3.0 requires Gradle 9.5+ and JDK 17.
androidGradlePlugin = "9.3.0"
ksp = "2.3.11"

[libraries]
# Kotlin
kotlin-stdlib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version.ref = "kotlin" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }

# AndroidX Core
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "androidxCore" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "androidxLifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "androidxLifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "androidxActivity" }

# Compose
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "androidxComposeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

# Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "androidxNavigation" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "androidxHilt" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-android-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# DataStore
androidx-datastore = { group = "androidx.datastore", name = "datastore", version.ref = "dataStore" }
protobuf-kotlin-lite = { group = "com.google.protobuf", name = "protobuf-kotlin-lite", version.ref = "protobuf" }

# Networking
retrofit-core = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
# Retrofit 3 ships its own kotlinx.serialization converter - the old
# com.jakewharton.retrofit converter is no longer needed.
retrofit-kotlin-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }

# Testing
junit = { group = "junit", name = "junit", version = "4.13.2" }
androidx-test-ext = { group = "androidx.test.ext", name = "junit-ktx", version = "1.3.0" }
androidx-test-runner = { group = "androidx.test", name = "runner", version = "1.7.0" }
turbine = { group = "app.cash.turbine", name = "turbine", version = "1.2.1" }

# Build plugins (for convention plugins)
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "androidGradlePlugin" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
compose-gradlePlugin = { group = "org.jetbrains.kotlin", name = "compose-compiler-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }

[plugins]
android-application = { id = "com.android.application", version.ref = "androidGradlePlugin" }
android-library = { id = "com.android.library", version.ref = "androidGradlePlugin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
# Since Kotlin 2.0 the Compose compiler ships with Kotlin itself.
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }

# Convention plugins (defined in build-logic)
nowinandroid-android-application = { id = "nowinandroid.android.application", version = "unspecified" }
nowinandroid-android-library = { id = "nowinandroid.android.library", version = "unspecified" }
nowinandroid-android-feature = { id = "nowinandroid.android.feature", version = "unspecified" }
nowinandroid-android-library-compose = { id = "nowinandroid.android.library.compose", version = "unspecified" }
nowinandroid-hilt = { id = "nowinandroid.hilt", version = "unspecified" }
nowinandroid-jvm-library = { id = "nowinandroid.jvm.library", version = "unspecified" }
```

## Convention Plugins

### Setup (`build-logic/convention/build.gradle.kts`)

```kotlin
plugins {
    `kotlin-dsl`
}

group = "com.example.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "nowinandroid.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "nowinandroid.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "nowinandroid.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "nowinandroid.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("hilt") {
            id = "nowinandroid.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "nowinandroid.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
```

### Android Library Plugin

```kotlin
// AndroidLibraryConventionPlugin.kt
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 36
            }
        }
    }
}

// KotlinAndroid.kt (shared configuration)
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = 36

        defaultConfig {
            minSdk = 24
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    // The `kotlinOptions` block is gone in AGP 9 - configure the Kotlin
    // extension directly instead.
    configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}
```

### Compose Plugin

```kotlin
// AndroidLibraryComposeConventionPlugin.kt
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            // Kotlin 2.x: the Compose compiler is a Kotlin plugin, so there is
            // no `composeOptions { kotlinCompilerExtensionVersion = ... }` and
            // no separate compose-compiler version to keep in sync.
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val extension = extensions.getByType<LibraryExtension>()
            configureAndroidCompose(extension)
        }
    }
}

// AndroidCompose.kt
internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        buildFeatures {
            compose = true
        }

        dependencies {
            val bom = libs.findLibrary("androidx-compose-bom").get()
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        }
    }
}
```

### Feature Plugin

```kotlin
// AndroidFeatureConventionPlugin.kt
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("nowinandroid.android.library")
                apply("nowinandroid.android.library.compose")
                apply("nowinandroid.hilt")
            }

            extensions.configure<LibraryExtension> {
                defaultConfig {
                    testInstrumentationRunner = "com.example.core.testing.NiaTestRunner"
                }
            }

            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:designsystem"))
                add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            }
        }
    }
}
```

### Hilt Plugin

```kotlin
// HiltConventionPlugin.kt
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.google.devtools.ksp")
                apply("dagger.hilt.android.plugin")
            }

            dependencies {
                add("implementation", libs.findLibrary("hilt-android").get())
                add("ksp", libs.findLibrary("hilt-android-compiler").get())
            }
        }
    }
}
```

## Module Build Files

### App Module

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.nowinandroid.android.application)
    alias(libs.plugins.nowinandroid.android.application.compose)
    alias(libs.plugins.nowinandroid.hilt)
}

android {
    namespace = "com.example.app"
    
    defaultConfig {
        applicationId = "com.example.app"
        versionCode = 1
        versionName = "1.0"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(projects.feature.foryou.impl)
    implementation(projects.feature.interests.impl)
    implementation(projects.feature.topic.impl)
    implementation(projects.feature.settings.impl)
    
    implementation(projects.core.common)
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.model)
    
    implementation(projects.sync.work)
    
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3.windowSizeClass)
}
```

### Feature Module

```kotlin
// feature/topic/impl/build.gradle.kts
plugins {
    alias(libs.plugins.nowinandroid.android.feature)
}

android {
    namespace = "com.example.feature.topic.impl"
}

dependencies {
    api(projects.feature.topic.api)
    implementation(projects.core.data)
}
```

### Core Module

```kotlin
// core/data/build.gradle.kts
plugins {
    alias(libs.plugins.nowinandroid.android.library)
    alias(libs.plugins.nowinandroid.hilt)
}

android {
    namespace = "com.example.core.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.core.datastore)
    implementation(projects.core.common)
    
    implementation(libs.kotlinx.coroutines.android)
}
```

## Build Variants

### Product Flavors

```kotlin
// app/build.gradle.kts
android {
    flavorDimensions += "contentType"
    
    productFlavors {
        create("demo") {
            dimension = "contentType"
            // Uses local/static data
        }
        create("prod") {
            dimension = "contentType"
            // Uses real network calls
        }
    }
}
```

### Build Types

```kotlin
android {
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
}
```

## Root Settings

`settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyApp"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":feature:foryou:api")
include(":feature:foryou:impl")
include(":feature:topic:api")
include(":feature:topic:impl")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:model")
include(":core:network")
include(":core:testing")
include(":core:ui")
include(":sync:work")
```
