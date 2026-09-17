package com.webgenius.spark

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.webrtc.*

/** Foreground-only peer calls. No paid relay, no hidden recording, no server media upload. */
class CallEngine(context: Context, video: Boolean, private val event: (String) -> Unit) {
 val egl: EglBase = EglBase.create()
 var remoteVideo: VideoTrack? = null; private set
 var localVideo: VideoTrack? = null; private set
 private val audioManager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
 private val previousMode=audioManager.mode
 private val previousSpeaker=audioManager.isSpeakerphoneOn
 private val factory: PeerConnectionFactory
 private val audioSource: AudioSource
 private val audioTrack: AudioTrack
 private var videoSource: VideoSource?=null
 private var capturer: CameraVideoCapturer?=null
 private var helper: SurfaceTextureHelper?=null
 private val gathered=CompletableDeferred<Unit>()
 private val peer: PeerConnection
 private var closed=false
 init {
  PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
  factory=PeerConnectionFactory.builder().setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext,true,true))
   .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext)).createPeerConnectionFactory()
  audioManager.mode=AudioManager.MODE_IN_COMMUNICATION
  audioManager.isSpeakerphoneOn=video
  val config=PeerConnection.RTCConfiguration(listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()))
  config.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN
  peer=requireNotNull(factory.createPeerConnection(config,object: PeerConnection.Observer {
   override fun onSignalingChange(s: PeerConnection.SignalingState) {}
   override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {
    event(when(s) { PeerConnection.IceConnectionState.CONNECTED,PeerConnection.IceConnectionState.COMPLETED -> "Connected"
     PeerConnection.IceConnectionState.FAILED -> "Connection failed. This network may need a relay server."
     PeerConnection.IceConnectionState.DISCONNECTED -> "Connection interrupted"
     else -> "Connecting…" })
   }
   override fun onIceConnectionReceivingChange(receiving: Boolean) {}
   override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) { if(s==PeerConnection.IceGatheringState.COMPLETE) gathered.complete(Unit) }
   override fun onIceCandidate(c: IceCandidate) {}
   override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
   override fun onAddStream(s: MediaStream) {}
   override fun onRemoveStream(s: MediaStream) {}
   override fun onDataChannel(c: DataChannel) {}
   override fun onRenegotiationNeeded() {}
   override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
    remoteVideo=receiver.track() as? VideoTrack; event("Connecting…")
   }
  }))
  audioSource=factory.createAudioSource(MediaConstraints())
  audioTrack=factory.createAudioTrack("spark-audio",audioSource)
  peer.addTrack(audioTrack,listOf("spark"))
  if(video) {
   val cameras=Camera2Enumerator(context)
   val name=cameras.deviceNames.firstOrNull { cameras.isFrontFacing(it) } ?: cameras.deviceNames.firstOrNull()
   if(name!=null) {
    capturer=cameras.createCapturer(name,null)
    videoSource=factory.createVideoSource(false)
    helper=SurfaceTextureHelper.create("SparkCamera",egl.eglBaseContext)
    capturer?.initialize(helper,context,videoSource!!.capturerObserver)
    capturer?.startCapture(640,480,24)
    localVideo=factory.createVideoTrack("spark-video",videoSource)
    peer.addTrack(localVideo,listOf("spark"))
   }
  }
 }
 private suspend fun description(offer: Boolean): SessionDescription {
  val result=CompletableDeferred<SessionDescription>()
  val observer=object: SdpObserver {
   override fun onCreateSuccess(s: SessionDescription) { result.complete(s) }
   override fun onCreateFailure(s: String) { result.completeExceptionally(IllegalStateException(s)) }
   override fun onSetSuccess() {}
   override fun onSetFailure(s: String) {}
  }
  if(offer) peer.createOffer(observer,MediaConstraints()) else peer.createAnswer(observer,MediaConstraints())
  return withTimeout(15_000) { result.await() }
 }
 private suspend fun set(sdp: SessionDescription,local: Boolean) {
  val result=CompletableDeferred<Unit>()
  val observer=object: SdpObserver {
   override fun onSetSuccess() { result.complete(Unit) }
   override fun onSetFailure(s: String) { result.completeExceptionally(IllegalStateException(s)) }
   override fun onCreateSuccess(s: SessionDescription) {}
   override fun onCreateFailure(s: String) {}
  }
  if(local) peer.setLocalDescription(observer,sdp) else peer.setRemoteDescription(observer,sdp)
  withTimeout(15_000) { result.await() }
 }
 private suspend fun localDescription(offer: Boolean): String {
  set(description(offer),true)
  // Send complete ICE candidates in SDP; no unbounded signaling insert stream.
  withTimeout(15_000) { gathered.await() }
  return peer.localDescription.description
 }
 suspend fun offer()=localDescription(true)
 suspend fun answer(offer: String): String { set(SessionDescription(SessionDescription.Type.OFFER,offer),false);return localDescription(false) }
 suspend fun receiveAnswer(answer: String) { set(SessionDescription(SessionDescription.Type.ANSWER,answer),false) }
 fun mute(value: Boolean) { audioTrack.setEnabled(!value) }
 fun camera(value: Boolean) { localVideo?.setEnabled(value) }
 fun speaker(value: Boolean) { audioManager.isSpeakerphoneOn=value }
 fun close() {
  if(closed) return;closed=true
  audioTrack.setEnabled(false);localVideo?.setEnabled(false)
  runCatching { capturer?.stopCapture() };capturer?.dispose();peer.close();peer.dispose()
  localVideo?.dispose();videoSource?.dispose();helper?.dispose();audioTrack.dispose();audioSource.dispose();factory.dispose()
  audioManager.mode=previousMode;audioManager.isSpeakerphoneOn=previousSpeaker
  egl.release()
 }
}
