plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "fr.neige.stations"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.neige.stations"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.10.0"
    }

    // Clé de signature fixe : permet d'installer chaque nouvelle version par-dessus l'ancienne.
    // Si les variables NEIGE_KEYSTORE_* sont définies (secrets GitHub), elles remplacent la clé du dépôt.
    signingConfigs {
        create("neige") {
            storeFile = file(System.getenv("NEIGE_KEYSTORE_PATH") ?: "neige-debug.keystore")
            storePassword = System.getenv("NEIGE_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("NEIGE_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("NEIGE_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("neige")
        }
        // Version distribuée : non débogable, code optimisé et obscurci par R8.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("neige")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
}
