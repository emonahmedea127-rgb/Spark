package com.example.sociva.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.sociva.call.CallActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SparkMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    getSharedPreferences("spark.push", MODE_PRIVATE)
      .edit()
      .putString("token", token)
      .apply()
    SparkPush.refresh(applicationContext)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    val data = message.data
    val expectedUserId = getSharedPreferences("sociva_session", MODE_PRIVATE)
      .getString("auth_user_id", "")
      .orEmpty()

    val recipientId = data["recipient_id"].orEmpty()
    if (expectedUserId.isBlank() || recipientId != expectedUserId) return

    if (Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val isCall = data["kind"] == "call"
    val manager = getSystemService(NotificationManager::class.java)
    val channelId = if (isCall) "spark_calls" else "spark_updates"
    val channelName = if (isCall) "Incoming calls" else "Messages and notifications"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        channelId,
        channelName,
        if (isCall) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = if (isCall) "Incoming Spark audio and video calls" else "Spark messages and notifications"
        enableVibration(true)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
      }
      manager.createNotificationChannel(channel)
    }

    val eventId = data["event_id"] ?: return
    val notificationId = eventId.hashCode()

    if (isCall) {
      val callId = data["target_id"] ?: return
      val isVideo = data["title"]?.contains("video", ignoreCase = true) == true
      val intent = CallActivity.incomingPushIntent(this, callId, isVideo)
      val pending = PendingIntent.getActivity(
        this,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val notification = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(data["title"] ?: if (isVideo) "Incoming video call" else "Incoming audio call")
        .setContentText(data["body"] ?: "Tap to answer")
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setContentIntent(pending)
        .setFullScreenIntent(pending, true)
        .setOngoing(true)
        .setAutoCancel(false)
        .setTimeoutAfter(90_000L)
        .setOnlyAlertOnce(true)
        .build()

      manager.notify(notificationId, notification)
      return
    }

    val launchIntent = Intent(this, MainActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      .putExtra("kind", data["kind"])
      .putExtra("target_id", data["target_id"])

    val pending = PendingIntent.getActivity(
      this,
      notificationId,
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, channelId)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(data["title"] ?: "Spark")
      .setContentText(data["body"] ?: "You have a new notification")
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setContentIntent(pending)
      .setAutoCancel(true)
      .setOnlyAlertOnce(true)
      .build()

    manager.notify(notificationId, notification)
  }
}
