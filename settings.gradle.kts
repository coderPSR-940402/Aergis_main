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
        // LiveKit audio-route dependency is published to JitPack, not Maven Central.
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "Aergis"
include(":app")
