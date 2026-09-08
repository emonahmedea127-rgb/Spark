package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.storage.FirebaseStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testFirebaseStorage() = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
    println("Testing FirebaseStorage...")
    try {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val options = FirebaseOptions.Builder()
        .setApplicationId("com.aistudio.sociva.social")
        .setApiKey("AIzaSyCT_y2Q2v1z5kKPH8nsAXC1cA1fgfWjshY")
        .setProjectId("helloworld-d72dc")
        .setStorageBucket("helloworld-d72dc.firebasestorage.app")
        .build()

      if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(context, options)
      }
      val app = FirebaseApp.getInstance()
      println("FirebaseApp: ${app.name}, bucket: ${app.options.storageBucket}")

      println("Checking buckets...")
      val buckets = listOf(
        "helloworld-d72dc.appspot.com",
        "helloworld-d72dc.firebasestorage.app"
      )
      println("Checking GCS endpoints...")
      val gcsUrls = listOf(
        "https://storage.googleapis.com/storage/v1/b/helloworld-d72dc.firebasestorage.app",
        "https://storage.googleapis.com/storage/v1/b/google",
        "https://storage.googleapis.com/storage/v1/b/chromium"
      )
      for (u in gcsUrls) {
        try {
          val url = java.net.URL(u)
          val conn = url.openConnection() as java.net.HttpURLConnection
          conn.requestMethod = "GET"
          val code = conn.responseCode
          val stream = if (code in 200..299) conn.inputStream else conn.errorStream
          val resp = stream?.bufferedReader()?.readText()?.take(200) ?: ""
          println("GCS URL $u -> code: $code, body: $resp")
        } catch (e: Exception) {
          println("GCS URL $u failed: $e")
        }
      }



    } catch (e: Throwable) {
      println("FirebaseStorage test exception: $e")
      e.printStackTrace()
    }
  }
}



