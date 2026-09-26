package com.spark.social

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/** Short original effects. Foreground only; system silent mode and volume are respected. */
object SparkSounds {
    enum class Event { LIKE, REACT, POST, MESSAGE, NOTICE }
    private var uiPool:SoundPool?=null
    private var noticePool:SoundPool?=null
    private var ids:Map<Event,Int> = emptyMap()
    private fun initialize(context:Context){
        if(uiPool!=null)return
        val ui=SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        val notifications=SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        ids=mapOf(
            Event.LIKE to ui.load(context,R.raw.spark_like,1),
            Event.REACT to ui.load(context,R.raw.spark_react,1),
            Event.POST to ui.load(context,R.raw.spark_post,1),
            Event.MESSAGE to notifications.load(context,R.raw.spark_message,1),
            Event.NOTICE to notifications.load(context,R.raw.spark_notice,1)
        )
        uiPool=ui;noticePool=notifications
    }
    @Synchronized fun play(context:Context,event:Event,enabled:Boolean=true){
        if(!enabled)return
        val manager=context.getSystemService(AudioManager::class.java)
        if(manager.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        val notification=event==Event.MESSAGE||event==Event.NOTICE
        if(manager.getStreamVolume(if(notification)AudioManager.STREAM_NOTIFICATION else AudioManager.STREAM_MUSIC)==0)return
        initialize(context.applicationContext)
        val pool=if(notification)noticePool else uiPool
        ids[event]?.let{pool?.play(it,.35f,.35f,1,0,1f)}
    }
}
