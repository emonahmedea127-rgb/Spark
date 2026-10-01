package com.example.sociva.call

import com.example.BuildConfig
import com.example.sociva.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SparkCall(
  val id: String,
  val conversationId: String,
  val callerId: String,
  val calleeId: String,
  val video: Boolean,
  val status: String,
  val offer: JSONObject?,
  val answer: JSONObject?
)

data class SparkIce(val id: Long, val candidate: JSONObject)

object CallSignalingApi {
  private val jsonType = "application/json; charset=utf-8".toMediaType()
  private val http = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .writeTimeout(12, TimeUnit.SECONDS)
    .build()

  private fun client() = SupabaseClientProvider.client ?: error("Supabase is not configured")
  private fun baseUrl() = BuildConfig.SUPABASE_URL.trimEnd('/')
  private fun apiKey() = BuildConfig.SUPABASE_ANON_KEY
  private fun token(): String = client().auth.currentSessionOrNull()?.accessToken ?: error("Sign in required")
  fun currentUserId(): String = client().auth.currentUserOrNull()?.id ?: error("Sign in required")

  private fun requestBuilder(url: String): Request.Builder = Request.Builder()
    .url(url)
    .header("apikey", apiKey())
    .header("Authorization", "Bearer ${token()}")
    .header("Content-Type", "application/json")

  private fun parseCall(o: JSONObject) = SparkCall(
    id = o.getString("id"),
    conversationId = o.getString("conversation_id"),
    callerId = o.getString("caller_id"),
    calleeId = o.getString("callee_id"),
    video = o.optBoolean("video", false),
    status = o.optString("status", "ringing"),
    offer = o.optJSONObject("offer"),
    answer = o.optJSONObject("answer")
  )

  suspend fun createOutgoing(conversationId: String, calleeId: String, video: Boolean): SparkCall = withContext(Dispatchers.IO) {
    val body = JSONObject()
      .put("conversation_id", conversationId)
      .put("caller_id", currentUserId())
      .put("callee_id", calleeId)
      .put("video", video)
      .put("status", "ringing")
    val req = requestBuilder("${baseUrl()}/rest/v1/sparknew_calls?select=*")
      .header("Prefer", "return=representation")
      .post(body.toString().toRequestBody(jsonType)).build()
    http.newCall(req).execute().use { r ->
      val text = r.body?.string().orEmpty()
      if (!r.isSuccessful) error("Unable to start call (${r.code})")
      val rows = JSONArray(text)
      if (rows.length() == 0) error("Call was not created")
      parseCall(rows.getJSONObject(0))
    }
  }

  suspend fun findIncomingCall(): SparkCall? = withContext(Dispatchers.IO) {
    val uid = try { currentUserId() } catch (_: Throwable) { return@withContext null }
    val url = "${baseUrl()}/rest/v1/sparknew_calls?callee_id=eq.$uid&status=eq.ringing&order=created_at.desc&limit=1&select=*"
    val req = requestBuilder(url).get().build()
    http.newCall(req).execute().use { r ->
      if (!r.isSuccessful) return@withContext null
      val rows = JSONArray(r.body?.string().orEmpty())
      if (rows.length() == 0) null else parseCall(rows.getJSONObject(0))
    }
  }

  suspend fun getCall(callId: String): SparkCall? = withContext(Dispatchers.IO) {
    val req = requestBuilder("${baseUrl()}/rest/v1/sparknew_calls?id=eq.$callId&select=*").get().build()
    http.newCall(req).execute().use { r ->
      if (!r.isSuccessful) return@withContext null
      val rows = JSONArray(r.body?.string().orEmpty())
      if (rows.length() == 0) null else parseCall(rows.getJSONObject(0))
    }
  }

  suspend fun updateCall(callId: String, values: JSONObject) = withContext(Dispatchers.IO) {
    val req = requestBuilder("${baseUrl()}/rest/v1/sparknew_calls?id=eq.$callId")
      .patch(values.toString().toRequestBody(jsonType)).build()
    http.newCall(req).execute().use { r -> if (!r.isSuccessful) error("Call update failed (${r.code})") }
  }

  suspend fun sendIce(callId: String, candidate: JSONObject) = withContext(Dispatchers.IO) {
    val body = JSONObject().put("call_id", callId).put("user_id", currentUserId()).put("candidate", candidate)
    val req = requestBuilder("${baseUrl()}/rest/v1/sparknew_ice")
      .post(body.toString().toRequestBody(jsonType)).build()
    http.newCall(req).execute().use { r -> if (!r.isSuccessful) error("ICE send failed (${r.code})") }
  }

  suspend fun getRemoteIce(callId: String, afterId: Long): List<SparkIce> = withContext(Dispatchers.IO) {
    val uid = currentUserId()
    val url = "${baseUrl()}/rest/v1/sparknew_ice?call_id=eq.$callId&user_id=neq.$uid&id=gt.$afterId&order=id.asc&select=id,candidate"
    val req = requestBuilder(url).get().build()
    http.newCall(req).execute().use { r ->
      if (!r.isSuccessful) return@withContext emptyList()
      val rows = JSONArray(r.body?.string().orEmpty())
      buildList {
        for (i in 0 until rows.length()) {
          val o = rows.getJSONObject(i)
          add(SparkIce(o.getLong("id"), o.getJSONObject("candidate")))
        }
      }
    }
  }

  suspend fun getIceServers(callId: String): JSONArray = withContext(Dispatchers.IO) {
    val body = JSONObject().put("call_id", callId)
    val req = requestBuilder("${baseUrl()}/functions/v1/spark-turn")
      .post(body.toString().toRequestBody(jsonType)).build()
    http.newCall(req).execute().use { r ->
      val text = r.body?.string().orEmpty()
      if (!r.isSuccessful) error("TURN unavailable (${r.code})")
      JSONObject(text).getJSONArray("iceServers")
    }
  }
}
