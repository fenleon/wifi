plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lightphone.wifi.server"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // SDK modules come from the included ../light-sdk build (see settings.gradle.kts).
    // sdk:server = LightSdkServer + LightSdkService (the binder). This library
    // ships INSIDE the tool APK, which hosts the service and binds to itself
    // (lighttool.toml serverPackage).
    implementation(libs.sdk.server)
    implementation(libs.kotlinx.coroutines)
    // Unit tests for the pure logic (QR parser, probe classifier); a plain
    // android library module, so the plugin's test-source restrictions don't apply.
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
