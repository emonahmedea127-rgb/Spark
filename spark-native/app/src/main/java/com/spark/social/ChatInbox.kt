package com.spark.social

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

private fun recentlyActive(time:String)=runCatching{java.time.Duration.between(Instant.parse(time),Instant.now()).seconds in 0..45}.getOrDefault(false)
fun presenceLabel(vm:SparkViewModel,id:String):String {
    val time=vm.presenceRows.firstOrNull{it.s("user_id")==id}?.s("last_active").orEmpty()
    return if(recentlyActive(time))"Active now" else if(time.isNotBlank())"Active ${ago(time)} ago" else "Offline"
}
@Composable fun ChatPresenceSync(vm:SparkViewModel){
    val owner=LocalLifecycleOwner.current
    LaunchedEffect(vm.api.userId,owner){owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
        while(true){
            try{
                vm.api.request("/rest/v1/rpc/sparknew_presence_pulse","POST",json())
                vm.presenceRows=vm.api.rows("presence","select=*,profile:sparknew_profiles!user_id(*)&order=last_active.desc&limit=100")
                val incoming=vm.api.rows("messages","select=id&sender_id=neq.${vm.api.userId}&delivered_at=is.null&unsent_at=is.null&order=created_at.desc&limit=100")
                if(incoming.isNotEmpty())vm.api.request("/rest/v1/rpc/sparknew_mark_delivered","POST",json("message_ids" to JSONArray(incoming.map{it.id()})))
            }catch(e:CancellationException){throw e}catch(_:Exception){}
            delay(12000)
        }
    }}
}
@Composable fun MessageStatus(message:JSONObject){
    val seen=message.s("seen_at").isNotBlank()
    val delivered=message.s("delivered_at").isNotBlank()
    Icon(if(seen)Icons.Outlined.DoneAll else if(delivered)Icons.Outlined.CheckCircle else Icons.Outlined.Check,
        if(seen)"Seen" else if(delivered)"Delivered" else "Sent",Modifier.size(17.dp),tint=if(seen||delivered)Blue else MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun OnlineAvatar(vm:SparkViewModel,person:JSONObject,size:Int=58,onClick:()->Unit){
    Box{
        Avatar(vm,person,size,onClick)
        if(presenceLabel(vm,person.id())=="Active now")Box(Modifier.align(Alignment.BottomEnd).size(15.dp).background(Color(0xFF27883A),CircleShape).border(2.dp,MaterialTheme.colorScheme.surface,CircleShape))
    }
}
@Composable fun MessengerInbox(vm:SparkViewModel){
    var chats by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var unread by remember{mutableStateOf<Map<String,Int>>(emptyMap())}
    var error by remember{mutableStateOf<String?>(null)}
    val owner=LocalLifecycleOwner.current
    LaunchedEffect(vm.revision,owner){owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(true){
        try{
            chats=vm.api.rows("conversations","select=*,a:sparknew_profiles!user_a(*),b:sparknew_profiles!user_b(*),latest:sparknew_messages(*)&latest.order=created_at.desc&latest.limit=1&limit=100")
                .sortedByDescending{it.optJSONArray("latest")?.optJSONObject(0)?.s("created_at")?:it.s("created_at")}
            unread=vm.api.rows("messages","select=conversation_id&sender_id=neq.${vm.api.userId}&seen_at=is.null&unsent_at=is.null&limit=1000").groupingBy{it.s("conversation_id")}.eachCount()
            error=null
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
        delay(4000)
    }}}
    LazyColumn(Modifier.fillMaxSize()){
        item{Section("Messages","New message"){vm.go("Search")}}
        item{
            val active=vm.presenceRows.filter{it.s("user_id")!=vm.api.userId&&recentlyActive(it.s("last_active"))}
            if(active.isNotEmpty())LazyRow(contentPadding=PaddingValues(horizontal=16.dp,vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){
                items(active,key={it.s("user_id")}){row->val person=row.child("profile")
                    Column(Modifier.width(66.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        OnlineAvatar(vm,person){vm.work{val c=vm.api.conversation(person.id());vm.go("Chat",c.id(),person.s("display_name"))}}
                        Text(person.s("display_name"),Modifier.padding(top=6.dp),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp)
                    }
                }
            }
        }
        error?.let{item{Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)}}
        items(chats,key={it.id()}){c->
            val person=c.child(if(c.s("user_a")==vm.api.userId)"b" else "a")
            val last=c.optJSONArray("latest")?.optJSONObject(0)
            val count=unread[c.id()]?:0
            Surface(onClick={vm.go("Chat",c.id(),person.s("display_name"))}){
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                    OnlineAvatar(vm,person){vm.go("Chat",c.id(),person.s("display_name"))}
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)){
                        Text(person.s("display_name"),fontSize=18.sp,fontWeight=if(count>0)FontWeight.Bold else FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        val prefix=if(last?.s("sender_id")==vm.api.userId)"You: " else ""
                        val preview=when{last==null->"Start a conversation";last.s("unsent_at").isNotBlank()->"Message unsent";last.s("body").isNotBlank()->last.s("body");last.s("media_type")=="video"->"Video";else->"Photo"}
                        Text(prefix+preview,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=if(count>0)FontWeight.Bold else FontWeight.Normal)
                    }
                    if(count>0)Badge(containerColor=Color(0xFFE82E43)){Text(if(count>99)"99+" else count.toString())}
                    else if(last!=null&&last.s("sender_id")==vm.api.userId)MessageStatus(last)
                }
            }
        }
        if(chats.isEmpty()&&error==null)item{Empty("Say hello","Find a friend and start a conversation.",Icons.Outlined.Chat)}
    }
}
