package com.spark.social

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Android WebRTC peers and RTP, synthetic media, isolated in-memory signaling. No user calls. */
@RunWith(AndroidJUnit4::class)
class TwoPeerCallTest {
    @Test fun audioTravelsBothWaysAndHangupStopsTracks() = checkCall(false)
    @Test fun audioAndVideoTravelBothWaysAndHangupStopsTracks() = checkCall(true)

    class Bridge(val dispatch:(String,String,Int)->Unit) {
        @JavascriptInterface fun send(command:String,payload:String,callback:Int) = dispatch(command,payload,callback)
    }
    class Probe(val receive:(String)->Unit) {
        @JavascriptInterface fun report(value:String) = receive(value)
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun checkCall(video:Boolean) {
        val connected=Array(2){CountDownLatch(1)}
        val ended=Array(2){CountDownLatch(1)}
        val latest=Array(2){java.util.concurrent.atomic.AtomicReference("No report")}
        val views=mutableListOf<WebView>()
        val call=JSONObject().put("status","ringing")
        val candidates=mutableListOf<Pair<Int,JSONObject>>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    val layout=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
                    activity.setContentView(layout)
                    val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(activity)).build()
                    repeat(2) { side ->
                        val view=WebView(activity);views.add(view)
                        layout.addView(view,LinearLayout.LayoutParams(-1,0,1f))
                        view.settings.javaScriptEnabled=true
                        view.settings.mediaPlaybackRequiresUserGesture=false
                        view.addJavascriptInterface(Probe { raw ->
                            latest[side].set(raw)
                            val value=JSONObject(raw)
                            if(value.optString("state")=="connected" && value.optLong("audio")>0 && (!video || value.optLong("video")>0))connected[side].countDown()
                            if(value.optBoolean("stopped") && value.optBoolean("tracksEnded"))ended[side].countDown()
                        },"SparkProbe")
                        view.addJavascriptInterface(Bridge { command,payload,id -> activity.runOnUiThread {
                            val value=JSONObject(payload)
                            val result=when(command) {
                                "relay" -> JSONObject().put("iceServers",JSONArray().put(JSONObject().put("urls","stun:127.0.0.1:9")))
                                "offer" -> {call.put("offer",value);JSONObject()}
                                "answer" -> {call.put("answer",value).put("status","accepted");JSONObject()}
                                "ice" -> {candidates.add(side to JSONObject().put("id",candidates.size+1).put("candidate",value));JSONObject()}
                                "poll" -> JSONObject().put("call",JSONObject(call.toString())).put("ice",JSONArray().apply {
                                    candidates.filter{it.first!=side && it.second.getLong("id")>value.optLong("after")}.forEach{put(it.second)}
                                })
                                "end","failed" -> {call.put("status","ended");JSONObject()}
                                else -> JSONObject()
                            }
                            view.evaluateJavascript("window.deliver($id,${JSONObject().put("result",result)});",null)
                        } },"SparkCall")
                        view.webChromeClient=object:WebChromeClient() {
                            override fun onConsoleMessage(message:ConsoleMessage):Boolean {
                                latest[side].set(message.message());return true
                            }
                        }
                        view.webViewClient=object:WebViewClient() {
                            override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse? = loader.shouldInterceptRequest(r.url)
                            override fun onPageFinished(v:WebView,url:String) {
                                v.evaluateJavascript("""
                                    (() => {
                                      const audioContext=new AudioContext();
                                      const oscillator=audioContext.createOscillator();
                                      const destination=audioContext.createMediaStreamDestination();
                                      oscillator.connect(destination);oscillator.start();audioContext.resume();
                                      const testStream=new MediaStream(destination.stream.getAudioTracks());
                                      const canvas=document.createElement('canvas');canvas.width=160;canvas.height=120;
                                      const ctx=canvas.getContext('2d');let frame=0;
                                      const painting=setInterval(()=>{ctx.fillStyle=(frame++%2)?'red':'blue';ctx.fillRect(0,0,160,120)},100);
                                      if($video)canvas.captureStream(10).getVideoTracks().forEach(t=>testStream.addTrack(t));
                                      navigator.mediaDevices.getUserMedia=async()=>testStream;
                                      window.boot({caller:${side==0},video:$video,id:'isolated-test',user:'$side'});
                                      const probing=setInterval(async()=>{
                                        let audio=0,video=0;
                                        if(pc && pc.connectionState!=='closed') {
                                          const stats=await pc.getStats();stats.forEach(s=>{
                                            if(s.type==='inbound-rtp'){if(s.kind==='audio'||s.mediaType==='audio')audio+=s.bytesReceived||0;if(s.kind==='video'||s.mediaType==='video')video+=s.bytesReceived||0;}
                                          });
                                        }
                                        SparkProbe.report(JSON.stringify({state:pc && pc.connectionState,audio,video,stopped,tracksEnded:testStream.getTracks().every(t=>t.readyState==='ended'),hint:document.getElementById('hint').textContent}));
                                        if(stopped){clearInterval(probing);clearInterval(painting);oscillator.stop();audioContext.close();}
                                      },250);
                                    })();
                                """.trimIndent(),null)
                            }
                        }
                        view.loadUrl("https://appassets.androidplatform.net/assets/call.html")
                    }
                }
                repeat(2){val ok=connected[it].await(45,TimeUnit.SECONDS);assertTrue("Peer $it media failed: ${latest[it].get()}",ok)}
                scenario.onActivity {views[0].evaluateJavascript("document.getElementById('end').click();",null)}
                repeat(2){assertTrue("Peer $it did not release tracks: ${latest[it].get()}",ended[it].await(10,TimeUnit.SECONDS))}
            } finally {
                scenario.onActivity {views.forEach{it.evaluateJavascript("window.stopMedia();",null);it.removeJavascriptInterface("SparkCall");it.removeJavascriptInterface("SparkProbe");it.destroy()}}
            }
        }
    }
}
