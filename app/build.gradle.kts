plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val gestureModelUrl = "https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task"
val gestureModelSha256 = "97952348cf6a6a4915c2ea1496b4b37ebabc50cbbf80571435643c455f2b0482"
val gestureModelFile = layout.projectDirectory.file("src/main/assets/gesture_recognizer.task").asFile

tasks.register("prepareGestureModel") {
    outputs.file(gestureModelFile)
    doLast {
        gestureModelFile.parentFile.mkdirs()
        if (!gestureModelFile.exists() || gestureModelFile.length() == 0L) {
            logger.lifecycle("Downloading the exact MediaPipe gesture model used by the 0.10.0-preview APK")
            java.net.URL(gestureModelUrl).openStream().use { input ->
                gestureModelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        gestureModelFile.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == gestureModelSha256) {
            "gesture_recognizer.task SHA-256 mismatch: expected $gestureModelSha256, got $actual"
        }
        check(gestureModelFile.length() == 8_373_440L) {
            "gesture_recognizer.task size mismatch: expected 8373440 bytes, got ${gestureModelFile.length()}"
        }
    }
}

tasks.named("preBuild").configure { dependsOn("prepareGestureModel") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-service:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")

    val cameraX = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")

    implementation("com.google.mediapipe:tasks-vision:1.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
