plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.mysignmate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mysignmate"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        encoding = "UTF-8"
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures{
        viewBinding = true
        dataBinding = true
    }

}

dependencies {
    // Kotlin lang
    implementation("androidx.core:core-ktx:1.9.0")

    // App compat and UI things
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Navigation library
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")

    // CameraX core library
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")

    // CameraX Camera2 extensions
    implementation("androidx.camera:camera-camera2:$cameraxVersion")

    // CameraX Lifecycle library
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")

    // CameraX View class
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")

    // Unit tests
    testImplementation("junit:junit:4.13.2")

    // Instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // MediaPipe Library
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // Pytorch
    implementation ("org.pytorch:pytorch_android_lite:1.13.1")
    implementation ("org.pytorch:pytorch_android_torchvision_lite:1.13.1")

    // Voice Transcription library using Whisper
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
}