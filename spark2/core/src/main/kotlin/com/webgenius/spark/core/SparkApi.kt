package com.webgenius.spark.core

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SparkApi(
    val config: Config,
    private val store: SecretStore,
    private val http: OkHttpClient = OkHttpClient.Builder().followRedirects(false)
        .followSslRedirects(false).retryOnConnectionFailure(false)
        .callTimeout(45, TimeUnit.SECONDS).build(),
    private val clock: () -> Long = { Instant.now().epochSecond }
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val refreshLock = Mutex()
    private val _session = MutableStateFlow(runCatching {
        store.read("session")?.let { json.decodeFromString<Session>(it) }
    }.getOrNull())
    val session = _session.asStateFlow()
    val userId: String get() = session.value?.userId ?: error("Please sign in again.")
    private fun save(value: Session?) { store.write("session", value?.let { json.encodeToString(it) }); _session.value = value }
    private fun accept(value: JsonElement): Session {
        val o = value.jsonObject
        return Session(o.getValue("access_token").jsonPrimitive.content,
            o.getValue("refresh_token").jsonPrimitive.content,
            o["expires_at"]?.jsonPrimitive?.longOrNull ?: (clock() + (o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600)),
            o.getValue("user").jsonObject.getValue("id").jsonPrimitive.content).also(::save)
    }
    suspend fun accessToken(): String = refreshLock.withLock {
        val current = session.value ?: error("Please sign in again.")
        if (current.expiresAt > clock() + 90) current.accessToken else refresh(current).accessToken
    }
    private suspend fun refresh(current: Session): Session = try {
        accept(raw("POST", "auth/v1/token", obj("refresh_token" to current.refreshToken),
            mapOf("grant_type" to "refresh_token")))
    } catch (e: ApiException) { if (e.status == 400 || e.status == 401) save(null); throw e }

    private suspend fun request(method: String, path: String, body: JsonElement? = null,
        query: Map<String, String> = emptyMap(), prefer: String? = null): JsonElement {
        val token = accessToken()
        return try { raw(method, path, body, query, token, prefer) }
        catch (e: ApiException) {
            if (e.status != 401) throw e
            val renewed = refreshLock.withLock {
                val current = session.value ?: throw e
                if (current.accessToken != token) current.accessToken else refresh(current).accessToken
            }
            raw(method, path, body, query, renewed, prefer)
        }
    }
    private suspend fun raw(method: String, path: String, body: JsonElement? = null,
        query: Map<String, String> = emptyMap(), token: String? = null, prefer: String? = null,
        bytes: ByteArray? = null): JsonElement {
        val url = (config.url.trimEnd('/') + "/" + path).toHttpUrl().newBuilder()
        query.forEach { (key, value) -> url.addQueryParameter(key, value) }
        val payload = if (bytes != null) bytes.toRequestBody("image/jpeg".toMediaType())
            else body?.toString()?.toRequestBody("application/json".toMediaType())
            ?: if (method in listOf("POST", "PUT", "PATCH")) "{}".toRequestBody("application/json".toMediaType()) else null
        val builder = Request.Builder().url(url.build()).method(method, payload)
            .header("apikey", config.publishableKey).header("Accept", "application/json")
        token?.let { builder.header("Authorization", "Bearer $it") }
        prefer?.let { builder.header("Prefer", it) }
        if (bytes != null) builder.header("x-upsert", "false").header("Cache-Control", "no-store")
        val response = http.newCall(builder.build()).await()
        response.use {
            val text = it.body?.string().orEmpty()
            val result = runCatching { json.parseToJsonElement(text) }.getOrElse { JsonNull }
            if (!it.isSuccessful) {
                val o = result as? JsonObject
                val code = o?.get("code")?.jsonPrimitive?.contentOrNull ?: o?.get("error_code")?.jsonPrimitive?.contentOrNull ?: "http_${it.code}"
                val message = when {
                    code == "23505" -> "That username or item already exists. Please refresh and try again."
                    code == "42501" || it.code == 403 -> "You do not have permission for this action. Check your account or backend setup."
                    it.code == 429 -> "Too many attempts. Please wait a little and try again."
                    it.code >= 500 -> "The server is unavailable. Please try again shortly."
                    else -> (o?.get("msg") ?: o?.get("message") ?: o?.get("error_description"))?.jsonPrimitive?.contentOrNull?.take(220)
                        ?: "Request failed (${it.code}). Please try again."
                }
                throw ApiException(it.code, code, message)
            }
            return result
        }
    }
    suspend fun login(email: String, password: String) {
        require(password.isNotBlank()) { "Enter your password." }
        accept(raw("POST", "auth/v1/token", obj("email" to Rules.email(email), "password" to password), mapOf("grant_type" to "password")))
        store.write("recovery_verifier", null)
    }
    suspend fun signUp(email: String, password: String): Boolean {
        val result = raw("POST", "auth/v1/signup", obj("email" to Rules.email(email), "password" to Rules.password(password)))
        if ((result as? JsonObject)?.get("access_token") is JsonPrimitive) { accept(result); return true }
        return false
    }
    suspend fun recover(email: String) {
        val verifier = Pkce.verifier()
        store.write("recovery_verifier", verifier)
        store.write("recovery_started", clock().toString())
        raw("POST", "auth/v1/recover", obj("email" to Rules.email(email), "code_challenge" to Pkce.challenge(verifier),
            "code_challenge_method" to "s256"), mapOf("redirect_to" to "spark://auth/callback"))
    }
    suspend fun finishRecovery(code: String) {
        val verifier = store.read("recovery_verifier") ?: error("Request a new reset email on this device.")
        val started = store.read("recovery_started")?.toLongOrNull() ?: 0
        require(clock() - started in 0..3600) { "Reset expired. Request a new reset email." }
        require(code.isNotBlank() && code.length <= 512) { "Invalid reset link." }
        accept(raw("POST", "auth/v1/token", obj("auth_code" to code, "code_verifier" to verifier), mapOf("grant_type" to "pkce")))
        store.write("recovery_verifier", null)
        store.write("reset_ready", "true")
    }
    fun resetReady() = store.read("reset_ready") == "true"
    suspend fun updatePassword(password: String) {
        request("PUT", "auth/v1/user", obj("password" to Rules.password(password)))
        store.write("reset_ready", null)
    }
    suspend fun logout() {
        try { if (session.value != null) request("POST", "auth/v1/logout", query = mapOf("scope" to "local")) }
        finally { clearLocalSession() }
    }
    fun clearLocalSession() { save(null); store.write("recovery_verifier", null); store.write("reset_ready", null) }
    suspend fun ensureProfile(): Profile {
        val list = profiles(mapOf("id" to "eq.$userId"))
        if (list.isNotEmpty()) return list.first()
        try {
            request("POST", "rest/v1/profiles", obj("id" to userId, "username" to "spark_${userId.replace("-", "").take(12)}", "display_name" to "New spark"))
        } catch (e: ApiException) { if (e.code != "23505") throw e }
        return profiles(mapOf("id" to "eq.$userId")).firstOrNull() ?: error("Profile unavailable. Check the database setup.")
    }
    suspend fun profiles(filter: Map<String, String> = emptyMap()): List<Profile> =
        json.decodeFromJsonElement(request("GET", "rest/v1/profiles", query = mapOf("select" to "*", "limit" to "40", "order" to "username.asc") + filter))
    suspend fun searchPeople(term: String): List<Profile> {
        val normalized = term.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(24)
        if (normalized.isBlank()) return emptyList()
        return profiles(mapOf("username" to "ilike.${normalized.replace("_", "\\_")}*"))
    }
    suspend fun updateProfile(name: String, username: String, bio: String, privateAccount: Boolean, avatar: String? = null): Profile {
        require(name.trim().length in 1..60 && bio.length <= 160) { "Name needs 1–60 characters; bio has a 160-character limit." }
        val data = buildJsonObject {
            put("display_name", name.trim()); put("username", Rules.username(username)); put("bio", bio.trim()); put("is_private", privateAccount)
            avatar?.let { put("avatar_path", it) }
        }
        val response = request("PATCH", "rest/v1/profiles", data, mapOf("id" to "eq.$userId"), "return=representation")
        return json.decodeFromJsonElement<List<Profile>>(response).firstOrNull() ?: error("Profile could not be updated.")
    }
    suspend fun feed(cursor: FeedCursor? = null, author: String? = null, savedOnly: Boolean = false): List<Post> {
        val query = mutableMapOf("select" to "*", "order" to "created_at.desc,id.desc", "limit" to Rules.PAGE_SIZE.toString())
        author?.let { query["author_id"] = "eq.$it" }
        if (savedOnly) query["saved"] = "eq.true"
        cursor?.let { query["or"] = "(created_at.lt.${it.createdAt},and(created_at.eq.${it.createdAt},id.lt.${it.id}))" }
        return json.decodeFromJsonElement(request("GET", "rest/v1/spark_feed", query = query))
    }
    fun mediaUrl(bucket: String, path: String): String {
        require(bucket in listOf("spark-media", "spark-avatars"))
        require(Regex("[a-f0-9-]{36}/[a-f0-9-]{36}\\.jpg").matches(path))
        return "${config.url}/storage/v1/object/authenticated/$bucket/$path"
    }
    private suspend fun upload(bucket: String, path: String, bytes: ByteArray) {
        Rules.image(bytes)
        raw("POST", "storage/v1/object/$bucket/$path", token = accessToken(), bytes = bytes)
    }
    suspend fun uploadAvatar(bytes: ByteArray): String {
        val path = "$userId/${UUID.randomUUID()}.jpg"
        upload("spark-avatars", path, bytes)
        return path
    }
    suspend fun createPost(bytes: ByteArray, caption: String) {
        val text = Rules.caption(caption)
        val id = UUID.randomUUID().toString()
        val path = "$userId/$id.jpg"
        upload("spark-media", path, bytes)
        // Keep uploaded bytes on an ambiguous network failure. Never remove media that a
        // successful-but-unacknowledged database insert might already reference.
        request("POST", "rest/v1/posts", obj("id" to id, "author_id" to userId, "image_path" to path, "caption" to text))
    }
    suspend fun deletePost(post: Post): Boolean {
        require(post.authorId == userId)
        request("DELETE", "rest/v1/posts", query = mapOf("id" to "eq.${post.id}"))
        return runCatching { removeObject("spark-media", post.imagePath) }.isSuccess
    }
    suspend fun removeObject(bucket: String, path: String) {
        mediaUrl(bucket, path) // Validate the path before sending it.
        request("DELETE", "storage/v1/object/$bucket", buildJsonObject { put("prefixes", buildJsonArray { add(path) }) })
    }
    suspend fun like(post: Post) = relation("likes", "post_id", post.id, !post.liked)
    suspend fun savePost(post: Post) = relation("saves", "post_id", post.id, !post.saved)
    private suspend fun relation(table: String, column: String, id: String, add: Boolean) {
        if (add) request("POST", "rest/v1/$table", obj("user_id" to userId, column to id), prefer = "resolution=ignore-duplicates")
        else request("DELETE", "rest/v1/$table", query = mapOf("user_id" to "eq.$userId", column to "eq.$id"))
    }
    suspend fun comments(postId: String): List<Comment> = json.decodeFromJsonElement(
        request("GET", "rest/v1/spark_comments", query = mapOf("post_id" to "eq.$postId", "order" to "created_at.desc,id.desc", "limit" to "100")))
    suspend fun addComment(postId: String, body: String) {
        request("POST", "rest/v1/comments", obj("post_id" to postId, "author_id" to userId, "body" to Rules.comment(body)))
    }
    suspend fun deleteComment(id: String) { request("DELETE", "rest/v1/comments", query = mapOf("id" to "eq.$id")) }
    suspend fun follows(incoming: Boolean = false): List<Follow> = json.decodeFromJsonElement(
        request("GET", "rest/v1/follows", query = mapOf((if (incoming) "following_id" else "follower_id") to "eq.$userId", "limit" to "1000")))
    suspend fun relationship(id: String): Follow? = json.decodeFromJsonElement<List<Follow>>(
        request("GET", "rest/v1/follows", query = mapOf("follower_id" to "eq.$userId", "following_id" to "eq.$id"))).firstOrNull()
    suspend fun follow(id: String, existing: Follow?) {
        require(id != userId)
        if (existing != null) request("DELETE", "rest/v1/follows", query = mapOf("follower_id" to "eq.$userId", "following_id" to "eq.$id"))
        else request("POST", "rest/v1/follows", obj("follower_id" to userId, "following_id" to id))
    }
    suspend fun resolveFollow(follower: String, accept: Boolean) {
        val query = mapOf("follower_id" to "eq.$follower", "following_id" to "eq.$userId")
        if (accept) request("PATCH", "rest/v1/follows", obj("status" to "accepted"), query)
        else request("DELETE", "rest/v1/follows", query = query)
    }
    suspend fun block(id: String) { require(id != userId); request("POST", "rest/v1/blocks", obj("blocker_id" to userId, "blocked_id" to id), prefer = "resolution=ignore-duplicates") }
    suspend fun blockedIds(): List<String> = request("GET", "rest/v1/blocks", query = mapOf("select" to "blocked_id", "blocker_id" to "eq.$userId"))
        .jsonArray.map { it.jsonObject.getValue("blocked_id").jsonPrimitive.content }
    suspend fun unblock(id: String) { request("DELETE", "rest/v1/blocks", query = mapOf("blocker_id" to "eq.$userId", "blocked_id" to "eq.$id")) }
    suspend fun report(postId: String, reason: String) {
        require(reason in listOf("spam", "harassment", "unsafe_content", "other"))
        request("POST", "rest/v1/reports", obj("reporter_id" to userId, "post_id" to postId, "reason" to reason))
    }
    suspend fun deleteAccount(password: String) {
        require(password.isNotBlank()) { "Enter your current password to confirm deletion." }
        request("POST", "functions/v1/delete-account", obj("confirmation" to "DELETE", "password" to password))
        clearLocalSession()
    }
    private fun obj(vararg entries: Pair<String, String>): JsonObject = buildJsonObject { entries.forEach { (k, v) -> put(k, v) } }
}
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}
