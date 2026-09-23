plugins {
    id("com.android.application") version "8.10.1"
    id("org.jetbrains.kotlin.android") version "2.1.21"
}

android {
    namespace = "com.airgesture.control"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.airgesture.control"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.10.0-preview"
    }
}

