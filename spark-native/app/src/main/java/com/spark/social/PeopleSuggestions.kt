package com.spark.social

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

suspend fun SparkApi.suggestions(offset:Int=0):List<JSONObject> = JSONArray(request("/rest/v1/rpc/sparknew_suggestions","POST",json("page_size" to 20,"page_offset" to offset))).rows()

@Composable fun PeopleSuggestions(vm:SparkViewModel,fullPage:Boolean=false,load:suspend(Int)->List<JSONObject> = {vm.api.suggestions(it)},sendRequest:suspend(JSONObject)->Unit={vm.api.insert("friendships",json("sender_id" to vm.api.userId,"receiver_id" to it.id()));Unit},dismiss:suspend(JSONObject)->Unit={vm.api.insert("suggestion_dismissals",json("owner_id" to vm.api.userId,"target_id" to it.id()));Unit}) {
    var people by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    var loading by remember {mutableStateOf(false)}
    var more by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf<String?>(null)}
    val busy=remember {mutableStateListOf<String>()}
    val scope=rememberCoroutineScope()
    var generation by remember {mutableIntStateOf(0)}
    suspend fun fetch(reset:Boolean=false) {
        if(loading&&!reset)return
        val current=if(reset){generation++;generation}else generation
        loading=true;error=null
        try {val next=load(if(reset)0 else people.size);if(current==generation){people=if(reset)next else (people+next).distinctBy{it.id()};more=next.size==20}}
        catch(e:CancellationException){throw e}catch(e:Exception){if(current==generation)error="Couldn't load suggestions. Try again."}
        finally{if(current==generation)loading=false}
    }
    LaunchedEffect(vm.revision){fetch(true)}
    fun act(person:JSONObject,remove:Boolean){if(person.id() in busy)return;busy.add(person.id());scope.launch {
        try {if(remove)dismiss(person) else sendRequest(person);people=people.filterNot{it.id()==person.id()};error=null}
        catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Couldn't update. Try again."}
        finally{busy.remove(person.id())}
    }}
    val cards:@Composable ()->Unit={
        LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            items(people,key={it.id()}){p->SuggestionCard(vm,p,p.id() in busy,{vm.go("Profile",p.id())},{act(p,false)},{act(p,true)})}
        }
    }
    if(fullPage) {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item{Text("People You May Know",fontSize=24.sp,fontWeight=FontWeight.Bold);Text("Mutual friends and shared public details help you find people.",Modifier.padding(vertical=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(people,key={it.id()}){p->SuggestionCard(vm,p,p.id() in busy,{vm.go("Profile",p.id())},{act(p,false)},{act(p,true)},wide=true)}
            if(loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error);TextButton(onClick={scope.launch{fetch(people.isEmpty())}}){Text("Retry")}}
            if(!loading&&people.isEmpty()&&error==null)item{Empty("You're all caught up","New suggestions will appear as more people join and add shared details.")}
            if(more&&!loading&&people.isNotEmpty())item{TextButton(onClick={scope.launch{fetch()}}){Text("Show more people")}}
        }
    } else if(people.isNotEmpty()||loading||error!=null) {
        Surface {Column(Modifier.fillMaxWidth().padding(vertical=12.dp)) {
            Text("People You May Know",Modifier.padding(start=14.dp,bottom=12.dp),fontSize=21.sp,fontWeight=FontWeight.SemiBold)
            cards()
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))
            if(error!=null)TextButton(onClick={scope.launch{fetch(people.isEmpty())}}){Text("Couldn't load suggestions · Retry")}
            TextButton(onClick={vm.go("Suggestions")},modifier=Modifier.fillMaxWidth()){Text("See all ›")}
            HorizontalDivider(thickness=5.dp,color=MaterialTheme.colorScheme.surfaceVariant)
        }}
    }
}
@Composable fun SuggestionCard(vm:SparkViewModel,person:JSONObject,busy:Boolean,onProfile:()->Unit,onAdd:()->Unit,onDismiss:()->Unit,wide:Boolean=false) {
    OutlinedCard(modifier=if(wide)Modifier.fillMaxWidth() else Modifier.width(224.dp),shape=RoundedCornerShape(10.dp)) {
        if(wide)Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Avatar(vm,person,76,onProfile)
            Column(Modifier.weight(1f).padding(start=12.dp)) {SuggestionName(person,onProfile);SuggestionButtons(busy,onAdd,onDismiss)}
        }else {
            Box(Modifier.fillMaxWidth().height(224.dp).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick=onProfile)) {
                if(person.s("avatar_path").isNotBlank())PrivateImage(vm,person.s("avatar_path"),Modifier.fillMaxSize())
                else Icon(Icons.Outlined.PersonOutline,null,Modifier.size(92.dp).align(Alignment.Center),tint=MaterialTheme.colorScheme.outline)
                IconButton(enabled=!busy,onClick=onDismiss,modifier=Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp).background(MaterialTheme.colorScheme.surface.copy(alpha=.9f),CircleShape)){Icon(Icons.Outlined.Close,"Remove suggestion for ${person.s("display_name")}")}
            }
            Column(Modifier.padding(12.dp)){SuggestionName(person,onProfile)
                OutlinedButton(enabled=!busy,onClick=onAdd,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(7.dp)){Icon(Icons.Outlined.PersonAdd,null,Modifier.size(20.dp));Text(if(busy)"Please wait…" else " Add friend",fontWeight=FontWeight.Bold)}
            }
        }
    }
}
@Composable private fun SuggestionName(person:JSONObject,onProfile:()->Unit) {
    Text(person.s("display_name"),Modifier.clickable(onClick=onProfile),fontWeight=FontWeight.Bold,fontSize=18.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    Text(person.s("reason"),Modifier.padding(top=4.dp,bottom=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp,maxLines=2)
}
@Composable private fun SuggestionButtons(busy:Boolean,onAdd:()->Unit,onDismiss:()->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(enabled=!busy,onClick=onAdd,shape=RoundedCornerShape(7.dp),contentPadding=PaddingValues(horizontal=12.dp)){Text(if(busy)"Sending…" else "Add friend")}
        TextButton(enabled=!busy,onClick=onDismiss){Text("Remove")}
    }
}
