package com.example.sociva.push

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.sociva.data.supabase.SupabaseClientProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SparkPush {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val jsonType = "application/json; charset=utf-8".toMediaType()
  private val http = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .writeTimeout(12, TimeUnit.SECONDS)
    .build()

  fun initialize(context: Context): Boolean {
    val appId = context.getString(R.string.spark_firebase_app_id)
    val projectId = context.getString(R.string.spark_firebase_project_id)
    val senderId = context.getString(R.string.spark_firebase_sender_id)
    val apiKey = context.getString(R.string.spark_firebase_api_key)
    if (appId.isBlank() || projectId.isBlank() || senderId.isBlank() || apiKey.isBlank()) return false

    return runCatching {
      if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(
          context,
          FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setProjectId(projectId)
            .setGcmSenderId(senderId)
            .setApiKey(apiKey)
            .build()
        )
      }
      FirebaseMessaging.getInstance().isAutoInitEnabled = true
      true
    }.getOrDefault(false)
  }

  fun refresh(context: Context) {
    val appContext = context.applicationContext
    scope.launch { runCatching { register(appContext) } }
  }

  suspend fun register(context: Context) {
    if (!initialize(context)) return
    val client = SupabaseClientProvider.client ?: return
    val session = client.auth.currentSessionOrNull() ?: return
    val uid = client.auth.currentUserOrNull()?.id ?: return
    val token = FirebaseMessaging.getInstance().token.await()
    val body = JSONObject().put("device_token", token)
    val request = Request.Builder()
      .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/rpc/sparknew_register_push")
      .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
      .header("Authorization", "Bearer ${session.accessToken}")
      .header("Content-Type", "application/json")
      .post(body.toString().toRequestBody(jsonType))
      .build()

    http.newCall(request).execute().use { response ->
      if (!response.isSuccessful) error("Push token registration failed (${response.code})")
    }
    context.getSharedPreferences("spark.push", Context.MODE_PRIVATE)
      .edit()
      .putString("token", token)
      .putString("user_id", uid)
      .apply()
  }

  suspend fun unregister(context: Context) {
    if (!initialize(context)) return
    val prefs = context.getSharedPreferences("spark.push", Context.MODE_PRIVATE)
    val token = prefs.getString("token", null)
    val client = SupabaseClientProvider.client
    val session = client?.auth?.currentSessionOrNull()

    if (token != null && session != null) {
      val encoded = java.net.URLEncoder.encode(token, "UTF-8")
      val request = Request.Builder()
        .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/sparknew_push_devices?token=eq.$encoded")
        .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
        .header("Authorization", "Bearer ${session.accessToken}")
        .delete()
        .build()
      runCatching { http.newCall(request).execute().close() }
    }

    prefs.edit().clear().apply()
    runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
  }
}
