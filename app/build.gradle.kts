import java.net.URL
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.airgesture.control"
    compileSdk = 36

    // CI run numbers increase across main and PR builds of the same workflow.
    // Re-running a build keeps its version; local builds use the preview floor.
    val previewBuildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orElse("0").get().toInt()
    require(previewBuildNumber in 0..2_099_999_000) { "Invalid preview build number" }

    signingConfigs {
        create("preview") {
            // Public test identity only. Never use this key for production releases.
            storeFile = rootProject.file("ci/aergis-preview.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.airgesture.control"
        minSdk = 26
        targetSdk = 36
        buildConfigField("String", "SOURCE_COMMIT", "\"${System.getenv("GITHUB_SHA") ?: "local-uncommitted"}\"")
        versionCode = 1000 + previewBuildNumber
        versionName = "0.10.0-preview"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true; buildConfig = true }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("preview")
        }
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

fun validGestureModel(file: File): Boolean {
    if (!file.isFile || file.length() != 8_373_440L) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } == gestureModelSha256
}

tasks.register("prepareGestureModel") {
    outputs.file(gestureModelFile)
    outputs.upToDateWhen { validGestureModel(gestureModelFile) }
    doLast {
        if (!validGestureModel(gestureModelFile)) {
            gestureModelFile.parentFile.mkdirs()
            val temporary = File.createTempFile("gesture-model-", ".partial", gestureModelFile.parentFile)
            logger.lifecycle("Downloading and verifying the pinned MediaPipe gesture model")
            try {
                val connection = URL(gestureModelUrl).openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 120_000
                }
                connection.getInputStream().use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                check(validGestureModel(temporary)) { "Downloaded gesture model failed size/SHA-256 verification" }
                Files.move(temporary.toPath(), gestureModelFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                throw GradleException("Could not prepare gesture model: ${e.message}", e)
            } finally {
                temporary.delete()
            }
        }
        check(validGestureModel(gestureModelFile)) { "Gesture model verification failed; refusing to build" }
    }
}

tasks.named("preBuild").configure { dependsOn("prepareGestureModel") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("androidx.core:core-ktx:1.18.0")

    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")

    implementation("com.google.mediapipe:tasks-vision:1.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
