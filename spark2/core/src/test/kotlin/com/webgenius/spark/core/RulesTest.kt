package com.webgenius.spark.core

import org.junit.Test
import kotlin.test.*

class RulesTest {
    @Test fun normalizesEmail() { assertEquals("someone@example.com", Rules.email(" someone@example.com ")) }
    @Test fun rejectsInvalidEmail() { listOf("x", "a@@b.c", "one two@three.four").forEach { assertFailsWith<IllegalArgumentException> { Rules.email(it) } } }
    @Test fun usernamesAreNormalized() { assertEquals("emon_123",Rules.username(" Emon_123 ")) }
    @Test fun rejectsUnsafeUsernames() { listOf("a", "two words", "a*b", "../user", "a".repeat(25)).forEach { assertFailsWith<IllegalArgumentException> { Rules.username(it) } } }
    @Test fun strongEnoughPasswords() { assertEquals(12, Rules.password("abcdefghijkl").length); assertFailsWith<IllegalArgumentException> { Rules.password("short") } }
    @Test fun captionBoundary() { Rules.caption("a".repeat(2200)); assertFailsWith<IllegalArgumentException> { Rules.caption("a".repeat(2201)) } }
    @Test fun commentsRequireContent() { assertFailsWith<IllegalArgumentException> { Rules.comment("   ") }; assertEquals("hello", Rules.comment(" hello ")) }
    @Test fun imageTypeAndSize() { Rules.image(byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte())); assertFailsWith<IllegalArgumentException> { Rules.image("not an image".toByteArray()) }; assertFailsWith<IllegalArgumentException> { Rules.image(ByteArray(Rules.MAX_IMAGE_BYTES+1)) } }
    @Test fun configOnlyAcceptsHostedHttpsPublishableKey() {
        val c=Config("https://abcdefghijklmnopqrst.supabase.co/","sb_publishable_1234567890abcdefgh").validate()
        assertFalse(c.url.endsWith('/'))
        listOf("http://abcdefghijklmnopqrst.supabase.co", "https://evil.test", "https://abcdefghijklmnopqrst.supabase.co.evil.test", "https://abcdefghijklmnopqrst.supabase.co/path", "https://user@abcdefghijklmnopqrst.supabase.co").forEach { assertFailsWith<IllegalArgumentException> { c.copy(url=it).validate() } }
        listOf("sb_secret_12345678901234", "service_role", "eyJhbGciOiJIUzI1NiJ9").forEach { assertFailsWith<IllegalArgumentException> { c.copy(publishableKey=it).validate() } }
    }
    @Test fun pkceMatchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        assertNotEquals(Pkce.verifier(),Pkce.verifier()); assertTrue(Pkce.verifier().length in 43..128)
    }
}
