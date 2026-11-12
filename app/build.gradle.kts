

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.example"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "com.aistudio.unitask.zrvqk"
    minSdk = 24
    targetSdk = 36
    versionCode = 22
    versionName = "4.2.0"
    buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"532041682564-hm7328k6empdkip9ldoml916klqrdcjb.apps.googleusercontent.com\"")
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
      // Use the project's debug.keystore if present, otherwise Android's standard debug key.
      val projectKeystore = file("${rootDir}/debug.keystore")
      storeFile = if (projectKeystore.exists()) projectKeystore
                  else file("${System.getProperty("user.home")}/.android/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
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
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  
  lint {
    // Fail the build on real errors, but never on warnings.
    abortOnError = true
    warningsAsErrors = false
    // Include the "why" text for each rule in the reports.
    explainIssues = true
    // Reports read by .github/scripts/lint_report.py and attached to failed CI runs.
    xmlReport = true
    htmlReport = true
    sarifReport = true
    textReport = false
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.jvmArgs(
          "--add-opens=java.base/java.lang=ALL-UNNAMED",
          "--add-opens=java.base/java.util=ALL-UNNAMED",
          "--add-opens=java.base/java.io=ALL-UNNAMED",
          "--add-opens=java.base/java.net=ALL-UNNAMED",
          "--add-opens=java.base/java.security=ALL-UNNAMED",
          "--add-opens=java.base/java.text=ALL-UNNAMED",
          "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
          "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
        )
        it.testLogging {
          events("failed")
          exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
          showStackTraces = true
        }
      }
    }
  }
  sourceSets {
    getByName("test").assets.directories.add("$projectDir/schemas")
    getByName("debug").assets.directories.add("$projectDir/schemas")
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// SUPABASE_URL and SUPABASE_ANON_KEY come from this plugin: it reads the git-ignored .env file
// (or the placeholders in .env.example when there is no .env) and turns each line into a
// BuildConfig field. See the README for how to create .env.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}


dependencies {
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(platform(libs.androidx.compose.bom))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.foundation.layout)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)

  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.play.services.auth)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.glance.appwidget)
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.auth)
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.realtime)
  implementation(libs.ktor.client.okhttp)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.zxing.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.ktor.client.mock)
  testImplementation(libs.org.json)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.foundation.layout)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.core)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.room.testing)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
