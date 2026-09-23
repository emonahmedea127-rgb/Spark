@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.spark.social

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Exercises the exact player factory used by feed and fullscreen stories. */
@RunWith(AndroidJUnit4::class)
class MediaPlaybackTest {
    @Test fun mp4DecodesRendersSeeksAndStops() = checkPlayback("playback.mp4")
    @Test fun webmDecodesRendersSeeksAndStops() = checkPlayback("playback.webm")

    private fun checkPlayback(asset:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=File(instrumentation.targetContext.cacheDir,asset)
        instrumentation.context.assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
        val frame=CountDownLatch(1)
        val finished=CountDownLatch(1)
        val error=AtomicReference<PlaybackException?>(null)
        var player:ExoPlayer?=null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    val p=createSparkPlayer(activity,MediaAccess(Uri.fromFile(file).toString(),emptyMap()))
                    player=p
                    activity.setContentView(PlayerView(activity).apply { this.player=p })
                    p.volume=0f
                    p.addListener(object:Player.Listener {
                        override fun onRenderedFirstFrame() { frame.countDown() }
                        override fun onPlaybackStateChanged(state:Int) { if(state==Player.STATE_ENDED)finished.countDown() }
                        override fun onPlayerError(e:PlaybackException) { error.set(e);frame.countDown() }
                    })
                    p.prepare();p.play()
                }
                assertTrue("No rendered video frame within 20 seconds",frame.await(20,TimeUnit.SECONDS))
                assertNull("Playback decoder error",error.get())
                val seek=CountDownLatch(1)
                scenario.onActivity {
                    assertTrue("Video has no decoded width",player!!.videoSize.width>0)
                    player!!.addListener(object:Player.Listener {
                        override fun onPositionDiscontinuity(oldPosition:Player.PositionInfo,newPosition:Player.PositionInfo,reason:Int) {
                            if(reason==Player.DISCONTINUITY_REASON_SEEK&&newPosition.positionMs>=1000)seek.countDown()
                        }
                        override fun onPlaybackStateChanged(state:Int) {
                            if(state==Player.STATE_READY&&player!!.currentPosition>=1000)seek.countDown()
                        }
                    })
                    player!!.pause()
                    assertFalse("Pause did not stop playback",player!!.playWhenReady)
                    player!!.seekTo(1500)
                    player!!.play()
                }
                assertTrue("Seek did not reach requested position",seek.await(15,TimeUnit.SECONDS))
                assertTrue("Video did not finish after seeking",finished.await(15,TimeUnit.SECONDS))
                scenario.onActivity {
                    player!!.pause()
                    assertFalse("Pause did not stop playback",player!!.playWhenReady)
                    assertNull("Playback error after seek",player!!.playerError)
                }
            } finally {
                scenario.onActivity { player?.release() }
                file.delete()
            }
        }
    }
}
