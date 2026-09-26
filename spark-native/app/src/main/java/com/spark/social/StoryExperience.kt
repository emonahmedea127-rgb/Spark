package com.spark.social

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable fun StoryDialog(vm:SparkViewModel,stories:List<JSONObject>,initial:Int,onClose:()->Unit) {
    var index by remember { mutableIntStateOf(initial) }
    if(index !in stories.indices) { LaunchedEffect(Unit){onClose()};return }
    val story=stories[index]
    FullscreenMedia(onClose) {
        key(story.id()) { StoryPage(vm,story,index,stories.size,{if(index>0)index--},{if(index<stories.lastIndex)index++ else onClose()},onClose) }
    }
}

@Composable private fun StoryPage(vm:SparkViewModel,story:JSONObject,index:Int,total:Int,previous:()->Unit,next:()->Unit,onClose:()->Unit) {
    val own=story.s("author_id")==vm.api.userId
    val video=story.s("media_type")=="video"&&story.s("media_path").isNotBlank()
    val context=LocalContext.current
    var paused by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var viewersOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var compose by remember { mutableStateOf(false) }
    var publishKind by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf(story.s("body")) }
    var reply by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var viewers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var viewerError by remember { mutableStateOf(false) }
    var reactions by remember { mutableStateOf(story.rows("reactions")) }
    val mine=reactions.firstOrNull{it.s("user_id")==vm.api.userId}?.s("reaction")
    var progress by remember { mutableFloatStateOf(0f) }
    var ready by remember { mutableStateOf(story.s("media_path").isBlank()) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver{_,_->active=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}
    }
    val stopped=paused||menu||viewersOpen||confirmDelete||compose||publishKind.isNotBlank()||reply.isNotBlank()||busy||!active
    LaunchedEffect(active) {
        if(!active)return@LaunchedEffect
        if(!own)runCatching{vm.api.request("/rest/v1/rpc/sparknew_record_view","POST",json("content_id" to story.id()))}
        else while(true) {
            runCatching {
                val rows=vm.api.rows("post_views","select=viewer_id,viewer:sparknew_profiles!viewer_id(*)&post_id=eq.${story.id()}&order=viewed_on.desc")
                val likes=vm.api.rows("reactions","post_id=eq.${story.id()}")
                viewers=rows.distinctBy{it.s("viewer_id")};reactions=likes;viewerError=false
            }.onFailure{viewerError=true}
            delay(10000)
        }
    }
    LaunchedEffect(ready,stopped) {
        if(ready&&!video&&!stopped){while(progress<1f){delay(50);progress=(progress+.00625f).coerceAtMost(1f)};next()}
    }
    LaunchedEffect(feedback){if(feedback.isNotBlank()){delay(2500);feedback=""}}
    fun send(text:String) {
        if(busy||text.isBlank())return
        busy=true
        vm.work { try { val chat=vm.api.conversation(story.s("author_id"));vm.api.send(chat.id(),"Reply to your story${story.s("body").take(100).let{if(it.isBlank())"" else ": $it"}}\n$text",null);reply="";feedback="Reply sent" }finally{busy=false} }
    }
    fun react(value:String) {
        if(busy)return
        busy=true
        vm.work { try {
            val query="post_id=eq.${story.id()}&user_id=eq.${vm.api.userId}"
            if(mine==value)vm.api.delete("reactions",query)
            else if(mine==null)vm.api.insert("reactions",json("post_id" to story.id(),"user_id" to vm.api.userId,"reaction" to value))
            else vm.api.update("reactions",query,json("reaction" to value))
            reactions=reactions.filterNot{it.s("user_id")==vm.api.userId}+if(mine==value)emptyList() else listOf(json("user_id" to vm.api.userId,"reaction" to value))
            if(mine!=value)vm.sound(if(value=="like")SparkSounds.Event.LIKE else SparkSounds.Event.REACT)
            feedback=if(mine==value)"Reaction removed" else "Reaction sent"
        }finally{busy=false} }
    }
    fun share() {
        if(busy)return
        busy=true
        vm.work { try {
            val intent=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,story.s("body"))}
            if(story.s("media_path").isNotBlank()) {
                val bytes=vm.api.downloadMedia(story.s("media_path"))
                val file=withContext(Dispatchers.IO){val dir=File(context.cacheDir,"story-share").apply{mkdirs()};dir.listFiles()?.filter{System.currentTimeMillis()-it.lastModified()>86400000}?.forEach{it.delete()};File(dir,story.id()+if(video)".mp4" else ".jpg").apply{writeBytes(bytes)}}
                val uri=FileProvider.getUriForFile(context,"${context.packageName}.storyshare",file)
                intent.type=if(video)"video/*" else "image/*";intent.putExtra(Intent.EXTRA_STREAM,uri);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.clipData=android.content.ClipData.newRawUri("Story",uri)
            }
            context.startActivity(Intent.createChooser(intent,"Share story"))
        }finally{busy=false} }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if(video)SparkVideo(vm,story.s("media_path"),Modifier.fillMaxSize().padding(top=90.dp,bottom=110.dp),onProgress={progress=it},onEnded=next,paused=stopped)
        else if(story.s("media_path").isNotBlank())PrivateImage(vm,story.s("media_path"),Modifier.fillMaxSize().padding(bottom=90.dp),ContentScale.Fit,onReady={ready=true},targetPx=1600)
        else Text(story.s("body"),Modifier.align(Alignment.Center).padding(32.dp),color=Color.White,fontSize=30.sp,textAlign=TextAlign.Center)
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.8f),Color.Transparent))).statusBarsPadding().padding(12.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){repeat(total){i->LinearProgressIndicator(progress={if(i<index)1f else if(i==index)progress else 0f},modifier=Modifier.weight(1f).height(3.dp),color=Color.White,trackColor=Color.Gray)}}
            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=10.dp)) {
                Avatar(vm,story.child("author"),38)
                Column(Modifier.weight(1f).padding(start=8.dp)) {
                    CompositionLocalProvider(LocalContentColor provides Color.White){VerifiedName(vm,story.child("author"))}
                    Text(ago(story.s("created_at")),color=Color.LightGray,fontSize=12.sp)
                }
                IconButton(onClick={paused=!paused}){Icon(if(paused)Icons.Outlined.PlayArrow else Icons.Outlined.Pause,"Pause or resume",tint=Color.White)}
                Box {
                    IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreHoriz,"Story options",tint=Color.White)}
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                        if(own)DropdownMenuItem(text={Text("Delete story")},onClick={menu=false;confirmDelete=true})
                        DropdownMenuItem(text={Text("Close story")},onClick={menu=false;onClose()})
                    }
                }
                IconButton(onClick=onClose){Icon(Icons.Outlined.Close,"Close story",tint=Color.White)}
            }
            if(own&&video)TextButton(onClick={publishKind="reel"},modifier=Modifier.align(Alignment.End)){Text("Share as Reel",color=Color.White);Icon(Icons.Outlined.Movie,"",tint=Color.White,modifier=Modifier.padding(start=8.dp))}
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black))).navigationBarsPadding().imePadding().padding(12.dp)) {
            if(feedback.isNotBlank())Text(feedback,color=Color.White,modifier=Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
            if(story.s("media_path").isNotBlank()&&story.s("body").isNotBlank())Text(story.s("body"),color=Color.White,maxLines=3,modifier=Modifier.padding(bottom=8.dp))
            if(own) {
                Row(Modifier.fillMaxWidth().clickable{viewersOpen=true}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.KeyboardArrowUp,"View viewers",tint=Color.White)
                    Text(if(viewerError)"Viewers unavailable · Tap to retry" else "${viewers.size} viewers",color=Color.White,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.weight(1f));viewers.take(3).forEach{Avatar(vm,it.child("viewer"),28)}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    StoryAction("Add New",Icons.Outlined.AddCircleOutline){compose=true}
                    StoryAction(if(busy)"Sharing…" else "Share",Icons.Outlined.Share){share()}
                    StoryAction("Share as Post",Icons.Outlined.PostAdd){publishKind="post"}
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally)) {
                    listOf("👀🔥","gorgeous","love it").forEach{label->SuggestionChip(onClick={send(label)},label={Text(label,color=Color.White)},enabled=!busy,colors=SuggestionChipDefaults.suggestionChipColors(containerColor=Color.DarkGray.copy(alpha=.8f)))}
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    OutlinedTextField(reply,{reply=it},placeholder={Text("Send message…",color=Color.LightGray)},singleLine=true,shape=CircleShape,modifier=Modifier.weight(1f),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,unfocusedContainerColor=Color.DarkGray,focusedContainerColor=Color.DarkGray))
                    if(reply.isNotBlank())IconButton(enabled=!busy,onClick={send(reply)}){Icon(Icons.AutoMirrored.Outlined.Send,"Send reply",tint=Color.White)}
                    else {
                        IconButton(enabled=!busy,onClick={react("love")},modifier=Modifier.padding(start=6.dp).background(if(mine=="love")Color(0xFFFF2967) else Color(0xFF8C2345),CircleShape)){Icon(Icons.Outlined.Favorite,"Love story",tint=Color.White)}
                        IconButton(enabled=!busy,onClick={react("like")},modifier=Modifier.padding(start=6.dp).background(if(mine=="like")Color(0xFF0866FF) else Color(0xFF234D8C),CircleShape)){Icon(Icons.Outlined.ThumbUp,"Like story",tint=Color.White)}
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=previous,enabled=index>0){Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft,"Previous story",tint=if(index>0)Color.White else Color.Gray)}
                Text("${index+1} / $total",color=Color.LightGray,fontSize=12.sp)
                IconButton(onClick=next){Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,"Next story",tint=Color.White)}
            }
        }
    }
    if(viewersOpen)AlertDialog(onDismissRequest={viewersOpen=false},title={Text("${viewers.size} viewers")},text={
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=400.dp)) {
            if(viewers.isEmpty())item{Text(if(viewerError)"Could not load viewers. Close and reopen to retry." else "No viewers yet.")}
            items(viewers,key={it.s("viewer_id")}){row->Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Avatar(vm,row.child("viewer"),42);Text(row.child("viewer").s("display_name"),Modifier.weight(1f).padding(start=10.dp));Text(when(reactions.firstOrNull{it.s("user_id")==row.s("viewer_id")}?.s("reaction")){"love"->"❤️";"like"->"👍";"haha"->"😆";"wow"->"😮";"sad"->"😢";"angry"->"😡";else->""},fontSize=24.sp)}}
        }
    },confirmButton={TextButton(onClick={viewersOpen=false}){Text("Done")}})
    if(compose)ComposeDialog(vm,"story"){compose=false}
    if(publishKind.isNotBlank())AlertDialog(onDismissRequest={if(!busy)publishKind=""},title={Text("Share as ${if(publishKind=="reel")"Reel" else "Post"}")},text={OutlinedTextField(caption,{caption=it},label={Text("Caption")})},confirmButton={TextButton(enabled=!busy,onClick={busy=true;vm.work{try{vm.api.insert("posts",json("author_id" to vm.api.userId,"body" to caption,"kind" to publishKind,"visibility" to story.s("visibility").ifBlank{"friends"},"media_path" to story.s("media_path").ifBlank{null},"media_type" to story.s("media_type").ifBlank{null}));publishKind="";feedback="Shared successfully";vm.refresh()}finally{busy=false}}}){Text(if(busy)"Sharing…" else "Share")}},dismissButton={TextButton(enabled=!busy,onClick={publishKind=""}){Text("Cancel")}})
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this story?")},confirmButton={TextButton(enabled=!busy,onClick={busy=true;vm.work{try{vm.api.delete("posts","id=eq.${story.id()}");vm.refresh();onClose()}finally{busy=false}}}){Text("Delete")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancel")}})
}

@Composable private fun StoryAction(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit) {
    Column(Modifier.clickable(onClick=onClick).padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,label,tint=Color.White,modifier=Modifier.size(30.dp));Text(label,color=Color.White,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))}
}
