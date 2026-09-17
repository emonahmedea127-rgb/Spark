package com.webgenius.spark.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Serializable data class Session(
    val accessToken: String, val refreshToken: String, val expiresAt: Long, val userId: String
)
@Serializable data class Profile(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String = "New spark",
    val bio: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("is_private") val isPrivate: Boolean = true,
    @SerialName("deleting_at") val deletingAt: String? = null
)
@Serializable data class Post(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val caption: String = "",
    @SerialName("image_path") val imagePath: String,
    @SerialName("created_at") val createdAt: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    val liked: Boolean = false,
    val saved: Boolean = false,
    @SerialName("media_kind") val mediaKind: String = "photo"
)
@Serializable data class Comment(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    val username: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null
)
@Serializable data class Follow(
    @SerialName("follower_id") val followerId: String,
    @SerialName("following_id") val followingId: String,
    val status: String
)
data class FeedCursor(val createdAt: String, val id: String)
data class Config(val url: String, val publishableKey: String) {
    fun validate(): Config {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
        require(uri != null && uri.scheme == "https" && uri.host != null &&
            Regex("[a-z0-9]{20}\\.supabase\\.co").matches(uri.host) &&
            uri.userInfo == null && uri.port == -1 && uri.query == null && uri.fragment == null &&
            (uri.path.isNullOrEmpty() || uri.path == "/")) {
            "Use your hosted project URL: https://your-project-ref.supabase.co"
        }
        require(Regex("sb_publishable_[A-Za-z0-9_-]{10,}").matches(publishableKey.trim())) {
            "Use a publishable key beginning sb_publishable_. Never enter a secret or service-role key."
        }
        return copy(url = url.trim().trimEnd('/'), publishableKey = publishableKey.trim())
    }
}
object Rules {
    const val PAGE_SIZE = 20
    const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    fun email(value: String): String = value.trim().also {
        require(it.length <= 254 && Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(it)) { "Enter a valid email address." }
    }
    fun password(value: String): String = value.also {
        require(it.length in 12..128) { "Use a password with 12 to 128 characters." }
    }
    fun username(value: String): String = value.trim().lowercase().also {
        require(Regex("[a-z0-9_]{3,24}").matches(it)) { "Username needs 3–24 lowercase letters, numbers or underscores." }
    }
    fun caption(value: String): String = value.trim().also {
        require(it.length <= 2200) { "Caption is limited to 2,200 characters." }
    }
    fun comment(value: String): String = value.trim().also {
        require(it.isNotEmpty() && it.length <= 1000) { "Write a comment between 1 and 1,000 characters." }
    }
    fun image(bytes: ByteArray) {
        require(bytes.size in 3..MAX_IMAGE_BYTES) { "Photo must be smaller than 8 MB." }
        require(bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()) { "Choose a valid photo." }
    }
}
object Pkce {
    fun verifier(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(48).also { SecureRandom().nextBytes(it) })
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    )
}
interface SecretStore {
    fun read(key: String): String?
    fun write(key: String, value: String?)
}
class ApiException(val status: Int, val code: String, override val message: String) : Exception(message)
