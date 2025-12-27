plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.openyourworld"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.openyourworld"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // 🔑 Compose BOM FIRST
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose libs (NO versions)
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Non-Compose deps
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.6")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("androidx.work:work-multiprocess:2.11.0")
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.preference:preference:1.2.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}