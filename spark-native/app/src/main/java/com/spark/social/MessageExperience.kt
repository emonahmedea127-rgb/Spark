@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.spark.social
import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun messageTime(value:String)=runCatching{DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.parse(value))}.getOrDefault(value)
internal fun messageReceipt(message:JSONObject):String=when {
    message.s("unsent_at").isNotBlank()->"Unsent"
    message.optBoolean("view_once")&&message.s("opened_at").isNotBlank()->"Opened · ${messageTime(message.s("opened_at"))}"
    message.s("seen_at").isNotBlank()->"Seen · ${messageTime(message.s("seen_at"))}"
    message.s("delivered_at").isNotBlank()->"Delivered"
    else->"Sent"
}
@Composable fun MessageActions(own:Boolean,canEdit:Boolean,onEdit:()->Unit,onUnsend:()->Unit,onClose:()->Unit) {
    ModalBottomSheet(onDismissRequest=onClose){Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
        Text("Message",fontWeight=FontWeight.Bold,fontSize=20.sp)
        if(own){if(canEdit)TextButton(onClick=onEdit,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Edit,null);Text(" Edit message")};TextButton(onClick=onUnsend,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Undo,null);Text(" Unsend for everyone")}}
        else Text("Only the sender can edit or unsend this message.",Modifier.padding(vertical=18.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }}
}
@Composable fun ChatMessage(vm:SparkViewModel,message:JSONObject) {
    val own=message.s("sender_id")==vm.api.userId
    val unsent=message.s("unsent_at").isNotBlank()
    var actions by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf(false)}
    var unsending by remember{mutableStateOf(false)}
    var viewing by remember{mutableStateOf(false)}
    val ink=if(own)Color.White else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
      if(!own){Avatar(vm,message.child("sender"),26);Spacer(Modifier.width(7.dp))}
      Column(Modifier.weight(1f),horizontalAlignment=if(own)Alignment.End else Alignment.Start) {
        Surface(color=if(own)Blue else MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(18.dp),modifier=Modifier.widthIn(max=290.dp).combinedClickable(enabled=!unsent,onClick={actions=true},onLongClick={actions=true})) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(if(unsent)"Message unsent" else message.s("body"),color=ink)
                if(!unsent){
                    if(message.optBoolean("view_once"))OutlinedButton(enabled=!own&&message.s("opened_at").isBlank(),onClick={viewing=true}){Icon(Icons.Outlined.Visibility,null,tint=ink);Text(if(message.s("opened_at").isNotBlank())"Opened" else if(own)"View once photo" else "Open photo once",color=ink)}
                    else Media(vm,message.s("media_path"),message.s("media_type"))
                }
                Text(ago(message.s("created_at"))+if(!unsent&&message.s("edited_at").isNotBlank())" · Edited" else "",fontSize=10.sp,color=ink.copy(alpha=.7f))
            }
        }
        if(own)Row(Modifier.padding(top=3.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){MessageStatus(message);Spacer(Modifier.width(4.dp));Text(messageReceipt(message),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
      }
    }
    if(actions)MessageActions(own,message.s("body").isNotBlank(),{actions=false;editing=true},{actions=false;unsending=true},{actions=false})
    if(editing)TextForm("Edit message",listOf("Message" to message.s("body")),vm,{editing=false}){values->
        require(values[0].trim().length in 1..10000){"Write between 1 and 10000 characters."}
        vm.api.changeMessage(message.id(),"edit",values[0].trim());editing=false;vm.refresh()
    }
    if(unsending)Confirm("Unsend message?","This removes the message from both sides of this conversation.",{unsending=false}){vm.work{vm.api.changeMessage(message.id(),"unsend");FastImages.clear();unsending=false;vm.refresh()}}
    if(viewing)ViewOncePhoto(vm,message.id()){viewing=false;vm.refresh()}
}
@Composable private fun ViewOncePhoto(vm:SparkViewModel,id:String,onClose:()->Unit) {
    var bitmap by remember{mutableStateOf<android.graphics.Bitmap?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    val owner=LocalLifecycleOwner.current
    val close by rememberUpdatedState(onClose)
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)close()};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer);bitmap=null}}
    LaunchedEffect(id){try{
        val bytes=vm.api.openOnce(id)
        bitmap=withContext(Dispatchers.IO){try{BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:throw IllegalStateException("Photo unavailable")}finally{bytes.fill(0)}}
    }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Photo unavailable"}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false,securePolicy=SecureFlagPolicy.SecureOn)) {
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            bitmap?.let{Image(it.asImageBitmap(),"View once photo",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
            if(bitmap==null&&error==null)CircularProgressIndicator(Modifier.align(Alignment.Center),color=Color.White)
            error?.let{Text(it,Modifier.align(Alignment.Center).padding(24.dp),color=Color.White)}
            IconButton(onClick=onClose,modifier=Modifier.align(Alignment.TopEnd)){Icon(Icons.Outlined.Close,"Close photo",tint=Color.White)}
            Text("View once · Closing removes access",Modifier.align(Alignment.BottomCenter).padding(16.dp),color=Color.White,fontSize=12.sp)
        }
    }
}
