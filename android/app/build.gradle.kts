import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Official builds read the release key from the environment. The keystore and its password
// never live in this repository. Without these variables a local build falls back to the debug
// key, which is fine for development but cannot update an official install.
val releaseKeystore = System.getenv("DREAMINSTALL_KEYSTORE")
val releaseStorePassword = System.getenv("DREAMINSTALL_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("DREAMINSTALL_KEY_ALIAS") ?: "dreaminstall"
val releaseKeyPassword = System.getenv("DREAMINSTALL_KEY_PASSWORD") ?: releaseStorePassword ?: ""

android {
    namespace = "dev.oni.dreaminstall"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.oni.dreaminstall"
        minSdk = 30
        targetSdk = 36
        versionCode = 12
        versionName = "1.4.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!releaseKeystore.isNullOrBlank() && !releaseStorePassword.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Official releases use the release key; contributors without it get the debug key.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // the native binary and the 3DS assets must stay byte-identical
        noCompress += listOf("3dsx", "gz", "bin", "ttf")
        localeFilters += listOf("en", "pt")
    }

    packaging {
        jniLibs {
            // extractNativeLibs: the app execs the save3ds binary from nativeLibraryDir
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
