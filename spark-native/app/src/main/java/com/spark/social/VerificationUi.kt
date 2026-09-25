package com.spark.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

@Composable fun VerifiedName(vm:SparkViewModel,person:JSONObject,large:Boolean=false) {
    val id=person.id()
    var verified by remember(id,vm.revision){mutableStateOf(false)}
    LaunchedEffect(id,vm.revision){
        if(id.isNotBlank()) verified=runCatching {
            vm.api.rows("badges","select=profile_id&profile_id=eq.$id&limit=1").isNotEmpty()
        }.getOrDefault(false)
    }
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=if(large)12.dp else 0.dp)){
        Text(person.s("display_name").ifBlank{"Spark member"},fontSize=if(large)28.sp else 16.sp,fontWeight=FontWeight.Bold)
        if(verified)Icon(Icons.Outlined.Verified,"Verified on Spark",Modifier.padding(start=5.dp).size(if(large)24.dp else 18.dp),tint=Blue)
    }
}

@Composable fun BadgeAwardAction(vm:SparkViewModel,person:JSONObject) {
    var admin by remember(vm.api.userId){mutableStateOf(false)}
    var hasBadge by remember(person.id(),vm.revision){mutableStateOf(false)}
    LaunchedEffect(person.id(),vm.revision){
        admin=runCatching{vm.api.rows("badge_admins","select=user_id&user_id=eq.${vm.api.userId}").isNotEmpty()}.getOrDefault(false)
        if(admin)hasBadge=runCatching{vm.api.rows("badges","select=profile_id&profile_id=eq.${person.id()}").isNotEmpty()}.getOrDefault(false)
    }
    if(admin)TextButton(enabled=vm.tasks==0,onClick={vm.work{
        if(hasBadge)vm.api.delete("badges","profile_id=eq.${person.id()}")
        else vm.api.insert("badges",json("profile_id" to person.id(),"granted_by" to vm.api.userId))
        vm.refresh()
    }}){Text(if(hasBadge)"Remove verification badge" else "Grant verification badge")}
}

@Composable fun BadgeRequestsScreen(vm:SparkViewModel) {
    var requests by remember(vm.revision){mutableStateOf<List<JSONObject>>(emptyList())}
    var myRequest by remember(vm.revision){mutableStateOf<JSONObject?>(null)}
    var administrator by remember(vm.revision){mutableStateOf(false)}
    var target by remember{mutableStateOf("")}
    var message by remember{mutableStateOf("")}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(vm.revision){
        loading=true;error=null
        try{
            administrator=vm.api.rows("badge_admins","select=user_id&user_id=eq.${vm.api.userId}").isNotEmpty()
            myRequest=vm.api.rows("badge_requests","requester_id=eq.${vm.api.userId}&order=created_at.desc&limit=1").firstOrNull()
            requests=if(administrator)vm.api.rows("badge_requests","select=*,requester:sparknew_profiles!requester_id(display_name,avatar_path)&status=eq.pending&order=created_at.asc&limit=100") else emptyList()
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
        finally{loading=false}
    }
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Text("Verification badges",fontSize=25.sp,fontWeight=FontWeight.Bold)}
        if(loading)item{CircularProgressIndicator()}
        error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
        item{Text("Your latest application: ${myRequest?.s("status")?:"Not submitted"}")}
        if(myRequest?.s("status")!="pending")item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(value=message,onValueChange={message=it.take(500)},label={Text("Why should your account be verified?")},modifier=Modifier.fillMaxWidth(),minLines=3)
                Button(enabled=message.isNotBlank()&&vm.tasks==0,onClick={vm.work{
                    vm.api.insert("badge_requests",json("requester_id" to vm.api.userId,"reason" to message.trim()))
                    message="";vm.refresh()
                }}){Text("Request verification")}
            }
        }
        if(administrator){
            item{HorizontalDivider();Text("Award a badge",fontSize=20.sp,fontWeight=FontWeight.Bold)}
            item{
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(value=target,onValueChange={target=it.trim()},label={Text("Account ID (UUID)")},modifier=Modifier.fillMaxWidth())
                    Button(enabled=target.isNotBlank()&&vm.tasks==0,onClick={vm.work{
                        vm.api.insert("badges",json("profile_id" to target,"granted_by" to vm.api.userId))
                        target="";vm.refresh()
                    }}){Text("Grant badge")}
                }
            }
            item{Text("Pending requests",fontSize=20.sp,fontWeight=FontWeight.Bold)}
            items(requests,key={it.id()}){request->
                OutlinedCard{Column(Modifier.fillMaxWidth().padding(14.dp)){
                    Text(request.child("requester").s("display_name").ifBlank{"Spark member"},fontWeight=FontWeight.Bold)
                    Text("Account ID: ${request.s("requester_id")}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(request.s("reason"))
                    Row{
                        TextButton(enabled=vm.tasks==0,onClick={vm.work{
                            if(vm.api.rows("badges","profile_id=eq.${request.s("requester_id")}").isEmpty())
                                vm.api.insert("badges",json("profile_id" to request.s("requester_id"),"granted_by" to vm.api.userId))
                            vm.api.update("badge_requests","id=eq.${request.id()}",json("status" to "approved"))
                            vm.refresh()
                        }}){Text("Approve")}
                        TextButton(enabled=vm.tasks==0,onClick={vm.work{
                            vm.api.update("badge_requests","id=eq.${request.id()}",json("status" to "rejected"))
                            vm.refresh()
                        }}){Text("Reject")}
                    }
                }}
            }
        }
    }
}
