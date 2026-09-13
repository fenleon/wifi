plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

android {
    compileSdk = 36

    signingConfigs {
        // Workspace dev signing (same key as the SDK tools/emulator).
        create("lightsdkDev") {
            storeFile = file("../../light-sdk/sdk/keys/lightsdk-dev.jks")
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
        }
    }

    defaultConfig {
        minSdk = 34
        targetSdk = 36

        // Consumed by the plugin's generated manifest (SDK_VERSION metadata).
        manifestPlaceholders["sdkVersion"] = property("sdkVersion") as String
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
        getByName("release") {
            // R8 dead-code elimination + resource shrinking (the audiobooks
            // methodology — see APK-SHRINKING.md); the SDK's consumer rules
            // keep the generated registry + entry points.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // zxing-cpp (the scanner's decode engine, pulled in by sdk:ui — WiFi scans
    // QR codes) is published with compileSdk 37 AAR metadata, above this
    // workspace's android-36. The wrapper is a JNI shim over API-1-level
    // types, so the check is advisory (the passes pattern).
    tasks.matching { it.name.endsWith("AarMetadata") }.configureEach { enabled = false }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // SDK modules come from the included ../light-sdk build (see settings.gradle.kts).
    // NO mlkit/camerax exclusions here (unlike tasks): the SDK's
    // LightBarcodeScanner (used by ScanScreen) needs them.
    implementation(libs.sdk.client)
    implementation(libs.kotlinx.coroutines)
    // kotlinx-serialization runtime (mirrors tasks/passes; lightJson comes from
    // sdk-shared if ever needed).
    implementation(libs.kotlinx.serialization.json)
    // The merged :server library (single-module build): its manifest contributes
    // the SDK server wiring (ServerBootstrapProvider) + the portal Activity and
    // the WiFi permissions; its LightSdkService comes from sdk:server, and the
    // tool binds to itself (lighttool.toml serverPackage = own id).
    implementation(project(":server"))
    testImplementation(libs.kotlin.test)
}
