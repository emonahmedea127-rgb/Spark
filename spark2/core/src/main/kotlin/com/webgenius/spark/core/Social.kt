package com.webgenius.spark.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import java.util.UUID
import java.time.Instant

@Serializable data class Friendship(val id: String, @SerialName("requester_id") val requesterId: String,
 @SerialName("addressee_id") val addresseeId: String, val status: String) {
 fun other(me: String) = if (requesterId == me) addresseeId else requesterId
}
@Serializable data class ProfileTotals(val id: String = "", val friends: Int = 0, val followers: Int = 0, val following: Int = 0)
@Serializable data class Preferences(@SerialName("user_id") val userId: String = "",
 @SerialName("ghost_mode") val ghostMode: Boolean = true, @SerialName("visit_notifications") val visitNotifications: Boolean = true)
@Serializable data class Visit(@SerialName("owner_id") val ownerId: String, @SerialName("visitor_id") val visitorId: String,
 @SerialName("visited_at") val visitedAt: String, val username: String, @SerialName("display_name") val displayName: String,
 @SerialName("avatar_path") val avatarPath: String? = null)
@Serializable data class Conversation(val id: String, @SerialName("member_a") val memberA: String, @SerialName("member_b") val memberB: String) {
 fun other(me: String) = if (memberA == me) memberB else memberA
}
@Serializable data class ChatMessage(val id: String, @SerialName("conversation_id") val conversationId: String,
 @SerialName("sender_id") val senderId: String, val body: String = "", @SerialName("media_path") val mediaPath: String? = null,
 @SerialName("media_kind") val mediaKind: String? = null, @SerialName("post_id") val postId: String? = null,
 @SerialName("created_at") val createdAt: String)
@Serializable data class CallSession(val id: String, @SerialName("conversation_id") val conversationId: String,
 @SerialName("caller_id") val callerId: String, @SerialName("callee_id") val calleeId: String,
 val video: Boolean, val status: String, val offer: String, val answer: String? = null,
 @SerialName("created_at") val createdAt: String)
private fun data(vararg pairs: Pair<String,String>) = buildJsonObject { pairs.forEach { put(it.first,it.second) } }
private fun uuid(id: String) = UUID.fromString(id).toString()
suspend fun SparkApi.friendships(): List<Friendship> = json.decodeFromJsonElement(request("GET","rest/v1/friendships",query=mapOf("or" to "(requester_id.eq.$userId,addressee_id.eq.$userId)","limit" to "200","order" to "created_at.desc")))
suspend fun SparkApi.requestFriend(id: String) { request("POST","rest/v1/friendships",data("requester_id" to userId,"addressee_id" to uuid(id))) }
suspend fun SparkApi.resolveFriend(friendship: Friendship, accept: Boolean) {
 request(if(accept) "PATCH" else "DELETE","rest/v1/friendships",if(accept) data("status" to "accepted") else null,mapOf("id" to "eq.${uuid(friendship.id)}"))
}
suspend fun SparkApi.profileTotals(id: String): ProfileTotals = json.decodeFromJsonElement<List<ProfileTotals>>(request("GET","rest/v1/spark_profile_totals",query=mapOf("id" to "eq.${uuid(id)}"))).firstOrNull() ?: ProfileTotals()
suspend fun SparkApi.connections(id: String, followers: Boolean): List<Profile> {
 val edges = json.decodeFromJsonElement<List<Follow>>(request("GET","rest/v1/follows",query=mapOf((if(followers) "following_id" else "follower_id") to "eq.${uuid(id)}","status" to "eq.accepted","limit" to "100")))
 return profilesByIds(edges.map { if(followers) it.followerId else it.followingId })
}
suspend fun SparkApi.profilesByIds(ids: List<String>): List<Profile> = if(ids.isEmpty()) emptyList() else profiles(mapOf("id" to "in.(${ids.take(100).joinToString(",") { uuid(it) }})","limit" to "100"))
suspend fun SparkApi.preferences(): Preferences {
 request("POST","rest/v1/user_preferences",data("user_id" to userId),prefer="resolution=ignore-duplicates")
 return json.decodeFromJsonElement<List<Preferences>>(request("GET","rest/v1/user_preferences",query=mapOf("user_id" to "eq.$userId"))).first()
}
suspend fun SparkApi.setPreferences(ghost: Boolean, visits: Boolean): Preferences {
 request("PATCH","rest/v1/user_preferences",buildJsonObject { put("ghost_mode",ghost);put("visit_notifications",visits) },mapOf("user_id" to "eq.$userId"))
 return Preferences(userId,ghost,visits)
}
suspend fun SparkApi.recordVisit(id: String) { request("POST","rest/v1/rpc/spark_record_visit",data("target" to uuid(id))) }
suspend fun SparkApi.visits(): List<Visit> = json.decodeFromJsonElement(request("GET","rest/v1/spark_visits",query=mapOf("order" to "visited_at.desc","limit" to "50")))
suspend fun SparkApi.conversations(): List<Conversation> = json.decodeFromJsonElement(request("GET","rest/v1/conversations",query=mapOf("limit" to "100","order" to "created_at.desc")))
suspend fun SparkApi.openConversation(peer: String): Conversation {
 val members=listOf(userId,uuid(peer)).sorted()
 val query=mapOf("member_a" to "eq.${members[0]}","member_b" to "eq.${members[1]}")
 suspend fun find()=json.decodeFromJsonElement<List<Conversation>>(request("GET","rest/v1/conversations",query=query)).firstOrNull()
 find()?.let { return it }
 try { request("POST","rest/v1/conversations",data("member_a" to members[0],"member_b" to members[1])) }
 catch(e: ApiException) { if(e.code!="23505") throw e }
 return find() ?: error("Accept a friend request before chatting.")
}
suspend fun SparkApi.messages(chat: String, before: String? = null): List<ChatMessage> {
 val query=mutableMapOf("conversation_id" to "eq.${uuid(chat)}","order" to "created_at.desc,id.desc","limit" to "50")
 before?.let { query["created_at"]="lt.$it" }
 return json.decodeFromJsonElement<List<ChatMessage>>(request("GET","rest/v1/messages",query=query)).reversed()
}
fun validateVideo(bytes: ByteArray) {
 require(bytes.size in 12..(20*1024*1024)) { "Choose an MP4 video smaller than 20 MB." }
 require(String(bytes,4,4,Charsets.US_ASCII)=="ftyp") { "Choose an MP4 video." }
}
suspend fun SparkApi.uploadSocial(bucket: String,path: String,bytes: ByteArray,video: Boolean) {
 mediaUrl(bucket,path)
 if(video) validateVideo(bytes) else Rules.image(bytes)
 raw("POST","storage/v1/object/$bucket/$path",token=accessToken(),bytes=bytes,mime=if(video) "video/mp4" else "image/jpeg")
}
suspend fun SparkApi.createVideo(bytes: ByteArray,caption: String) {
 val id=UUID.randomUUID().toString();val path="$userId/$id.mp4";val text=Rules.caption(caption)
 uploadSocial("spark-videos",path,bytes,true)
 request("POST","rest/v1/posts",data("id" to id,"author_id" to userId,"image_path" to path,"caption" to text,"media_kind" to "video"))
}
suspend fun SparkApi.sendMessage(chat: String,body: String,bytes: ByteArray?=null,video: Boolean=false,postId: String?=null) {
 require(body.length<=2000 && (body.isNotBlank() || bytes!=null || postId!=null)) { "Write a message or attach media." }
 val id=UUID.randomUUID().toString();val path=bytes?.let { "$userId/$id.${if(video) "mp4" else "jpg"}" }
 if(bytes!=null) uploadSocial("spark-chat",path!!,bytes,video)
 request("POST","rest/v1/messages",buildJsonObject {
 put("id",id);put("conversation_id",uuid(chat));put("sender_id",userId);put("body",body.trim())
 path?.let { put("media_path",it);put("media_kind",if(video) "video" else "photo") }
 postId?.let { put("post_id",uuid(it)) }
 })
}
suspend fun SparkApi.post(id: String): Post? = json.decodeFromJsonElement<List<Post>>(request("GET","rest/v1/spark_feed",query=mapOf("id" to "eq.${uuid(id)}"))).firstOrNull()
suspend fun SparkApi.createCall(chat: String,peer: String,video: Boolean,offer: String): CallSession = json.decodeFromJsonElement<List<CallSession>>(request("POST","rest/v1/call_sessions",buildJsonObject {
 put("conversation_id",uuid(chat));put("caller_id",userId);put("callee_id",uuid(peer));put("video",video);put("offer",offer)
 },prefer="return=representation")).first()
suspend fun SparkApi.incomingCall(): CallSession? = json.decodeFromJsonElement<List<CallSession>>(request("GET","rest/v1/call_sessions",query=mapOf("callee_id" to "eq.$userId","status" to "eq.ringing","created_at" to "gt.${Instant.now().minusSeconds(120)}","order" to "created_at.desc","limit" to "1"))).firstOrNull()
suspend fun SparkApi.call(id: String): CallSession? = json.decodeFromJsonElement<List<CallSession>>(request("GET","rest/v1/call_sessions",query=mapOf("id" to "eq.${uuid(id)}"))).firstOrNull()
suspend fun SparkApi.answerCall(id: String,answer: String) { request("PATCH","rest/v1/call_sessions",data("answer" to answer,"status" to "accepted"),mapOf("id" to "eq.${uuid(id)}")) }
suspend fun SparkApi.endCall(id: String) { request("PATCH","rest/v1/call_sessions",data("status" to "ended"),mapOf("id" to "eq.${uuid(id)}")) }
