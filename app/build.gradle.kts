import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// Release signing comes from environment variables (GitHub secrets in CI) or from a local
// keystore.properties file that is git-ignored. The keystore itself never enters the repository.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String): String? = System.getenv(key) ?: keystoreProps.getProperty(key)

android {
    namespace = "fr.familia.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.familia.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("FAMILIA_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = signingValue("FAMILIA_KEYSTORE_PASSWORD")
                keyAlias = signingValue("FAMILIA_KEY_ALIAS")
                keyPassword = signingValue("FAMILIA_KEY_PASSWORD")
            }
        }
    }

    val hasKeystore = signingValue("FAMILIA_KEYSTORE_PATH") != null
    buildTypes {
        // Test APKs use the same stable key as releases when it is available, so an update installs
        // over the previous one and Google sign-in recognises the app (SHA-1 registered in Firebase).
        debug {
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
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
    testOptions {
        // Robolectric renders Compose screens on the JVM for the screenshot tests.
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
