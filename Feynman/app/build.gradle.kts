import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: your upload key's details live in keystore.properties next to this
// project's settings.gradle.kts (not in git). See README, "Building a release (.aab)".
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }

android {
    // The code's package (it names the generated R class): keep this as it is.
    // To change the app's ID for Google Play, change applicationId below instead.
    namespace = "com.example.feynman"
    compileSdk = 36

    defaultConfig {
        // Google Play refuses IDs starting with com.example: choose your own (e.g. com.yourname.feynman).
        // It can't change once the app is published.
        applicationId = "com.example.feynman"
        minSdk = 26          // variable fonts
        targetSdk = 36
        // Raise versionCode by 1 for every upload to Play; versionName is what people see.
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            if (keystoreFile.exists()) {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with your upload key when keystore.properties exists.
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // Material 3 Expressive (floating toolbars, loading indicator, expressive theme and motion).
    // Stable 1.4.0 removed the expressive API (it lives on in 1.5.0-alpha), and the current 1.5.0
    // alphas need AGP 9.1+ and compileSdk 37. 1.4.0-alpha18 is the last release with the
    // expressive API that builds with AGP 8.10 and compileSdk 36. Pinned over the BOM.
    implementation("androidx.compose.material3:material3:1.4.0-alpha18")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")

    testImplementation("junit:junit:4.13.2")
}
