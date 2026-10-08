package com.spark.social

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Bounded exponential backoff; cancellation always stops work on logout/background. */
internal suspend fun retryPushRegistration(
    wait: suspend (Long) -> Unit = { delay(it) },
    register: suspend () -> Unit
): Boolean {
    val backoff = longArrayOf(1000, 2000, 4000, 8000)
    for (attempt in 0..backoff.size) {
        try { register(); return true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (attempt == backoff.size) return false }
        wait(backoff[attempt])
    }
    return false
}
