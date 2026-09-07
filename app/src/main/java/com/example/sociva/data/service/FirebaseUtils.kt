package com.example.sociva.data.service

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits the completion of a Google Play Services / Firebase Task without blocking the thread.
 */
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
  addOnSuccessListener { result ->
    if (cont.isActive) {
      cont.resume(result)
    }
  }
  addOnFailureListener { exception ->
    if (cont.isActive) {
      cont.resumeWithException(exception)
    }
  }
  addOnCanceledListener {
    if (cont.isActive) {
      cont.cancel()
    }
  }
}
