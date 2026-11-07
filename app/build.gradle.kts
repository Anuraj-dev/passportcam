plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.raja.passportcam"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.raja.passportcam"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing comes from the environment (CI secrets or a local shell); without it the
    // release APK is simply left unsigned. The keystore never lives in the repo.
    val keystorePath = System.getenv("PASSPORTCAM_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PASSPORTCAM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PASSPORTCAM_KEY_ALIAS") ?: "passportcam"
                keyPassword = System.getenv("PASSPORTCAM_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.face)
    implementation(libs.exifinterface)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    testImplementation(libs.junit)
}
