package com.spark.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable fun NotificationInbox(vm:SparkViewModel) {
    Rows(vm,"notices",{vm.api.rows("notifications","select=*,actor:sparknew_profiles!actor_id(*)&order=created_at.desc&limit=100")}){notices->
        var pending by remember(vm.revision){mutableStateOf<Set<String>>(emptySet())}
        var posts by remember(notices){mutableStateOf<Map<String,JSONObject>>(emptyMap())}
        LaunchedEffect(notices,vm.revision){
            try {
                if(notices.any{it.s("kind")=="friend_request"})pending=vm.api.rows("friendships","select=id&receiver_id=eq.${vm.api.userId}&status=eq.pending").map{it.id()}.toSet()
                val ids=notices.filter{it.s("kind") in listOf("comment","reaction")}.map{it.s("target_id")}.filter{it.isNotBlank()}.distinct()
                if(ids.isNotEmpty())posts=vm.api.rows("posts","select=id,body,kind&id=in.(${ids.joinToString(",")})").associateBy{it.id()}
            }catch(e:CancellationException){throw e}catch(_:Exception){}
        }
        val today=LocalDate.now()
        val groups=notices.groupBy{n->
            if(!n.optBoolean("is_read"))"New"
            else if(runCatching{Instant.parse(n.s("created_at")).atZone(ZoneId.systemDefault()).toLocalDate()==today}.getOrDefault(false))"Today"
            else "Earlier"
        }
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=16.dp)) {
            item{Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.End){TextButton(onClick={vm.work{vm.api.update("notifications","recipient_id=eq.${vm.api.userId}",json("is_read" to true));vm.unreadCount=0;vm.refresh()}}){Text("Mark all read")}}}
            listOf("New","Today","Earlier").forEach{group->
                val rows=groups[group].orEmpty()
                if(rows.isNotEmpty()){
                    item(key="header:$group"){Text(group,Modifier.padding(horizontal=16.dp,vertical=12.dp),fontSize=21.sp,fontWeight=FontWeight.Bold)}
                    items(rows,key={it.id()}){n->NotificationPreview(vm,n,posts[n.s("target_id")],n.s("target_id") in pending)}
                }
            }
            if(notices.isEmpty())item{Empty("You're all caught up","New reactions, requests and messages appear here.",Icons.Outlined.Notifications)}
        }
    }
}

@Composable private fun NotificationPreview(vm:SparkViewModel,n:JSONObject,post:JSONObject?,pending:Boolean){
    val kind=n.s("kind")
    val actor=n.child("actor")
    val unread=!n.optBoolean("is_read")
    var menu by remember(n.id()){mutableStateOf(false)}
    val eventIcon=when(kind){
        "friend_request","friend_accepted"->Icons.Outlined.PersonAdd
        "reaction"->Icons.Outlined.ThumbUp
        "comment"->Icons.Outlined.ChatBubble
        "message"->Icons.Outlined.Chat
        "profile_visit"->Icons.Outlined.Visibility
        else->Icons.Outlined.Notifications
    }
    val eventColor=when(kind){"comment"->Color(0xFF279B45);"reaction"->Color(0xFF1877F2);"message"->Color(0xFF7456CF);else->Blue}
    var openedStory by remember { mutableStateOf<JSONObject?>(null) }
    openedStory?.let{StoryDialog(vm,listOf(it),0){openedStory=null}}
    val subject=when(post?.s("kind")){"reel"->"reel";"story"->"story";else->"post"}
    val description=when(kind){
        "friend_request"->" sent you a friend request."
        "friend_accepted"->" accepted your friend request."
        "reaction"->" reacted to your $subject"
        "comment"->" commented on your $subject"
        "message"->" sent you a message."
        "profile_visit"->" visited your profile."
        else->" sent you a notification."
    }
    Surface(color=if(unread)Color(0xFFE7F3FF) else MaterialTheme.colorScheme.surface,contentColor=if(unread)Color(0xFF17191D) else MaterialTheme.colorScheme.onSurface,onClick={vm.work{
        if(unread)vm.api.update("notifications","id=eq.${n.id()}",json("is_read" to true))
        when(kind){
            "message"->vm.go("Chat",n.s("target_id"),actor.s("display_name"))
            "friend_request","friend_accepted","profile_visit"->vm.go("Profile",n.s("actor_id"))
            "reaction","comment"->{
                val target=vm.api.rows("posts","${vm.api.postSelect}&id=eq.${n.s("target_id")}").firstOrNull()
                when {
                    target==null->vm.notice="This content has expired or is no longer available."
                    target.s("kind")=="story"->openedStory=target
                    target.s("kind")=="reel"&&kind=="reaction"->vm.go("Reels",target.id())
                    else->vm.go("Comments",target.id())
                }
            }
            else->vm.go("Profile",n.s("actor_id"))
        };vm.refresh()
    }}) {
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp,top=12.dp,bottom=12.dp),verticalAlignment=Alignment.Top){
            Box(Modifier.size(66.dp)){
                Avatar(vm,actor,62){vm.go("Profile",n.s("actor_id"))}
                Box(Modifier.align(Alignment.BottomEnd).size(27.dp).background(eventColor,CircleShape),contentAlignment=Alignment.Center){Icon(eventIcon,null,Modifier.size(16.dp),tint=Color.White)}
            }
            Column(Modifier.weight(1f).padding(start=10.dp)){
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight=FontWeight.Bold)){append(actor.s("display_name").ifBlank{"Spark member"})}
                    append(description)
                    if(!post?.s("body").isNullOrBlank())append(": “${post!!.s("body").take(110)}”")
                },fontSize=16.sp,maxLines=4)
                Text(ago(n.s("created_at")),Modifier.padding(top=3.dp),fontSize=13.sp,color=Color(0xFF65676B))
                if(kind=="friend_request"&&pending)Row(Modifier.fillMaxWidth().padding(top=9.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(enabled=vm.tasks==0,modifier=Modifier.weight(1f),shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(horizontal=4.dp),onClick={vm.work{
                        vm.api.update("friendships","id=eq.${n.s("target_id")}&receiver_id=eq.${vm.api.userId}&status=eq.pending",json("status" to "accepted"))
                        vm.api.update("notifications","id=eq.${n.id()}",json("is_read" to true));vm.refresh()
                    }}){Text("Confirm")}
                    FilledTonalButton(enabled=vm.tasks==0,modifier=Modifier.weight(1f),shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(horizontal=4.dp),onClick={vm.work{
                        vm.api.delete("friendships","id=eq.${n.s("target_id")}&receiver_id=eq.${vm.api.userId}&status=eq.pending")
                        vm.api.delete("notifications","id=eq.${n.id()}");vm.refresh()
                    }}){Text("Delete")}
                }
            }
            Box{
                IconButton(onClick={menu=true},modifier=Modifier.size(30.dp)){Icon(Icons.Outlined.MoreHoriz,"Notification options",Modifier.size(21.dp))}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                    DropdownMenuItem(text={Text(if(unread)"Mark as read" else "Mark as unread")},onClick={menu=false;vm.work{vm.api.update("notifications","id=eq.${n.id()}",json("is_read" to unread));vm.refresh()}})
                    DropdownMenuItem(text={Text("Remove notification")},onClick={menu=false;vm.work{vm.api.delete("notifications","id=eq.${n.id()}");vm.refresh()}})
                }
            }
        }
    }
}
