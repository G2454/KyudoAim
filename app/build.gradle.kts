plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Screenshot tests on the JVM (Robolectric): ./gradlew recordRoborazziDebug
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.kyudo.aimhelper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kyudo.aimhelper"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // Robolectric needs the merged resources (fonts, themes) for screenshot tests.
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                it.testLogging {
                    events("failed", "skipped")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showStandardStreams = false
                }
            }
        }
    }
    androidResources {
        // MediaPipe memory-maps the model, so it must stay uncompressed in the APK.
        noCompress += listOf("task", "tflite")
    }
}

// ---- MediaPipe pose model --------------------------------------------------
// The model (~9 MB) is downloaded once into src/main/assets on the first build.
val poseModel = file("src/main/assets/pose_landmarker_full.task")
val downloadPoseModel = tasks.register("downloadPoseModel") {
    description = "Downloads the MediaPipe Pose Landmarker model into app assets."
    outputs.file(poseModel)
    onlyIf { !poseModel.exists() }
    doLast {
        poseModel.parentFile.mkdirs()
        val tmp = File(poseModel.parentFile, poseModel.name + ".part")
        val url = java.net.URI(
            "https://storage.googleapis.com/mediapipe-models/pose_landmarker/" +
                "pose_landmarker_full/float16/latest/pose_landmarker_full.task",
        ).toURL()
        url.openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (!tmp.renameTo(poseModel)) {
            tmp.copyTo(poseModel, overwrite = true)
            tmp.delete()
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadPoseModel) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("com.google.mediapipe:tasks-vision:0.10.20")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    val roborazzi = "1.38.0"
    testImplementation("io.github.takahirom.roborazzi:roborazzi:$roborazzi")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:$roborazzi")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:$roborazzi")
}
