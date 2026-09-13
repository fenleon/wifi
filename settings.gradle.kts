pluginManagement {
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
        // sdk:ui api-exposes com.github.lightphone:light-keyboard (font);
        // the composite resolves it against the consumer's repositories.
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "wifi"

include(":app")
include(":server")

// Tasks is a single-APK project (the audiobooks/chats pattern): `:app` is the
// real LightOS tool (lighttool.toml + the light-sdk tool plugin, LightScreen
// UI, task repository in-process); `:server` is the merged companion as an
// Android LIBRARY whose manifest contributes the SDK server wiring
// (ServerBootstrapProvider) and whose LightSdkService (from sdk:server) the
// tool binds to — serverPackage = com.lightphone.tasks (self-hosted). Both
// consume the SDK as an included build.
includeBuild("../light-sdk") {
    dependencySubstitution {
        substitute(module("com.thelightphone:sdk-ui")).using(project(":sdk:ui"))
        substitute(module("com.thelightphone:sdk-client")).using(project(":sdk:client"))
        substitute(module("com.thelightphone:sdk-server")).using(project(":sdk:server"))
        substitute(module("com.thelightphone:sdk-shared")).using(project(":sdk:shared"))
    }
}