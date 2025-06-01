import org.gradle.kotlin.dsl.implementation
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization) // YENİ EKLENEN ALIAS
}
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
// API anahtarını local.properties'den al
val mapsApiKeyFromLocalProps = localProperties.getProperty("MAPS_API_KEY", "DEFAULT_KEY_IF_NOT_FOUND_IN_LOCAL_PROPS")

android {
    namespace = "com.accessiblemap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.accessiblemap"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//        manifestPlaceholders["mapsApiKey"] = localProperties.getProperty("MAPS_API_KEY", "DEFAULT_API_KEY_IF_NOT_FOUND")
        manifestPlaceholders["mapsApiKey"] = mapsApiKeyFromLocalProps // Manifest için

        // API anahtarını buildConfig'e eklemek yerine (veya ek olarak) resValue olarak ekle:
        // Bu, R.string.Maps_api_key şeklinde erişilebilen bir string kaynağı oluşturacak.
        resValue("string", "Maps_api_key", mapsApiKeyFromLocalProps)
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation (libs.material)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.places)
    implementation(libs.kotlinx.coroutines.play.services) // En güncel sürümü kontrol edin

    // Ktor Client
    implementation(libs.ktor.client.core) // Örn: "io.ktor:ktor-client-core:2.3.10"
    implementation(libs.ktor.client.android) // Android engine
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Kotlinx Serialization JSON
    implementation(libs.kotlinx.serialization.json)
}