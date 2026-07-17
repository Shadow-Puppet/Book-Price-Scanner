plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.bookscanprice"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bookscanprice"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // OPTIONAL: paste an eBay OAuth app token here later to enable live eBay prices.
        buildConfigField("String", "EBAY_APP_TOKEN", "\"\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // CameraX
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // ML Kit barcode scanning (on-device, free)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // org.json is built into Android
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
