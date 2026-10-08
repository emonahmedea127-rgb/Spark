package com.spark.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

@Composable fun ModerationMenuEntry(vm:SparkViewModel) {
    var admin by remember(vm.api.userId){mutableStateOf(false)}
    LaunchedEffect(vm.api.userId){try{admin=vm.api.rows("badge_admins","user_id=eq.${vm.api.userId}&select=user_id").isNotEmpty()}catch(e:CancellationException){throw e}catch(_:Exception){}}
    if(admin)OutlinedCard(onClick={vm.go("Reports")},modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Shield,"Reports",tint=Blue);Column(Modifier.padding(start=14.dp)){Text("Reports & moderation",fontWeight=FontWeight.Bold);Text("Review reports · Hide or restore content",fontSize=12.sp)}}
    }
}

@Composable fun ModerationInbox(vm:SparkViewModel) {
    var reviewed by remember{mutableStateOf(false)}
    var limit by remember(reviewed){mutableIntStateOf(50)}
    var decision by remember{mutableStateOf("")}
    var chosen by remember{mutableStateOf<JSONObject?>(null)}
    var preview by remember{mutableStateOf<JSONObject?>(null)}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!reviewed,{reviewed=false},label={Text("Pending")})
            FilterChip(reviewed,{reviewed=true},label={Text("Reviewed")})
            IconButton(onClick={vm.refresh()}){Icon(Icons.Outlined.Refresh,"Refresh reports")}
        }
        Rows(vm,"reports:$reviewed:$limit",{
            require(vm.api.rows("badge_admins","user_id=eq.${vm.api.userId}&select=user_id").isNotEmpty()){"Admin access required."}
            vm.api.rows("reports","select=*,reporter:sparknew_profiles!reporter_id(display_name,avatar_path)&status=${if(reviewed)"neq.pending" else "eq.pending"}&order=created_at.desc&limit=$limit")
        }) { reports->
            LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(reports.isEmpty())item{Text(if(reviewed)"No reviewed reports." else "No pending reports.")}
                items(reports,key={it.id()}) { report->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("${report.s("target_type").replaceFirstChar{it.uppercase()}} report",fontWeight=FontWeight.Bold,fontSize=18.sp)
                            Text(report.s("reason"))
                            Text("Reported by ${report.child("reporter").s("display_name").ifBlank{"Spark member"}} · ${ago(report.s("created_at"))}",fontSize=12.sp)
                            Text(report.s("status").replaceFirstChar{it.uppercase()},color=Blue,fontWeight=FontWeight.SemiBold)
                            if(report.s("target_type")=="post"&&report.s("status")!="hidden")TextButton(onClick={vm.work{
                                preview=vm.api.rows("posts","${vm.api.postSelect}&id=eq.${report.s("target_id")}").firstOrNull()
                                if(preview==null)vm.notice="Content is no longer available."
                            }}){Text("View content")}
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                if(report.s("status")=="pending") {
                                    OutlinedButton(onClick={chosen=report;decision="dismiss"}){Text("Dismiss")}
                                    if(report.s("target_type")=="post")Button(onClick={chosen=report;decision="hide"}){Text("Hide content")}
                                }
                                if(report.s("status")=="hidden")Button(onClick={chosen=report;decision="restore"}){Text("Restore content")}
                            }
                        }
                    }
                }
                if(reports.size>=limit)item{TextButton(onClick={limit+=50}){Text("Load more reports")}}
            }
        }
    }
    preview?.let{post->AlertDialog(onDismissRequest={preview=null},title={Text("Reported content")},text={Column {
        Text(post.child("author").s("display_name"),fontWeight=FontWeight.Bold)
        Text(post.s("body"))
        if(post.s("media_path").isNotBlank())PrivateImage(vm,post.s("media_path"),Modifier.fillMaxWidth().height(240.dp),videoFrame=post.s("media_type")=="video")
    }},confirmButton={TextButton(onClick={preview=null}){Text("Close")}})}
    chosen?.let{report->AlertDialog(onDismissRequest={if(vm.tasks==0)chosen=null},title={Text(when(decision){"hide"->"Hide this content?";"restore"->"Restore this content?";else->"Dismiss this report?"})},text={Text(when(decision){"hide"->"The content will be hidden from users. You can restore it from Reviewed.";"restore"->"The content will become visible again under its original privacy settings.";else->"This report will move to Reviewed without hiding the content."})},confirmButton={Button(enabled=vm.tasks==0,onClick={vm.work{
        vm.api.request("/rest/v1/rpc/sparknew_review_report","POST",json("report_id" to report.id(),"decision" to decision));chosen=null;vm.refresh()
    }}){Text("Confirm")}},dismissButton={TextButton(enabled=vm.tasks==0,onClick={chosen=null}){Text("Cancel")}})}
}
