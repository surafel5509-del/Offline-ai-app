plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.studymate.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.studymate.app"
        // minSdk 24 covers ~99% of active devices and includes PdfRenderer + ML Kit support.
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    // Export Room schemas for safe, versioned migrations (output committed to repo).
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Globally strip Google's datatransport telemetry backend (network-oriented, unused
    // offline). text-recognition-bundled-common pulls it in transitively; removing the
    // artifacts also drops the <service>/<receiver> manifest entries ML Kit's transport
    // glue would otherwise add. The bundled OCR recognizer works without it.
    configurations.all {
        exclude(group = "com.google.android.datatransport")
    }

    // --- Compose (BOM keeps versions aligned) ---
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // --- Lifecycle / ViewModel ---
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.0") // ProcessLifecycleOwner for background unload

    // --- Coroutines (play-services Task.await() bridges ML Kit / MediaPipe tasks to suspend) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")

    // --- Room (offline vector store) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Google ML Kit: on-device text recognition (for PDF/image OCR) ---
    // The BUNDLED recognizer model + native lib in text-recognition-bundled-common do the
    // actual OCR and work 100% offline. The datatransport telemetry glue is excluded
    // globally above (see configurations.all).
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // --- MediaPipe: on-device Text Embedder (RAG vectors) + GenAI LLM Inference ---
    implementation("com.google.mediapipe:tasks-text:0.10.14")
    implementation("com.google.mediapipe:tasks-genai:0.10.14")

    // --- Document picker (SAF, no permissions needed) ---
    implementation("androidx.documentfile:documentfile:1.0.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
