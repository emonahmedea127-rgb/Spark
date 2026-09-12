package com.example.sociva.data.supabase

import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClientProvider {
  private const val TAG = "SupabaseClientProvider"

  val client: SupabaseClient? by lazy {
    try {
      val url = BuildConfig.SUPABASE_URL.trim()
      val key = BuildConfig.SUPABASE_ANON_KEY.trim()

      val effectiveUrl = if (url.startsWith("http")) url else "https://placeholder.supabase.co"
      val effectiveKey = if (key.isNotBlank()) key else "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummy"

      createSupabaseClient(
        supabaseUrl = effectiveUrl,
        supabaseKey = effectiveKey
      ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize Supabase client: ${e.message}", e)
      null
    }
  }

  fun isConfigured(): Boolean {
    val url = BuildConfig.SUPABASE_URL.trim()
    val key = BuildConfig.SUPABASE_ANON_KEY.trim()
    return url.isNotBlank() &&
      url.startsWith("http") &&
      !url.contains("your-project-ref") &&
      !url.contains("placeholder") &&
      key.isNotBlank() &&
      !key.contains("placeholder")
  }
}
