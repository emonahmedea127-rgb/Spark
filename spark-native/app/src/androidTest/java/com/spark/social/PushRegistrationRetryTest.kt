package com.spark.social
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PushRegistrationRetryTest {
    @Test fun temporaryNetworkFailureRecovers()=runBlocking {
        var attempts=0;val waits=mutableListOf<Long>()
        val ok=retryPushRegistration(wait={waits.add(it)}) { attempts++;if(attempts<3)error("offline") }
        assertTrue(ok);assertEquals(3,attempts);assertEquals(listOf(1000L,2000L),waits)
    }
    @Test fun persistentFailureIsBounded()=runBlocking {
        var attempts=0;val waits=mutableListOf<Long>()
        assertFalse(retryPushRegistration(wait={waits.add(it)}) {attempts++;error("offline")})
        assertEquals(5,attempts);assertEquals(listOf(1000L,2000L,4000L,8000L),waits)
    }
    @Test fun backgroundCancellationDoesNotRetry()=runBlocking {
        var waits=0;var cancelled=false
        try {retryPushRegistration(wait={waits++}) {throw CancellationException("background")}}
        catch (_:CancellationException){cancelled=true}
        assertTrue(cancelled);assertEquals(0,waits)
    }
}
