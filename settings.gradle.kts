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
        maven(url = "https://jitpack.io") { content { includeGroup("com.github.davidliu") } }
    }
}

rootProject.name = "Aergis"
include(":app")
