import java.net.URL
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

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
            try {
                URL(gestureModelUrl).openStream().use { input ->
                    gestureModelFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                logger.warn("Could not download gesture model: ${e.message}")
            }
        }
        if (gestureModelFile.exists() && gestureModelFile.length() > 0L) {
            val digest = MessageDigest.getInstance("SHA-256")
            gestureModelFile.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
            check(actual == gestureModelSha256) {
                "gesture_recognizer.task SHA-256 mismatch: expected $gestureModelSha256, got $actual"
            }
            check(gestureModelFile.length() == 8_373_440L) {
                "gesture_recognizer.task size mismatch: expected 8373440 bytes, got ${gestureModelFile.length()}"
            }
        } else {
            logger.warn("Gesture model asset missing or empty; build will continue.")
        }
    }
}

tasks.named("preBuild").configure { dependsOn("prepareGestureModel") }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)

    implementation(libs.mediapipe.tasks.vision)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
