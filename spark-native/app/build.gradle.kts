import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Firebase config is optional until the owner supplies google-services.json.
val firebaseFile = file("google-services.json")
val firebaseConfig = if (firebaseFile.exists()) JsonSlurper().parse(firebaseFile) as Map<*, *> else emptyMap<Any, Any>()
val firebaseProject = firebaseConfig["project_info"] as? Map<*, *> ?: emptyMap<Any, Any>()
val firebaseClients = firebaseConfig["client"] as? List<*> ?: emptyList<Any>()
val firebaseClient = firebaseClients.mapNotNull { it as? Map<*, *> }.firstOrNull {
    val info = it["client_info"] as? Map<*, *>
    (info?.get("android_client_info") as? Map<*, *>)?.get("package_name") == "com.spark.social"
} ?: emptyMap<Any, Any>()
val firebaseClientInfo = firebaseClient["client_info"] as? Map<*, *> ?: emptyMap<Any, Any>()
val firebaseApiKey = ((firebaseClient["api_key"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("current_key")?.toString().orEmpty()
require(!firebaseFile.exists() || (firebaseClientInfo["mobilesdk_app_id"] != null && firebaseApiKey.isNotBlank())) { "google-services.json must contain com.spark.social" }
android {
    namespace = "com.spark.social"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.spark.social"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "1.21.0"
        resValue("string", "spark_firebase_app_id", firebaseClientInfo["mobilesdk_app_id"]?.toString().orEmpty())
        resValue("string", "spark_firebase_project_id", firebaseProject["project_id"]?.toString().orEmpty())
        resValue("string", "spark_firebase_sender_id", firebaseProject["project_number"]?.toString().orEmpty())
        resValue("string", "spark_firebase_api_key", firebaseApiKey)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // CI restores this key before Gradle starts. Local developers keep the default debug key.
    if (rootProject.file(".signing/debug.keystore").exists()) {
        signingConfigs.getByName("debug") {
            storeFile = rootProject.file(".signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment:1.8.6")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.webkit:webkit:1.13.0")
    testImplementation("junit:junit:4.13.2")
}
