import java.net.URI
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

    // Production and rollback use their own monotonic version namespace. Signing
    // occurs in a separate job: no private key or password is exposed to Gradle.
    providers.environmentVariable("AERGIS_PRODUCTION_VERSION_CODE").orNull?.let { value ->
        val code = value.toInt()
        require(code in 2_000_000_001..2_100_000_000) { "Invalid production version code" }
        defaultConfig.versionCode = code
        val name = providers.environmentVariable("AERGIS_PRODUCTION_VERSION_NAME").get()
        require(name.matches(Regex("[0-9A-Za-z.+-]+"))) { "Invalid production version name" }
        defaultConfig.versionName = name
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

fun validModel(file: File, size: Long, sha256: String): Boolean {
    if (!file.isFile || file.length() != size) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } == sha256
}

fun downloadModel(url: String, destination: File, size: Long) {
    val connection = URI.create(url).toURL().openConnection().apply {
        connectTimeout = 30_000
        readTimeout = 120_000
    }
    connection.getInputStream().use { input ->
        val advertisedSize = connection.contentLengthLong
        check(advertisedSize < 0 || advertisedSize == size) {
            "Expected $size model bytes, server advertised $advertisedSize"
        }
        destination.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = size
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                check(read >= 0) { "Model download ended before $size bytes" }
                output.write(buffer, 0, read)
                remaining -= read
            }
            check(input.read() < 0) { "Model download exceeded $size bytes" }
        }
    }
}

fun registerPinnedModel(taskName: String, label: String, url: String, asset: String, size: Long, sha256: String) {
    val destination = layout.projectDirectory.file("src/main/assets/$asset").asFile
    val offline = gradle.startParameter.isOffline
    val preparation = tasks.register(taskName) {
        outputs.file(destination)
        outputs.upToDateWhen { validModel(destination, size, sha256) }
        doLast {
            if (!validModel(destination, size, sha256)) {
                check(!offline) { "Missing or invalid pinned $label model at $destination. Restore the verified asset or build online." }
                destination.parentFile.mkdirs()
                val temporary = File.createTempFile("$asset-", ".partial", destination.parentFile)
                logger.lifecycle("Downloading and verifying the pinned MediaPipe $label model")
                try {
                    downloadModel(url, temporary, size)
                    check(validModel(temporary, size, sha256)) { "Model failed size/SHA-256 verification" }
                    Files.move(temporary.toPath(), destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (e: Exception) {
                    throw GradleException("Could not prepare $label model: ${e.message}", e)
                } finally {
                    temporary.delete()
                }
            }
            check(validModel(destination, size, sha256)) { "$label model verification failed; refusing to build" }
        }
    }
    tasks.named("preBuild").configure { dependsOn(preparation) }
}

registerPinnedModel(
    "prepareGestureModel", "gesture",
    "https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task",
    "gesture_recognizer.task", 8_373_440L,
    "97952348cf6a6a4915c2ea1496b4b37ebabc50cbbf80571435643c455f2b0482",
)

// Opt-in gaze lab uses a separate pinned model.
registerPinnedModel(
    "prepareFaceModel", "face",
    "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task",
    "face_landmarker.task", 3_758_596L,
    "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
)

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
    // Native Compose navigation and mapping-draft regression tests (no runtime dependency).
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
