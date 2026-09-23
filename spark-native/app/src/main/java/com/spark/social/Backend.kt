package com.spark.social
import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
object Backend {
    const val URL = "https://twywavuyghftkzsflfrf.supabase.co"
    const val KEY = "sb_publishable_6JVNe6WnP7BC5tHvNqWZFw_5NgCLgq0"
    const val PREFIX = "sparknew_"
    const val BUCKET = "spark-media-v1"
}
fun json(vararg pairs: Pair<String, Any?>) = JSONObject().apply {
    pairs.forEach {
        put(it.first, it.second ?: JSONObject.NULL)
    }
}
fun JSONObject.s(key: String) = if (isNull(key)) "" else optString(key, "")
fun JSONArray.rows() = (0 until length()).map {
    getJSONObject(it)
}
fun JSONObject.child(key: String) = optJSONObject(key) ?: JSONObject()
fun JSONObject.rows(key: String) = optJSONArray(key)?.rows() ?: emptyList()
fun JSONObject.id() = s("id")
class SessionVault(context: Context) {
    private val prefs = context.getSharedPreferences("spark.session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        (store.getKey("spark.session.v1", null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("spark.session.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): JSONObject? {
        return runCatching {
            val encoded = prefs.getString("payload", null) ?: return null
            val parts = encoded.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        }.getOrNull()
    }
    fun save(session: JSONObject?) {
        if (session == null) {
            prefs.edit().clear().apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val data = cipher.doFinal(session.toString().toByteArray())
        prefs.edit().putString("payload", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)).apply()
    }
}
class ApiException(val status: Int, message: String): Exception(message)
class SparkApi private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: SparkApi? = null
        fun get(context: Context): SparkApi = instance ?: synchronized(this) {
            instance ?: SparkApi(context.applicationContext).also {
                instance = it
            }
        }
    }
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(90, TimeUnit.SECONDS).build()
    private val vault = SessionVault(context)
    private var session: JSONObject? = vault.load()
    private val refreshLock = Mutex()
    val userId get() = session?.child("user")?.id() ?: ""
    val signedIn get() = userId.isNotBlank()
    private fun persist(value: JSONObject) {
        if (!value.has("expires_at")) value.put("expires_at", System.currentTimeMillis()/1000 + value.optLong("expires_in", 3600))
        session = value
        vault.save(value)
    }
    private suspend fun raw(path: String, method: String, data: ByteArray? = null, contentType: String = "application/json", auth: Boolean = true, prefer: String? = null): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(Backend.URL + path).header("apikey", Backend.KEY)
        if(auth) session?.s("access_token")?.takeIf {
            it.isNotEmpty()
        }?.let {
            request.header("Authorization", "Bearer $it")
        }
        if(prefer != null) request.header("Prefer", prefer)
        request.method(method, if(method in listOf("GET", "HEAD")) null else (data ?: ByteArray(0)).toRequestBody(contentType.toMediaType()))
        client.newCall(request.build()).execute().use {
            response ->
            val body = response.body?.string() ?: ""
            if(!response.isSuccessful) {
                val error = runCatching {
                    JSONObject(body)
                }.getOrDefault(JSONObject())
                throw ApiException(response.code, error.s("msg").ifEmpty {
                    error.s("message")
                }.ifEmpty {
                    error.s("error_description")
                }.ifEmpty {
                    "Request failed (${response.code}). Try again."
                })
            }
            body
        }
    }
    private suspend fun fresh() = refreshLock.withLock {
        val current = session ?: return@withLock
        val expiry = current.optLong("expires_at", 0)
        if(expiry > System.currentTimeMillis()/1000 + 90) return@withLock
        try {
            val value = JSONObject(raw("/auth/v1/token?grant_type=refresh_token", "POST", json("refresh_token" to current.s("refresh_token")).toString().toByteArray(), auth=false))
            persist(value)
        } catch(e: ApiException) {
            if(e.status in listOf(400,401,403)) {
                session=null
                vault.save(null)
                FastImages.clear()
            }
            throw e
        }
    }
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, prefer: String? = null): String {
        fresh()
        return raw(path, method, body?.toString()?.toByteArray(), prefer=prefer)
    }
    suspend fun login(email: String, password: String) {
        persist(JSONObject(raw("/auth/v1/token?grant_type=password", "POST", json("email" to email.trim(), "password" to password).toString().toByteArray(), auth=false)))
        ensureProfile()
    }
    suspend fun signup(name: String, email: String, password: String): Boolean {
        require(name.trim().isNotEmpty()) {
            "Enter your name."
        }
        require(password.length>=8) {
            "Use at least 8 characters for your password."
        }
        val result = JSONObject(raw("/auth/v1/signup", "POST", json("email" to email.trim(), "password" to password, "data" to json("display_name" to name.trim(),"full_name" to name.trim())).toString().toByteArray(), auth=false))
        if(result.s("access_token").isNotEmpty()) {
            persist(result)
            ensureProfile(name)
            return true
        }
        return false
    }
    suspend fun recover(email: String) {
        raw("/auth/v1/recover", "POST", json("email" to email.trim()).toString().toByteArray(), auth=false)
    }
    suspend fun resetPassword(email: String, code: String, password: String) {
        require(password.length>=8) {
            "Use at least 8 characters."
        }
        persist(JSONObject(raw("/auth/v1/verify", "POST", json("email" to email.trim(), "token" to code.trim(), "type" to "recovery").toString().toByteArray(), auth=false)))
        request("/auth/v1/user","PUT",json("password" to password))
        ensureProfile()
    }
    suspend fun logout() {
        try {
            request("/auth/v1/logout?scope=local","POST")
        } finally {
            session=null
            vault.save(null)
            FastImages.clear()
        }
    }
    suspend fun ensureProfile(name: String = ""): JSONObject {
        require(signedIn) {
            "Please sign in again."
        }
        rows("profiles", "id=eq.$userId").firstOrNull()?.let {
            return it
        }
        val display = name.ifBlank {
            session?.child("user")?.child("user_metadata")?.s("display_name").orEmpty().ifBlank {
                "Spark member"
            }
        }
        return insert("profiles", json("id" to userId,"display_name" to display)).first()
    }
    suspend fun rows(table: String, query: String = ""): List<JSONObject> = JSONArray(request("/rest/v1/${Backend.PREFIX}$table?$query")).rows()
    suspend fun insert(table: String, body: JSONObject) = JSONArray(request("/rest/v1/${Backend.PREFIX}$table", "POST", body, "return=representation")).rows()
    suspend fun update(table: String, filter: String, body: JSONObject) {
        request("/rest/v1/${Backend.PREFIX}$table?$filter", "PATCH", body, "return=minimal")
    }
    suspend fun delete(table: String, filter: String) {
        request("/rest/v1/${Backend.PREFIX}$table?$filter", "DELETE")
    }
    suspend fun upload(uri: Uri): Pair<String,String> = withContext(Dispatchers.IO) {
        fresh()
        var mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        require(mime in listOf("image/jpeg","image/png","image/webp","video/mp4","video/webm")) {
            "Choose a JPG, PNG, WebP, MP4 or WebM file."
        }
        var bytes = context.contentResolver.openInputStream(uri)?.use {
            input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while(true) {
                val count=input.read(buffer)
                if(count<0)break
                require(output.size()+count<=25*1024*1024) {
                    "Maximum upload size is 25 MB."
                }
                output.write(buffer,0,count)
            }
            output.toByteArray()
        } ?: error("Cannot read this file.")
        if(mime.startsWith("image/")) {
            val optimized=optimizePhoto(bytes)
            bytes=optimized.first;mime=optimized.second
        }
        val ext = mapOf("image/jpeg" to "jpg","image/png" to "png","image/webp" to "webp","video/mp4" to "mp4","video/webm" to "webm")[mime]
        val path = "$userId/${UUID.randomUUID()}.$ext"
        raw("/storage/v1/object/${Backend.BUCKET}/$path", "POST",bytes,mime)
        Pair(path,if(mime.startsWith("video")) "video" else "image")
    }
    suspend fun removeMedia(path: String) {
        request("/storage/v1/object/${Backend.BUCKET}","DELETE",json("prefixes" to JSONArray().put(path)))
    }
    // Refreshed user JWT keeps private media behind existing Storage RLS.
    // Unlike a 60-second signed URL, later video range requests remain authorized.
    suspend fun downloadMedia(path:String):ByteArray=withContext(Dispatchers.IO) {
        val access=mediaAccess(path)
        val request=Request.Builder().url(access.url).apply { access.headers.forEach { (k,v)->header(k,v) } }.build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Photo unavailable (${response.code})." }
            response.body?.bytes()?:error("Empty image.")
        }
    }
    suspend fun mediaAccess(path: String): MediaAccess {
        require(path.isNotBlank()) { "Media is missing." }
        fresh()
        val token = session?.s("access_token").orEmpty()
        check(token.isNotBlank()) { "Sign in again to view this media." }
        val encoded = path.split("/").joinToString("/") { Uri.encode(it) }
        return MediaAccess(
            "${Backend.URL}/storage/v1/object/authenticated/${Backend.BUCKET}/$encoded",
            mapOf("apikey" to Backend.KEY, "Authorization" to "Bearer $token")
        )
    }
    suspend fun signedUrl(path: String): String {
        if(path.isBlank()) return ""
        val result=JSONObject(request("/storage/v1/object/sign/${Backend.BUCKET}/$path","POST",json("expiresIn" to 60)))
        val url=result.s("signedURL").ifBlank {
            result.s("signedUrl")
        }
        return if(url.startsWith("http")) url else Backend.URL+"/storage/v1"+url
    }
    suspend fun feed(kind: String="post", extra: String="", offset: Int=0) = rows("posts", "select=*,author:sparknew_profiles!author_id(*),reactions:sparknew_reactions(*),comments:sparknew_comments(count)&kind=eq.$kind&order=created_at.desc,id.desc&limit=20&offset=$offset$extra")
    suspend fun createPost(body: String, uri: Uri?, kind: String, visibility: String, community: String?=null) {
        require(body.isNotBlank()||uri!=null) {
            "Write something or choose a photo/video."
        }
        var media: Pair<String,String>?=null
        try {
            if(uri!=null) media=upload(uri)
            require(kind!="reel"||media?.second=="video") {
                "Reels need a video."
            }
            insert("posts",json("author_id" to userId,"body" to body.trim(),"kind" to kind,"visibility" to visibility,"community_id" to community,"media_path" to media?.first,"media_type" to media?.second))
        } catch(e: Exception) {
            if(e is IllegalArgumentException || (e is ApiException && e.status in 400..499)) media?.let {
                runCatching {
                    removeMedia(it.first)
                }
            }
            throw e
        }
    }
    suspend fun conversation(other: String): JSONObject {
        val (a,b)=listOf(userId,other).sorted()
        rows("conversations","user_a=eq.$a&user_b=eq.$b").firstOrNull()?.let {
            return it
        }
        return try {
            insert("conversations",json("user_a" to a,"user_b" to b)).first()
        }
        catch(e: ApiException) {
            if(e.status==409)rows("conversations","user_a=eq.$a&user_b=eq.$b").first() else throw e
        }
    }
    suspend fun send(chat: String, body: String, uri: Uri?) {
        require(body.isNotBlank()||uri!=null) {
            "Write a message first."
        }
        var media:Pair<String,String>?=null
        try {
            if(uri!=null)media=upload(uri)
            insert("messages",json("conversation_id" to chat,"sender_id" to userId,"body" to body.trim(),"media_path" to media?.first,"media_type" to media?.second))
        }
        catch(e:Exception) {
            if(e is IllegalArgumentException || (e is ApiException && e.status in 400..499)) media?.let {
                runCatching {
                    removeMedia(it.first)
                }
            }
            throw e
        }
    }
}
