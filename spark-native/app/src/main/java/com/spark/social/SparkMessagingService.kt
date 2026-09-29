package com.spark.social

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.*
import java.time.Instant

class SparkApplication:android.app.Application() {
    override fun onCreate() { super.onCreate();SparkPush.initialize(this) }
}

object SparkPush {
    private val registrationScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun refresh(context:Context) { registrationScope.launch { runCatching { register(context.applicationContext) } } }
    fun initialize(context:Context):Boolean {
        val appId=context.getString(R.string.spark_firebase_app_id)
        if(appId.isBlank())return false
        return runCatching {
            if(FirebaseApp.getApps(context).isEmpty())FirebaseApp.initializeApp(context,
                FirebaseOptions.Builder().setApplicationId(appId)
                    .setProjectId(context.getString(R.string.spark_firebase_project_id))
                    .setGcmSenderId(context.getString(R.string.spark_firebase_sender_id))
                    .setApiKey(context.getString(R.string.spark_firebase_api_key)).build())
            FirebaseMessaging.getInstance().isAutoInitEnabled=true
            true
        }.getOrDefault(false)
    }
    suspend fun register(context:Context) {
        if(!initialize(context))return
        val api=SparkApi.get(context)
        if(!api.signedIn)return
        val token=FirebaseMessaging.getInstance().token.await()
        api.request("/rest/v1/rpc/sparknew_register_push","POST",json("device_token" to token))
        context.getSharedPreferences("spark.push",Context.MODE_PRIVATE).edit().putString("token",token).apply()
    }
    suspend fun unregister(context:Context) {
        val prefs=context.getSharedPreferences("spark.push",Context.MODE_PRIVATE)
        val token=prefs.getString("token",null)
        try {
            if(token!=null)SparkApi.get(context).request("/rest/v1/sparknew_push_devices?token=eq.${java.net.URLEncoder.encode(token,"UTF-8")}","DELETE")
        } finally {
            prefs.edit().remove("token").apply()
            context.getSystemService(NotificationManager::class.java).cancelAll()
            if(initialize(context))FirebaseMessaging.getInstance().deleteToken()
        }
    }
}

class SparkMessagingService:FirebaseMessagingService() {
    // Registration is retried on every foreground resume, including token rotations.
    override fun onNewToken(token:String) {
        getSharedPreferences("spark.push",MODE_PRIVATE).edit().putString("token",token).apply()
        SparkPush.refresh(applicationContext)
    }
    override fun onMessageReceived(message:RemoteMessage) {
        val data=message.data
        val api=SparkApi.get(this)
        if(!api.signedIn||data["recipient_id"]!=api.userId)return
        val expiry=runCatching{Instant.parse(data["expires_at"]).toEpochMilli()}.getOrDefault(0)
        if(expiry<=System.currentTimeMillis())return
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val call=data["kind"]=="call"
        val manager=getSystemService(NotificationManager::class.java)
        val channel=if(call)"spark_calls" else "spark_updates"
        manager.createNotificationChannel(NotificationChannel(channel,if(call)"Incoming calls" else "Messages and notifications",NotificationManager.IMPORTANCE_HIGH))
        val intent=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        listOf("recipient_id","kind","target_id","expires_at").forEach{intent.putExtra(it,data[it])}
        val id=(data["event_id"]?:return).hashCode()
        val pending=PendingIntent.getActivity(this,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(data["title"]?:"Spark")
            .setContentText(data["body"]?:"You have a new notification")
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(if(call)NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setTimeoutAfter(expiry-System.currentTimeMillis()).build()
        manager.notify(id,notification)
    }
}
