package com.spark.social

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

data class MediaAccess(val url:String,val headers:Map<String,String>)

@Composable fun PrivateImage(
    vm:SparkViewModel,path:String,modifier:Modifier=Modifier,
    contentScale:ContentScale=ContentScale.Crop,videoFrame:Boolean=false,onReady:()->Unit={},targetPx:Int=960,onRatio:(Float)->Unit={}
) {
    // Never download an entire video just to draw a scrolling thumbnail.
    if(videoFrame) {
        Box(modifier.background(Color(0xFF20242B)),contentAlignment=Alignment.Center) {
            Icon(Icons.Outlined.PlayCircle,"Video",tint=Color.White.copy(alpha=.7f),modifier=Modifier.size(40.dp))
        };return
    }
    var bytes by remember(path,vm.api.userId) { mutableStateOf<ByteArray?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    var loading by remember(path) { mutableStateOf(true) }
    var attempt by remember(path) { mutableIntStateOf(0) }
    val ready by rememberUpdatedState(onReady)
    val ratioReady by rememberUpdatedState(onRatio)
    LaunchedEffect(path,attempt,vm.api.userId) {
        loading=true;failed=false
        try { bytes=FastImages.bytes(vm.api,path) }
        catch(e:CancellationException) { throw e }
        catch(_:Exception) { failed=true;loading=false }
    }
    val context=LocalContext.current
    val loader=remember(context) { FastImages.loader(context) }
    val request=remember(bytes,targetPx) {
        bytes?.let { data -> ImageRequest.Builder(context).data(java.nio.ByteBuffer.wrap(data))
            .memoryCacheKey("${vm.api.userId}:$path:${data.hashCode()}:$targetPx")
            .size(targetPx,targetPx).diskCachePolicy(CachePolicy.DISABLED).memoryCachePolicy(CachePolicy.ENABLED).build() }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)),contentAlignment=Alignment.Center) {
        if(request!=null&&!failed)key(attempt) { AsyncImage(model=request,imageLoader=loader,contentDescription="Shared photo",
            contentScale=contentScale,modifier=Modifier.fillMaxSize(),
            onSuccess={result->loading=false;val image=result.result.drawable;if(image.intrinsicHeight>0)ratioReady(image.intrinsicWidth.toFloat()/image.intrinsicHeight);ready()},onError={loading=false;failed=true}) }
        if(loading)Icon(Icons.Outlined.Image,null,tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.25f),modifier=Modifier.size(28.dp))
        if(failed)TextButton(onClick={bytes=null;attempt++}) { Icon(Icons.Outlined.Refresh,null);Text(" Reload") }
    }
}

@Composable fun Media(vm:SparkViewModel,path:String,type:String,modifier:Modifier=Modifier,post:JSONObject?=null) {
    if(path.isBlank())return
    var open by rememberSaveable(path) { mutableStateOf(false) }
    var ratio by remember(path) { mutableFloatStateOf(1f) }
    Box(modifier.fillMaxWidth().then(if(type=="video")Modifier.height(280.dp) else Modifier.aspectRatio(ratio)).clickable { open=true }) {
        PrivateImage(vm,path,Modifier.fillMaxSize(),videoFrame=type=="video",onRatio={ratio=it.coerceIn(.55f,2f)})
        if(type=="video") {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.5f)))))
            Surface(Modifier.align(Alignment.Center).size(68.dp),shape=CircleShape,color=Color.Black.copy(alpha=.45f)) {
                Box(contentAlignment=Alignment.Center) { Icon(Icons.Outlined.PlayArrow,"Play video",Modifier.size(44.dp),tint=Color.White) }
            }
            Text("WATCH VIDEO",Modifier.align(Alignment.BottomStart).padding(18.dp),color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
        }else {
            Surface(Modifier.align(Alignment.BottomEnd).padding(12.dp),color=Color.Black.copy(alpha=.45f),shape=CircleShape) {
                Icon(Icons.Outlined.Fullscreen,"View full photo",Modifier.padding(8.dp).size(20.dp),tint=Color.White)
            }
        }
    }
    if(open) { if(type=="video")VideoDialog(vm,path) { open=false } else PhotoDialog(vm,path,post) { open=false } }
}

@Composable internal fun FullscreenMedia(onClose:()->Unit,content:@Composable BoxScope.()->Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        DisposableEffect(view) {
            val window=(view.parent as? DialogWindowProvider)?.window
            val controller=window?.let { WindowCompat.getInsetsController(it,view) }
            controller?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        Box(Modifier.fillMaxSize().background(Color.Black),content=content)
    }
}

@Composable fun PhotoDialog(vm:SparkViewModel,path:String,post:JSONObject?=null,onClose:()->Unit) {
    FullscreenMedia(onClose) {
        var scale by remember(path) { mutableFloatStateOf(1f) }
        var pan by remember(path) { mutableStateOf(Offset.Zero) }
        var bounds by remember { mutableStateOf(Offset.Zero) }
        val transform=rememberTransformableState { zoom,offset,_ ->
            scale=(scale*zoom).coerceIn(1f,5f)
            val limitX=bounds.x*(scale-1)/2;val limitY=bounds.y*(scale-1)/2
            pan=if(scale==1f)Offset.Zero else Offset((pan.x+offset.x).coerceIn(-limitX,limitX),(pan.y+offset.y).coerceIn(-limitY,limitY))
        }
        BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))) {
            val density=androidx.compose.ui.platform.LocalDensity.current
            SideEffect { with(density) { bounds=Offset(maxWidth.toPx(),maxHeight.toPx()) } }
            PrivateImage(vm,path,Modifier.fillMaxSize()
                .graphicsLayer { scaleX=scale;scaleY=scale;translationX=pan.x;translationY=pan.y }
                .transformable(transform).pointerInput(path) {
                    detectTapGestures(onDoubleTap={scale=if(scale>1f)1f else 2.5f;pan=Offset.Zero})
                },contentScale=ContentScale.Fit,targetPx=2048)
        }
        ViewerClose("Photo",onClose)
        if(post==null)Text("Pinch to zoom · Double tap to reset",Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),color=Color.White.copy(alpha=.8f),fontSize=12.sp)
        else Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.9f)))).navigationBarsPadding().padding(top=24.dp)) {
            Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Avatar(vm,post.child("author"),40)
                Column(Modifier.padding(start=10.dp)) { Text(post.child("author").s("display_name"),color=Color.White,fontWeight=FontWeight.SemiBold);Text(ago(post.s("created_at")),color=Color.LightGray,fontSize=12.sp) }
            }
            if(post.s("body").isNotBlank())Text(post.s("body"),Modifier.padding(16.dp),color=Color.White,maxLines=3)
            PostActions(vm,post,white=true,onComments={onClose();vm.go("Comments",post.id())})
        }
    }
}

@Composable private fun BoxScope.ViewerClose(title:String,onClose:()->Unit) {
    Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.7f),Color.Transparent))).statusBarsPadding().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick=onClose) { Icon(Icons.Outlined.Close,"Close viewer",tint=Color.White) }
        Text(title,color=Color.White,fontWeight=FontWeight.SemiBold)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createSparkPlayer(context:Context,access:MediaAccess):ExoPlayer {
    val http=DefaultHttpDataSource.Factory().setDefaultRequestProperties(access.headers)
        .setUserAgent("Spark/1.1 Android").setConnectTimeoutMs(20000).setReadTimeoutMs(30000)
    return ExoPlayer.Builder(context,DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context,http)))
        .build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(access.url))
        }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun SparkVideo(
    vm:SparkViewModel,path:String,modifier:Modifier=Modifier,
    onProgress:(Float)->Unit={},onEnded:()->Unit={},paused:Boolean=false,loop:Boolean=false,controls:Boolean=true
) {
    var access by remember(path) { mutableStateOf<MediaAccess?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var attempt by remember(path) { mutableIntStateOf(0) }
    var resumeAt by rememberSaveable(path) { mutableLongStateOf(0L) }
    val progress by rememberUpdatedState(onProgress)
    val ended by rememberUpdatedState(onEnded)
    LaunchedEffect(path,attempt) {
        access=null;error=null
        try { access=vm.api.mediaAccess(path) }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { error=e.message?:"Cannot load this video." }
    }
    Box(modifier,contentAlignment=Alignment.Center) {
        val source=access
        if(source!=null) {
            val context=LocalContext.current
            val lifecycle=LocalLifecycleOwner.current.lifecycle
            val player=remember(source,attempt) { createSparkPlayer(context,source) }
            var buffering by remember(player) { mutableStateOf(true) }
            var wasPlaying by remember(player) { mutableStateOf(true) }
            val pauseNow by rememberUpdatedState(paused)
            DisposableEffect(player,lifecycle) {
                val listener=object:Player.Listener {
                    override fun onPlaybackStateChanged(state:Int) {
                        buffering=state==Player.STATE_BUFFERING
                        if(state==Player.STATE_ENDED)ended()
                    }
                    override fun onPlayerError(e:PlaybackException) {
                        resumeAt=player.currentPosition
                        val httpError=generateSequence<Throwable>(e) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                        if(httpError?.responseCode in listOf(401,403)&&attempt<1)attempt++
                        else error=when(e.errorCode) {
                            PlaybackException.ERROR_CODE_DECODING_FAILED,PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED->"This video's encoding isn't supported on this device. Try an MP4 with H.264 video and AAC audio."
                            else->"Video couldn't play. Check your connection and retry. (${e.errorCodeName})"
                        }
                    }
                }
                val observer=LifecycleEventObserver { _,event ->
                    if(event==Lifecycle.Event.ON_STOP) { wasPlaying=player.playWhenReady;player.pause() }
                    if(event==Lifecycle.Event.ON_START&&wasPlaying&&!pauseNow)player.play()
                }
                player.addListener(listener);lifecycle.addObserver(observer)
                player.repeatMode=if(loop)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                player.seekTo(resumeAt);player.prepare()
                player.playWhenReady=!paused&&lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                onDispose {
                    resumeAt=player.currentPosition
                    lifecycle.removeObserver(observer);player.removeListener(listener);player.release()
                }
            }
            LaunchedEffect(paused,player) { if(paused)player.pause() else if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))player.play() }
            LaunchedEffect(player) {
                while(true) {
                    if(player.duration>0)progress((player.currentPosition.toFloat()/player.duration).coerceIn(0f,1f))
                    delay(100)
                }
            }
            AndroidView(factory={ctx->PlayerView(ctx).apply {
                this.player=player
                useController=controls
                resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                setShowNextButton(false);setShowPreviousButton(false)
                keepScreenOn=true
            }},update={it.player=player},modifier=Modifier.fillMaxSize())
            if(buffering&&error==null)CircularProgressIndicator(color=Color.White)
        }else if(error==null)CircularProgressIndicator(color=Color.White)
        error?.let { message ->
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha=.9f)).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.ErrorOutline,null,tint=Color.White,modifier=Modifier.size(36.dp))
                Text(message,Modifier.padding(vertical=16.dp),color=Color.White,textAlign=TextAlign.Center)
                Button(onClick={attempt++}) { Icon(Icons.Outlined.Refresh,null);Text(" Retry video") }
            }
        }
    }
}

@Composable fun VideoDialog(vm:SparkViewModel,path:String,onClose:()->Unit) {
    FullscreenMedia(onClose) {
        SparkVideo(vm,path,Modifier.fillMaxSize().navigationBarsPadding())
        ViewerClose("Video",onClose)
    }
}

@Composable fun Stories(vm:SparkViewModel) {
    var create by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(-1) }
    Rows(vm,"stories",{vm.api.feed("story")}) { stories ->
        Column {
            LazyRow(contentPadding=PaddingValues(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                item {
                    Card(onClick={create=true},modifier=Modifier.width(116.dp).height(208.dp),shape=RoundedCornerShape(14.dp)) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.3f))) {
                            val ownPhoto=vm.me!!.s("avatar_path")
                            if(ownPhoto.isNotBlank())PrivateImage(vm,ownPhoto,Modifier.fillMaxWidth().height(145.dp),targetPx=384)
                            else Box(Modifier.fillMaxWidth().height(145.dp).background(Color(0xFFDDEBFA)),contentAlignment=Alignment.Center) { Icon(Icons.Outlined.Person,null,Modifier.size(64.dp),tint=Blue) }
                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom=40.dp).size(42.dp).border(3.dp,MaterialTheme.colorScheme.surface,CircleShape).background(Blue,CircleShape),contentAlignment=Alignment.Center) { Icon(Icons.Outlined.Add,"Create story",tint=Color.White,modifier=Modifier.size(30.dp)) }
                            Text("Create story",Modifier.align(Alignment.BottomCenter).padding(bottom=14.dp),fontWeight=FontWeight.Bold,fontSize=13.sp)
                        }
                    }
                }
                itemsIndexed(stories,key={_,s->s.id()}) { index,story ->
                    Card(onClick={selected=index},modifier=Modifier.width(116.dp).height(208.dp),shape=RoundedCornerShape(14.dp)) {
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF6766D8),Color(0xFF24265F))))) {
                            if(story.s("media_path").isNotBlank())PrivateImage(vm,story.s("media_path"),Modifier.fillMaxSize(),videoFrame=story.s("media_type")=="video",targetPx=384)
                            else Text(story.s("body"),Modifier.padding(top=60.dp,start=10.dp,end=10.dp),color=Color.White,maxLines=3,fontSize=13.sp)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.7f)))))
                            Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.SpaceBetween) {
                                Box(Modifier.border(2.dp,Color(0xFFB6BEFF),CircleShape).padding(3.dp)) { Avatar(vm,story.child("author"),30) { selected=index } }
                                Text(story.child("author").s("display_name"),color=Color.White,fontWeight=FontWeight.Bold,maxLines=2,fontSize=12.sp)
                            }
                        }
                    }
                }
            }
        }
        if(selected in stories.indices)StoryDialog(vm,stories,selected) { selected=-1 }
    }
    if(create)ComposeDialog(vm,"story") { create=false }
}

@Composable fun StoryDialog(vm:SparkViewModel,stories:List<JSONObject>,initial:Int,onClose:()->Unit) {
    var index by remember { mutableIntStateOf(initial) }
    if(index !in stories.indices) { LaunchedEffect(Unit) { onClose() };return }
    val story=stories[index]
    val next:()->Unit={if(index<stories.lastIndex)index++ else onClose()}
    var confirmDelete by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf("") }
    FullscreenMedia(onClose) {
        key(story.id()) {
            var progress by remember { mutableFloatStateOf(0f) }
            var ready by remember { mutableStateOf(story.s("media_path").isBlank()) }
            val lifecycle=LocalLifecycleOwner.current.lifecycle
            var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
            DisposableEffect(lifecycle) {
                val observer=LifecycleEventObserver { _,_ -> active=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
                lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer) }
            }
            val video=story.s("media_type")=="video"&&story.s("media_path").isNotBlank()
            LaunchedEffect(ready,paused,confirmDelete,active,reply) {
                if(ready&&!video&&!paused&&!confirmDelete&&active&&reply.isBlank()) {
                    while(progress<1f) { delay(50);progress=(progress+.00625f).coerceAtMost(1f) }
                    next()
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF302B80),Color(0xFF111328))))) {
                if(video)SparkVideo(vm,story.s("media_path"),Modifier.fillMaxSize().padding(top=100.dp,bottom=120.dp),onProgress={progress=it},onEnded=next,paused=paused||confirmDelete||reply.isNotBlank())
                else if(story.s("media_path").isNotBlank())PrivateImage(vm,story.s("media_path"),Modifier.fillMaxSize(),ContentScale.Fit,onReady={ready=true},targetPx=1600)
                else Text(story.s("body"),Modifier.align(Alignment.Center).padding(32.dp),fontSize=30.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,color=Color.White)
                Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.75f),Color.Transparent))).statusBarsPadding().padding(12.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        stories.forEachIndexed { i,_ -> LinearProgressIndicator(progress={when { i<index->1f;i==index->progress;else->0f }},modifier=Modifier.weight(1f).height(3.dp),color=Color.White,trackColor=Color.White.copy(alpha=.25f)) }
                    }
                    Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Avatar(vm,story.child("author"),36)
                        Column(Modifier.weight(1f).padding(start=10.dp)) {
                            Text(story.child("author").s("display_name"),color=Color.White,fontWeight=FontWeight.Bold)
                            Text(ago(story.s("created_at")),color=Color.White.copy(alpha=.7f),fontSize=12.sp)
                        }
                        IconButton(onClick={paused=!paused}) { Icon(if(paused)Icons.Outlined.PlayArrow else Icons.Outlined.Pause,if(paused)"Resume story" else "Pause story",tint=Color.White) }
                        IconButton(onClick=onClose) { Icon(Icons.Outlined.Close,"Close story",tint=Color.White) }
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.8f)))).navigationBarsPadding().imePadding().padding(12.dp)) {
                    if(story.s("media_path").isNotBlank()&&story.s("body").isNotBlank())Text(story.s("body"),color=Color.White,maxLines=4,modifier=Modifier.padding(bottom=12.dp))
                    if(story.s("author_id")!=vm.api.userId)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        OutlinedTextField(value=reply,onValueChange={reply=it},placeholder={Text("Send message…",color=Color.LightGray)},singleLine=true,shape=CircleShape,modifier=Modifier.weight(1f),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=Color.White,unfocusedBorderColor=Color.Gray))
                        IconButton(enabled=vm.tasks==0,onClick={vm.work { val chat=vm.api.conversation(story.s("author_id"));vm.api.send(chat.id(),reply.ifBlank { "❤️" },null);reply="";vm.notice="Story reply sent." }}) { Icon(if(reply.isBlank())Icons.Outlined.Favorite else Icons.AutoMirrored.Outlined.Send,"Send story reply",tint=Color.White) }
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        IconButton(onClick={if(index>0)index--},enabled=index>0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft,"Previous story",tint=Color.White.copy(alpha=if(index>0)1f else .3f)) }
                        if(story.s("author_id")==vm.api.userId)IconButton(onClick={confirmDelete=true}) { Icon(Icons.Outlined.DeleteOutline,"Delete story",tint=Color.White) }
                        Text("${index+1} / ${stories.size}",color=Color.White,fontSize=12.sp)
                        IconButton(onClick=next) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,"Next story",tint=Color.White) }
                    }
                }
            }
        }
        if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this story?")},text={Text("This story will be removed from Spark.")},confirmButton={TextButton(onClick={
            vm.work { vm.api.delete("posts","id=eq.${story.id()}");confirmDelete=false;onClose();vm.refresh() }
        }) { Text("Delete") }},dismissButton={TextButton(onClick={confirmDelete=false}) { Text("Cancel") }})
    }
}
