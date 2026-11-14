import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// google-services.json comes from the Firebase console. Until it is added, the app still builds
// (CI stays green) but cannot reach Firebase; the sign-in screen then says so.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
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
        // Two stable keys: the release key signs only the APKs given to the family; the test key
        // signs CI test builds, so a test APK can never replace the family's installed app.
        create("release") {
            signingValue("FAMILIA_KEYSTORE_PATH")?.let { path ->
                storeFile = file(path)
                storePassword = signingValue("FAMILIA_KEYSTORE_PASSWORD")
                keyAlias = signingValue("FAMILIA_KEY_ALIAS")
                keyPassword = signingValue("FAMILIA_KEY_PASSWORD")
            }
        }
        create("test") {
            signingValue("FAMILIA_TEST_KEYSTORE_PATH")?.let { path ->
                storeFile = file(path)
                storePassword = signingValue("FAMILIA_TEST_KEYSTORE_PASSWORD")
                keyAlias = signingValue("FAMILIA_TEST_KEY_ALIAS")
                keyPassword = signingValue("FAMILIA_TEST_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Without the test key (e.g. on a fresh machine) debug builds fall back to the local debug key.
        debug {
            applicationIdSuffix = ".test"
            if (signingValue("FAMILIA_TEST_KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("test")
        }
        release {
            isMinifyEnabled = false
            if (signingValue("FAMILIA_KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
