package com.example.sociva.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class CallActivity : ComponentActivity() {
  companion object {
    private const val EXTRA_CALL_ID = "call_id"
    private const val EXTRA_CONVERSATION_ID = "conversation_id"
    private const val EXTRA_OTHER_USER_ID = "other_user_id"
    private const val EXTRA_OTHER_NAME = "other_name"
    private const val EXTRA_VIDEO = "video"
    private const val EXTRA_INCOMING = "incoming"

    fun outgoingIntent(context: Context, conversationId: String, calleeId: String, name: String, video: Boolean) =
      Intent(context, CallActivity::class.java)
        .putExtra(EXTRA_CONVERSATION_ID, conversationId)
        .putExtra(EXTRA_OTHER_USER_ID, calleeId)
        .putExtra(EXTRA_OTHER_NAME, name)
        .putExtra(EXTRA_VIDEO, video)
        .putExtra(EXTRA_INCOMING, false)

    fun incomingIntent(context: Context, call: SparkCall) =
      Intent(context, CallActivity::class.java)
        .putExtra(EXTRA_CALL_ID, call.id)
        .putExtra(EXTRA_CONVERSATION_ID, call.conversationId)
        .putExtra(EXTRA_OTHER_USER_ID, call.callerId)
        .putExtra(EXTRA_OTHER_NAME, "Incoming call")
        .putExtra(EXTRA_VIDEO, call.video)
        .putExtra(EXTRA_INCOMING, true)
  }

  private val egl = EglBase.create()
  private lateinit var status: TextView
  private lateinit var remoteView: SurfaceViewRenderer
  private lateinit var localView: SurfaceViewRenderer
  private lateinit var controls: LinearLayout
  private var peerFactory: PeerConnectionFactory? = null
  private var peer: PeerConnection? = null
  private var audioSource: AudioSource? = null
  private var audioTrack: AudioTrack? = null
  private var videoSource: VideoSource? = null
  private var videoTrack: VideoTrack? = null
  private var capturer: VideoCapturer? = null
  private var surfaceHelper: SurfaceTextureHelper? = null
  private var callId: String? = null
  private var pollJob: Job? = null
  private var lastIceId = 0L
  private var remoteDescriptionSet = false
  private var ending = false

  private val incoming by lazy { intent.getBooleanExtra(EXTRA_INCOMING, false) }
  private val wantsVideo by lazy { intent.getBooleanExtra(EXTRA_VIDEO, false) }
  private val conversationId by lazy { intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty() }
  private val otherUserId by lazy { intent.getStringExtra(EXTRA_OTHER_USER_ID).orEmpty() }
  private val otherName by lazy { intent.getStringExtra(EXTRA_OTHER_NAME) ?: "Call" }

  private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
    val audioOk = result[Manifest.permission.RECORD_AUDIO] ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    val videoOk = !wantsVideo || (result[Manifest.permission.CAMERA] ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED))
    if (audioOk && videoOk) lifecycleScope.launch { if (incoming) acceptIncoming() else startOutgoing() }
    else status.text = "Microphone${if (wantsVideo) " and camera" else ""} permission required"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    callId = intent.getStringExtra(EXTRA_CALL_ID)
    buildUi()
    configureAudio()
    if (incoming) showIncomingControls() else requestPermissionsAndStart()
  }

  private fun buildUi() {
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.rgb(13, 18, 32))
    }
    status = TextView(this).apply {
      text = if (incoming) "Incoming ${if (wantsVideo) "video" else "audio"} call" else "Preparing call..."
      setTextColor(Color.WHITE)
      textSize = 19f
      gravity = Gravity.CENTER
      setPadding(16, 36, 16, 24)
    }
    root.addView(status, LinearLayout.LayoutParams(-1, -2))

    val videoFrame = FrameLayout(this)
    remoteView = SurfaceViewRenderer(this).apply {
      init(egl.eglBaseContext, null)
      setMirror(false)
    }
    localView = SurfaceViewRenderer(this).apply {
      init(egl.eglBaseContext, null)
      setMirror(true)
      setZOrderMediaOverlay(true)
    }
    videoFrame.addView(remoteView, FrameLayout.LayoutParams(-1, -1))
    videoFrame.addView(localView, FrameLayout.LayoutParams(320, 430, Gravity.TOP or Gravity.END).apply { setMargins(16, 16, 16, 16) })
    localView.visibility = if (wantsVideo) View.VISIBLE else View.GONE
    remoteView.visibility = if (wantsVideo) View.VISIBLE else View.INVISIBLE
    root.addView(videoFrame, LinearLayout.LayoutParams(-1, 0, 1f))

    controls = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER
      setPadding(16, 20, 16, 36)
    }
    root.addView(controls, LinearLayout.LayoutParams(-1, -2))
    setContentView(root)
    if (!incoming) showInCallControls()
  }

  private fun button(text: String, color: Int, action: () -> Unit) = Button(this).apply {
    this.text = text
    setTextColor(Color.WHITE)
    setBackgroundColor(color)
    setOnClickListener { action() }
    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(10, 0, 10, 0) }
  }

  private fun showIncomingControls() {
    controls.removeAllViews()
    controls.addView(button("Decline", Color.rgb(220, 55, 70)) {
      lifecycleScope.launch {
        callId?.let { runCatching { CallSignalingApi.updateCall(it, JSONObject().put("status", "declined")) } }
        ending = true
        finish()
      }
    })
    controls.addView(button("Accept", Color.rgb(30, 180, 95)) { requestPermissionsAndStart() })
  }

  private fun showInCallControls() {
    controls.removeAllViews()
    controls.addView(button("Mute", Color.rgb(70, 80, 105)) {
      audioTrack?.setEnabled(!(audioTrack?.enabled() ?: true))
    })
    if (wantsVideo) controls.addView(button("Camera", Color.rgb(70, 80, 105)) {
      videoTrack?.setEnabled(!(videoTrack?.enabled() ?: true))
    })
    controls.addView(button("End", Color.rgb(220, 55, 70)) { endCallAndFinish() })
  }

  private fun requestPermissionsAndStart() {
    val needed = buildList {
      if (ContextCompat.checkSelfPermission(this@CallActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
      if (wantsVideo && ContextCompat.checkSelfPermission(this@CallActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.CAMERA)
    }
    if (needed.isEmpty()) lifecycleScope.launch { if (incoming) acceptIncoming() else startOutgoing() }
    else permissionLauncher.launch(needed.toTypedArray())
  }

  private suspend fun startOutgoing() {
    try {
      val call = CallSignalingApi.createOutgoing(conversationId, otherUserId, wantsVideo)
      callId = call.id
      status.text = "Calling $otherName..."
      preparePeer(call.id)
      val offer = createOffer()
      CallSignalingApi.updateCall(call.id, JSONObject().put("offer", sdpJson(offer)))
      startPolling(isCaller = true)
      showInCallControls()
    } catch (t: Throwable) {
      status.text = t.message ?: "Unable to start call"
    }
  }

  private suspend fun acceptIncoming() {
    val id = callId ?: return
    try {
      controls.removeAllViews()
      status.text = "Connecting..."
      val call = waitForOffer(id) ?: error("Call ended")
      preparePeer(id)
      setRemote(call.offer ?: error("Missing offer"))
      remoteDescriptionSet = true
      val answer = createAnswer()
      CallSignalingApi.updateCall(id, JSONObject().put("answer", sdpJson(answer)).put("status", "accepted"))
      status.text = "Connected"
      startPolling(isCaller = false)
      showInCallControls()
    } catch (t: Throwable) {
      status.text = t.message ?: "Unable to answer call"
    }
  }

  private suspend fun waitForOffer(id: String): SparkCall? {
    repeat(30) {
      val call = CallSignalingApi.getCall(id) ?: return null
      if (call.status in listOf("declined", "ended")) return null
      if (call.offer != null) return call
      delay(500)
    }
    return null
  }

  private suspend fun preparePeer(id: String) {
    PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions())
    val factory = PeerConnectionFactory.builder()
      .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
      .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
      .createPeerConnectionFactory()
    peerFactory = factory

    audioSource = factory.createAudioSource(MediaConstraints())
    audioTrack = factory.createAudioTrack("spark_audio", audioSource).also { it.setEnabled(true) }

    if (wantsVideo) {
      val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(this)) Camera2Enumerator(this) else Camera1Enumerator(true)
      val device = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull()
      capturer = device?.let { enumerator.createCapturer(it, null) }
      videoSource = factory.createVideoSource(false)
      surfaceHelper = SurfaceTextureHelper.create("SparkCamera", egl.eglBaseContext)
      capturer?.initialize(surfaceHelper, applicationContext, videoSource?.capturerObserver)
      capturer?.startCapture(720, 1280, 24)
      videoTrack = factory.createVideoTrack("spark_video", videoSource).also { it.addSink(localView) }
    }

    val rtc = PeerConnection.RTCConfiguration(parseIceServers(CallSignalingApi.getIceServers(id))).apply {
      sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
      continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    }
    peer = factory.createPeerConnection(rtc, object : PeerConnection.Observer {
      override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
      override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        runOnUiThread {
          status.text = when (newState) {
            PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> "Connected"
            PeerConnection.IceConnectionState.FAILED -> "Connection failed"
            PeerConnection.IceConnectionState.DISCONNECTED -> "Reconnecting..."
            else -> status.text
          }
        }
      }
      override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
      override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit
      override fun onIceCandidate(candidate: IceCandidate?) {
        if (candidate == null) return
        lifecycleScope.launch {
          runCatching {
            CallSignalingApi.sendIce(id, JSONObject().put("sdpMid", candidate.sdpMid).put("sdpMLineIndex", candidate.sdpMLineIndex).put("candidate", candidate.sdp))
          }
        }
      }
      override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
      override fun onAddStream(stream: MediaStream?) = Unit
      override fun onRemoveStream(stream: MediaStream?) = Unit
      override fun onDataChannel(dataChannel: DataChannel?) = Unit
      override fun onRenegotiationNeeded() = Unit
      override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        (receiver?.track() as? VideoTrack)?.addSink(remoteView)
      }
    }) ?: error("Unable to create peer connection")

    audioTrack?.let { peer?.addTrack(it, listOf("spark_stream")) }
    videoTrack?.let { peer?.addTrack(it, listOf("spark_stream")) }
  }

  private fun parseIceServers(arr: JSONArray): List<PeerConnection.IceServer> = buildList {
    for (i in 0 until arr.length()) {
      val o = arr.getJSONObject(i)
      val raw = o.get("urls")
      val urls = if (raw is JSONArray) List(raw.length()) { idx -> raw.getString(idx) } else listOf(raw.toString())
      val builder = PeerConnection.IceServer.builder(urls)
      o.optString("username").takeIf { it.isNotBlank() }?.let { builder.setUsername(it) }
      o.optString("credential").takeIf { it.isNotBlank() }?.let { builder.setPassword(it) }
      add(builder.createIceServer())
    }
  }

  private suspend fun createOffer(): SessionDescription = createLocalSdp { observer -> peer?.createOffer(observer, MediaConstraints()) }
  private suspend fun createAnswer(): SessionDescription = createLocalSdp { observer -> peer?.createAnswer(observer, MediaConstraints()) }

  private suspend fun createLocalSdp(start: (SdpObserver) -> Unit): SessionDescription = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    start(object : SdpObserver {
      override fun onCreateSuccess(sdp: SessionDescription?) {
        if (sdp == null) {
          if (cont.isActive) cont.resumeWith(Result.failure(IllegalStateException("SDP failed")))
          return
        }
        peer?.setLocalDescription(object : SimpleSdpObserver() {
          override fun onSetSuccess() { if (cont.isActive) cont.resumeWith(Result.success(sdp)) }
          override fun onSetFailure(error: String?) { if (cont.isActive) cont.resumeWith(Result.failure(IllegalStateException(error ?: "SDP set failed"))) }
        }, sdp)
      }
      override fun onSetSuccess() = Unit
      override fun onCreateFailure(error: String?) { if (cont.isActive) cont.resumeWith(Result.failure(IllegalStateException(error ?: "SDP failed"))) }
      override fun onSetFailure(error: String?) = Unit
    })
  }

  private suspend fun setRemote(json: JSONObject) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
    val type = if (json.optString("type").equals("answer", true)) SessionDescription.Type.ANSWER else SessionDescription.Type.OFFER
    peer?.setRemoteDescription(object : SimpleSdpObserver() {
      override fun onSetSuccess() { if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
      override fun onSetFailure(error: String?) { if (cont.isActive) cont.resumeWith(Result.failure(IllegalStateException(error ?: "Remote SDP failed"))) }
    }, SessionDescription(type, json.getString("sdp")))
  }

  private fun sdpJson(sdp: SessionDescription) = JSONObject().put("type", sdp.type.canonicalForm()).put("sdp", sdp.description)

  private fun startPolling(isCaller: Boolean) {
    val id = callId ?: return
    pollJob?.cancel()
    pollJob = lifecycleScope.launch {
      while (isActive) {
        try {
          val call = CallSignalingApi.getCall(id) ?: break
          if (call.status in listOf("declined", "ended")) {
            status.text = if (call.status == "declined") "Call declined" else "Call ended"
            ending = true
            delay(500)
            finish()
            break
          }
          if (isCaller && !remoteDescriptionSet && call.answer != null) {
            setRemote(call.answer)
            remoteDescriptionSet = true
            status.text = "Connected"
          }
          for (item in CallSignalingApi.getRemoteIce(id, lastIceId)) {
            lastIceId = maxOf(lastIceId, item.id)
            val c = item.candidate
            peer?.addIceCandidate(IceCandidate(c.optString("sdpMid"), c.optInt("sdpMLineIndex"), c.getString("candidate")))
          }
        } catch (_: Throwable) { }
        delay(700)
      }
    }
  }

  private fun configureAudio() {
    val am = getSystemService(AUDIO_SERVICE) as AudioManager
    am.mode = AudioManager.MODE_IN_COMMUNICATION
    am.isSpeakerphoneOn = wantsVideo
  }

  private fun endCallAndFinish() {
    if (ending) { finish(); return }
    ending = true
    lifecycleScope.launch {
      callId?.let { runCatching { CallSignalingApi.updateCall(it, JSONObject().put("status", "ended")) } }
      finish()
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() { endCallAndFinish() }

  override fun onDestroy() {
    pollJob?.cancel()
    runCatching { capturer?.stopCapture() }
    capturer?.dispose()
    surfaceHelper?.dispose()
    videoTrack?.dispose()
    videoSource?.dispose()
    audioTrack?.dispose()
    audioSource?.dispose()
    peer?.dispose()
    peerFactory?.dispose()
    remoteView.release()
    localView.release()
    egl.release()
    val am = getSystemService(AUDIO_SERVICE) as AudioManager
    am.mode = AudioManager.MODE_NORMAL
    super.onDestroy()
  }
}

open class SimpleSdpObserver : SdpObserver {
  override fun onCreateSuccess(sdp: SessionDescription?) = Unit
  override fun onSetSuccess() = Unit
  override fun onCreateFailure(error: String?) = Unit
  override fun onSetFailure(error: String?) = Unit
}
