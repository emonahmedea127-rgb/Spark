package com.spark.social
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
/** Only bundled, origin-locked content can use this restricted signaling bridge. No token enters JS. */
class CallActivity:ComponentActivity() {
    private lateinit var api:SparkApi
    private var web:WebView?=null
    private lateinit var callId:String
    private var caller=false
    private var video=false
    private var ended=false
    private var audio:AudioManager?=null
    private var audioFocus:AudioFocusRequest?=null
    private var priorMode=AudioManager.MODE_NORMAL
    private var priorSpeaker=false
    private var speaker=false
    private fun routeAudio():Boolean {
        val manager=getSystemService(AudioManager::class.java)
        audio=manager;priorMode=manager.mode;priorSpeaker=manager.isSpeakerphoneOn
        volumeControlStream=AudioManager.STREAM_VOICE_CALL
        val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener{change->if(change==AudioManager.AUDIOFOCUS_LOSS)runOnUiThread{finish()}}.build()
        audioFocus=focus
        if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return false
        manager.mode=AudioManager.MODE_IN_COMMUNICATION;speaker=video;manager.isSpeakerphoneOn=speaker
        return true
    }
    private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        grants->
        if(grants.values.all {
            it
        })openWeb()else {
            Toast.makeText(this,"Microphone/camera permission is needed for this call.",Toast.LENGTH_LONG).show()
            finish()
        }
    }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        api=SparkApi.get(this)
        callId=intent.getStringExtra("call_id").orEmpty()
        caller=intent.getBooleanExtra("caller",false)
        video=intent.getBooleanExtra("video",false)
        if(runCatching {
            UUID.fromString(callId)
        }.isFailure||!api.signedIn) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
        val permissions=if(video)arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA)else arrayOf(Manifest.permission.RECORD_AUDIO)
        if(permissions.all {
            checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED
        })openWeb()else permissionLauncher.launch(permissions)
    }
    @SuppressLint("SetJavaScriptEnabled") private fun openWeb() {
        if(!routeAudio()){Toast.makeText(this,"Audio is in use. Try again after the other call ends.",Toast.LENGTH_LONG).show();finish();return}
        val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(this)).build()
        val view=WebView(this)
        web=view
        view.settings.javaScriptEnabled=true
        view.settings.mediaPlaybackRequiresUserGesture=false
        view.settings.allowFileAccess=false
        view.settings.allowContentAccess=false
        view.addJavascriptInterface(Bridge(),"SparkCall")
        view.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=true
            override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse? {
                if(r.url.host!="appassets.androidplatform.net")return WebResourceResponse("text/plain","UTF-8",403,"Forbidden",emptyMap(),"".byteInputStream())
                return loader.shouldInterceptRequest(r.url)
            }
            override fun onPageFinished(v:WebView,url:String) {
                if(url=="https://appassets.androidplatform.net/assets/call.html")v.evaluateJavascript("window.boot(${json("id" to callId,"caller" to caller,"video" to video,"user" to api.userId)});",null)
            }
        }
        view.webChromeClient=object:WebChromeClient() {
            override fun onPermissionRequest(request:PermissionRequest) {
                if(request.origin.toString().trimEnd('/')!="https://appassets.androidplatform.net") {
                    request.deny()
                    return
                }
                val granted=request.resources.filter {
                    (it==PermissionRequest.RESOURCE_AUDIO_CAPTURE&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)||(it==PermissionRequest.RESOURCE_VIDEO_CAPTURE&&video&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
                }
                if(granted.isEmpty())request.deny()else request.grant(granted.toTypedArray())
            }
        }
        setContentView(view)
        view.loadUrl("https://appassets.androidplatform.net/assets/call.html")
    }
    inner class Bridge {
        @JavascriptInterface fun send(command:String,payload:String,callback:Int) {
            lifecycleScope.launch {
                try {
                    val value=JSONObject(payload)
                    val result:Any=when(command) {
                        "poll"->json("call" to api.rows("calls","id=eq.$callId").firstOrNull(),"ice" to JSONArray(api.rows("ice","call_id=eq.$callId&user_id=neq.${api.userId}&id=gt.${value.optLong("after",0).coerceAtLeast(0)}&order=id.asc&limit=200")))
                        "offer"-> {
                            check(caller)
                            api.update("calls","id=eq.$callId",json("offer" to value))
                            json("ok" to true)
                        }
                        "answer"-> {
                            check(!caller)
                            api.update("calls","id=eq.$callId",json("answer" to value,"status" to "accepted"))
                            json("ok" to true)
                        }
                        "ice"-> {
                            api.insert("ice",json("call_id" to callId,"user_id" to api.userId,"candidate" to value))
                            json("ok" to true)
                        }
                        "speaker"-> {speaker=!speaker;audio?.isSpeakerphoneOn=speaker;json("speaker" to speaker)}
                        "end"-> {
                            endCall()
                            json("ok" to true)
                        }
                        else->error("Unsupported call command")
                    }
                    web?.evaluateJavascript("window.deliver($callback,${json("result" to result)});",null)
                    if(command=="end")finish()
                }catch(e:CancellationException) {
                    throw e
                }catch(e:Exception) {
                    web?.evaluateJavascript("window.deliver($callback,${json("error" to (e.message?:"Call failed"))});",null)
                }
            }
        }
    }
    private suspend fun endCall() {
        if(!ended) {
            ended=true
            runCatching {
                api.update("calls","id=eq.$callId",json("status" to "ended"))
            }
        }
    }
    override fun onStop() {
        super.onStop()
        if(web!=null&&!isChangingConfigurations)finish()
    }
    override fun onDestroy() {
        web?.evaluateJavascript("window.stopMedia && window.stopMedia();",null)
        web?.removeJavascriptInterface("SparkCall")
        web?.destroy()
        web=null
        if(::api.isInitialized&&::callId.isInitialized&&!ended)CoroutineScope(Dispatchers.IO).launch {
            withTimeoutOrNull(10000) {
                endCall()
            }
        }
        audio?.let{manager->audioFocus?.let{manager.abandonAudioFocusRequest(it)};manager.isSpeakerphoneOn=priorSpeaker;manager.mode=priorMode}
        super.onDestroy()
    }
}
