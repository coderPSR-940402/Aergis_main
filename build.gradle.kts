plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}

buildscript {
    dependencies {
        // AGP 9 provides built-in Kotlin; keep the Compose compiler aligned
        // with the project's explicitly selected Kotlin release.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}
