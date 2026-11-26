plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.myvpn"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.myvpn"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "2.0.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
