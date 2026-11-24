import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.filedude.lmsxhy"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        val preCachedSdkDir = file("/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7")
        if (preCachedSdkDir.exists()) {
          it.systemProperty("robolectric.offline", "true")
          it.systemProperty("robolectric.dependency.dir", preCachedSdkDir.absolutePath)
        }
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("KEYSTORE_PATH")
  ignoreList.add("STORE_PASSWORD")
  ignoreList.add("KEY_PASSWORD")
  ignoreList.add("RELEASE_KEYSTORE_BASE64")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.window.size)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.exifinterface)
  implementation(libs.coil.compose)

  // Firebase Auth (Google Sign-In via Credential Manager), Firestore, AI Logic & App Check
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)

  // FileDude On-Device Engines: PDFBox, Media3, ML Kit OCR, Play Billing
  implementation(libs.pdfbox.android)
  implementation(libs.androidx.media3.transformer)
  implementation(libs.androidx.media3.effect)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.play.billing.ktx)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}

val verifyReleaseSigningVars = tasks.register("verifyReleaseSigningVars") {
  group = "verification"
  description = "Verifies that KEYSTORE_PATH (or my-upload-key.jks), STORE_PASSWORD, and KEY_PASSWORD are defined for release signing."

  val defaultKeystore = rootProject.layout.projectDirectory.file("my-upload-key.jks").asFile
  val envKeystorePath = providers.environmentVariable("KEYSTORE_PATH")
  val envStorePassword = providers.environmentVariable("STORE_PASSWORD")
  val envKeyPassword = providers.environmentVariable("KEY_PASSWORD")

  doLast {
    val configuredPath = envKeystorePath.orNull?.trim().orEmpty()
    val keystoreFile = if (configuredPath.isNotEmpty()) File(configuredPath) else defaultKeystore
    val missing = mutableListOf<String>()

    if (!keystoreFile.exists()) {
      missing += "Release keystore not found at '${keystoreFile.absolutePath}' (set KEYSTORE_PATH or place my-upload-key.jks at project root with keyAlias 'upload')."
    }
    if (envStorePassword.orNull.isNullOrBlank()) {
      missing += "STORE_PASSWORD environment variable is not defined."
    }
    if (envKeyPassword.orNull.isNullOrBlank()) {
      missing += "KEY_PASSWORD environment variable is not defined."
    }

    if (missing.isNotEmpty()) {
      throw GradleException(
        "Release signing variables are incomplete:\n - " +
          missing.joinToString("\n - ") +
          "\n\nGenerate an upload keystore (keyAlias = \"upload\") once:\n" +
          "  keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload\n" +
          "Then set KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD in AI Studio Secrets, GitHub Secrets, or your shell environment."
      )
    }
  }
}

tasks.matching { it.name == "validateSigningRelease" }.configureEach {
  mustRunAfter(verifyReleaseSigningVars)
}

tasks.register("generateSignedReleaseApk") {
  group = "build"
  description = "Verifies release signing build variables and generates a signed release APK at app/build/outputs/apk/release/app-release.apk."
  dependsOn(verifyReleaseSigningVars, "assembleRelease")

  val releaseApkProvider = layout.buildDirectory.file("outputs/apk/release/app-release.apk")

  doLast {
    val apkFile = releaseApkProvider.get().asFile
    if (!apkFile.exists()) {
      throw GradleException("Expected signed release APK was not found at ${apkFile.absolutePath}")
    }
    logger.lifecycle("Signed release APK ready: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
  }
}
