package com.webgenius.spark.core

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test
import kotlin.test.*

private class MemoryStore : SecretStore {
    private val values = mutableMapOf<String,String>()
    @Synchronized override fun read(key:String)=values[key]
    @Synchronized override fun write(key:String,value:String?) { if(value==null) values.remove(key) else values[key]=value; Unit }
}
private class FakeTransport : Interceptor {
    val replies = ArrayDeque<Pair<Int,String>>()
    val requests = mutableListOf<Request>()
    val bodies = mutableListOf<String>()
    @Synchronized override fun intercept(chain:Interceptor.Chain):Response {
        requests += chain.request()
        bodies += Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
        val (code,text)=replies.removeFirstOrNull() ?: error("Unexpected request: ${chain.request().url.encodedPath}")
        return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Test response")
            .body(text.toResponseBody("application/json".toMediaType())).build()
    }
    fun reply(text:String="[]",code:Int=200) { replies.addLast(code to text) }
}
class SparkApiTest {
    private val uid="11111111-1111-4111-8111-111111111111"
    private val config=Config("https://abcdefghijklmnopqrst.supabase.co","sb_publishable_testing1234567")
    private fun fixture(expires:Long=5000):Triple<SparkApi,FakeTransport,MemoryStore> {
        val store=MemoryStore().apply { write("session",Json.encodeToString(Session("old-access","refresh",expires,uid))) }
        val transport=FakeTransport()
        return Triple(SparkApi(config,store,OkHttpClient.Builder().addInterceptor(transport).build(),clock={1000}),transport,store)
    }
    private fun authResponse()="""{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600,"user":{"id":"$uid"}}"""
    @Test fun sendsPublishableKeyAndBearer()=runBlocking {
        val(api,http,_)=fixture(); http.reply(); api.feed()
        assertEquals(config.publishableKey,http.requests[0].header("apikey")); assertEquals("Bearer old-access",http.requests[0].header("Authorization"))
    }
    @Test fun refreshesExpiredSessionsAndPersistsRotatedToken()=runBlocking {
        val(api,http,store)=fixture(900); http.reply(authResponse()); http.reply(); api.feed()
        assertEquals("refresh_token",http.requests[0].url.queryParameter("grant_type")); assertEquals("Bearer new-access",http.requests[1].header("Authorization")); assertTrue(store.read("session")!!.contains("new-refresh"))
    }
    @Test fun concurrentRequestsShareOneTokenRefresh()=runBlocking {
        val(api,http,_)=fixture(900); http.reply(authResponse()); http.reply(); http.reply()
        listOf(async { api.feed() },async { api.feed() }).awaitAll()
        assertEquals(1,http.requests.count { it.url.encodedPath.endsWith("/token") })
    }
    @Test fun invalidRefreshClearsLocalSession()=runBlocking {
        val(api,http,store)=fixture(900); http.reply("{}",400)
        assertFailsWith<ApiException> { api.feed() }; assertNull(store.read("session")); assertNull(api.session.value)
    }
    @Test fun temporaryRefreshFailureDoesNotLoseSession(): Unit = runBlocking {
        val(api,http,store)=fixture(900); http.reply("{}",503)
        assertFailsWith<ApiException> { api.feed() }; assertNotNull(store.read("session"))
    }
    @Test fun unauthorizedRequestRefreshesOnce()=runBlocking {
        val(api,http,_)=fixture(); http.reply("{}",401); http.reply(authResponse()); http.reply(); api.feed()
        assertEquals(3,http.requests.size)
    }
    @Test fun feedUsesStableCompositeCursor()=runBlocking {
        val(api,http,_)=fixture(); http.reply(); api.feed(FeedCursor("2026-09-16T12:00:00Z",uid))
        assertEquals("created_at.desc,id.desc",http.requests[0].url.queryParameter("order"))
        assertEquals("20",http.requests[0].url.queryParameter("limit"))
        assertTrue(http.requests[0].url.queryParameter("or")!!.contains("id.lt.$uid"))
    }
    @Test fun mediaUrlsCannotEscapeTheUserFolder() {
        val(api,_,_)=fixture(); assertFailsWith<IllegalArgumentException> { api.mediaUrl("spark-media","../../secret.jpg") }
        assertFailsWith<IllegalArgumentException> { api.mediaUrl("other-bucket","$uid/$uid.jpg") }
        assertTrue(api.mediaUrl("spark-media","$uid/$uid.jpg").contains("/authenticated/"))
    }
    @Test fun invalidCaptionDoesNotUploadAnyBytes()=runBlocking {
        val(api,http,_)=fixture(); assertFailsWith<IllegalArgumentException> { api.createPost(byteArrayOf(1),"x".repeat(2201)) }; assertTrue(http.requests.isEmpty())
    }
    @Test fun uploadedObjectIsLinkedWithTheSamePostId()=runBlocking {
        val(api,http,_)=fixture(); http.reply("{}"); http.reply("{}")
        api.createPost(byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte()),"hello")
        assertTrue(http.requests[0].url.encodedPath.startsWith("/storage/v1/object/spark-media/$uid/"))
        val path=http.requests[0].url.encodedPath.substringAfter("spark-media/")
        assertTrue(http.bodies[1].contains(path)); assertEquals("false",http.requests[0].header("x-upsert"))
    }
    @Test fun failedUploadNeverCreatesADatabasePost()=runBlocking {
        val(api,http,_)=fixture(); http.reply("{}",403)
        assertFailsWith<ApiException> { api.createPost(byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte()),"hello") }
        assertEquals(1,http.requests.size)
    }
    @Test fun ambiguousPostFailureDoesNotDeletePossiblyCommittedMedia()=runBlocking {
        val(api,http,_)=fixture(); http.reply("{}"); http.reply("{}",503)
        assertFailsWith<ApiException> { api.createPost(byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte()),"hello") }
        assertTrue(http.requests.none { it.method=="DELETE" })
    }
    @Test fun passwordRecoveryUsesStoredPkceVerifier()=runBlocking {
        val(api,http,store)=fixture(); http.reply("{}"); api.recover("user@example.com")
        val verifier=store.read("recovery_verifier")!!
        assertTrue(http.bodies[0].contains(Pkce.challenge(verifier))); assertFalse(http.bodies[0].contains(verifier))
        assertEquals("spark://auth/callback",http.requests[0].url.queryParameter("redirect_to"))
    }
    @Test fun cannotExchangeAnUnsolicitedResetCode()=runBlocking {
        val(api,http,_)=fixture(); assertFailsWith<IllegalStateException> { api.finishRecovery("code") }; assertTrue(http.requests.isEmpty())
    }
    @Test fun logoutClearsSecretsEvenWhenServerIsOffline()=runBlocking {
        val(api,http,store)=fixture(); http.reply("{}",503); assertFailsWith<ApiException> { api.logout() }; assertNull(store.read("session"))
    }
}
