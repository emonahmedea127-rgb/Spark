import groovy.json.JsonSlurper

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

val firebaseFile = file("google-services.json")
val firebaseConfig = if (firebaseFile.exists()) JsonSlurper().parse(firebaseFile) as Map<*, *> else emptyMap<Any, Any>()
val firebaseProject = firebaseConfig["project_info"] as? Map<*, *> ?: emptyMap<Any, Any>()
val firebaseClients = firebaseConfig["client"] as? List<*> ?: emptyList<Any>()
val firebaseClient = firebaseClients.mapNotNull { it as? Map<*, *> }.firstOrNull {
  val info = it["client_info"] as? Map<*, *>
  (info?.get("android_client_info") as? Map<*, *>)?.get("package_name") == "com.aistudio.sociva.social"
} ?: emptyMap<Any, Any>()
val firebaseClientInfo = firebaseClient["client_info"] as? Map<*, *> ?: emptyMap<Any, Any>()
val firebaseApiKey = ((firebaseClient["api_key"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("current_key")?.toString().orEmpty()
require(!firebaseFile.exists() || (firebaseClientInfo["mobilesdk_app_id"] != null && firebaseApiKey.isNotBlank())) {
  "google-services.json must contain com.aistudio.sociva.social"
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.sociva.social"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    resValue("string", "spark_firebase_app_id", firebaseClientInfo["mobilesdk_app_id"]?.toString().orEmpty())
    resValue("string", "spark_firebase_project_id", firebaseProject["project_id"]?.toString().orEmpty())
    resValue("string", "spark_firebase_sender_id", firebaseProject["project_number"]?.toString().orEmpty())
    resValue("string", "spark_firebase_api_key", firebaseApiKey)

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

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
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
  implementation("com.google.firebase:firebase-messaging")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
  implementation(libs.androidx.activity.compose)
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
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.auth)
  implementation(libs.supabase.realtime)
  implementation(libs.supabase.storage)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation("io.github.webrtc-sdk:android:150.7871.01")
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
