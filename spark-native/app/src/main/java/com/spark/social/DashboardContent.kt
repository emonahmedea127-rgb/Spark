package com.spark.social

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.time.Instant

@Composable fun ContentLibraryScreen(vm:SparkViewModel,content:List<JSONObject>) {
    var kind by remember{mutableStateOf("All")}
    var order by remember{mutableStateOf("Views")}
    var days by remember{mutableIntStateOf(0)}
    var dateMenu by remember{mutableStateOf(false)}
    var listView by remember{mutableStateOf(false)}
    val dated=remember(content,days) {
        val cutoff=Instant.now().minusSeconds(days.toLong()*86400)
        content.filter{days==0||runCatching{Instant.parse(it.s("created_at"))>=cutoff}.getOrDefault(false)}
    }
    val filtered=remember(dated,kind,order) {
        dated.filter {
            kind=="All" || kind=="Reels"&&it.s("kind")=="reel" ||
                kind=="Photos"&&it.s("media_type")=="image"
        }.sortedWith(compareByDescending<JSONObject>{
            when(order){"Newest"->runCatching{Instant.parse(it.s("created_at")).epochSecond}.getOrDefault(0L);"Views"->it.optLong("views");else->it.optLong("reactions")+it.optLong("comments")}
        }.thenByDescending{it.s("created_at")})
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("All","Reels","Photos").forEach{choice->
                FilterChip(selected=kind==choice,onClick={kind=choice},label={Text(choice)})
            }
        }
        if(dated.isNotEmpty()) {
            Text("Content overview",Modifier.padding(14.dp),fontSize=22.sp,fontWeight=FontWeight.Bold)
            LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                val top=listOf("Most viewed" to dated.maxByOrNull{it.optLong("views")},
                    "Most engaged" to dated.maxByOrNull{it.optLong("reactions")+it.optLong("comments")})
                items(top){(title,item)->
                    OutlinedCard(Modifier.width(148.dp).clickable{item?.let{vm.go("Content insights",it.id())}}){
                        Box(Modifier.fillMaxWidth().height(105.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if(item?.s("media_type")=="image")PrivateImage(vm,item.s("media_path"),Modifier.fillMaxSize(),targetPx=360)
                            else if(item?.s("media_type")=="video")VideoThumbnail(vm,item.s("media_path"),Modifier.fillMaxSize())
                            else Icon(Icons.Outlined.SmartDisplay,null,Modifier.align(Alignment.Center),tint=Blue)
                        }
                        Text(title,Modifier.padding(10.dp),fontWeight=FontWeight.SemiBold)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.weight(1f)) {
                TextButton(onClick={dateMenu=true}){Text(if(days==0)"All dates" else "Published: $days days");Icon(Icons.Outlined.ArrowDropDown,null)}
                DropdownMenu(expanded=dateMenu,onDismissRequest={dateMenu=false}){
                    listOf(0,7,28,90).forEach{value->DropdownMenuItem(text={Text(if(value==0)"All publishing dates" else "Published in last $value days")},onClick={days=value;dateMenu=false})}
                }
            }
            IconButton(onClick={listView=!listView}){Icon(if(listView)Icons.Outlined.GridView else Icons.Outlined.ViewList,if(listView)"Grid view" else "List view")}
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("Newest","Views","Engagement").forEach{value->FilterChip(selected=order==value,onClick={order=value},label={Text(value)})}
        }
        if(filtered.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("No content to show")}
        else if(listView)LazyColumn(contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            items(filtered,key={it.id()}){item->
                OutlinedCard(onClick={vm.go("Content insights",item.id())}){
                    Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                        Box(Modifier.size(78.dp).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){
                            if(item.s("media_path").isNotBlank())PrivateImage(vm,item.s("media_path"),Modifier.fillMaxSize(),videoFrame=item.s("media_type")=="video",targetPx=240)
                            else Icon(Icons.Outlined.Description,null)
                        }
                        Column(Modifier.weight(1f).padding(start=12.dp)){
                            Text(item.s("body").ifBlank{if(item.s("kind")=="reel")"Reel" else "Post"},maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                            Text(ago(item.s("created_at")),fontSize=12.sp)
                            Text("${item.optLong("views")} views · ${item.optLong("reactions")+item.optLong("comments")} engagements",fontSize=12.sp)
                        }
                    }
                }
            }
        }
        else LazyVerticalGrid(columns=GridCells.Fixed(3),contentPadding=PaddingValues(bottom=20.dp)){
            gridItems(filtered,key={it.id()}){item->
                Box(Modifier.fillMaxWidth().aspectRatio(.77f).padding(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable{vm.go("Content insights",item.id())}){
                    if(item.s("media_type")=="image")PrivateImage(vm,item.s("media_path"),Modifier.fillMaxSize(),targetPx=360)
                    else if(item.s("media_type")=="video")VideoThumbnail(vm,item.s("media_path"),Modifier.fillMaxSize())
                    else Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){
                        Icon(if(item.s("kind")=="reel")Icons.Outlined.SmartDisplay else Icons.Outlined.Description,null,tint=Blue)
                        Text(item.s("body").ifBlank{"Video"},Modifier.padding(6.dp),maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=12.sp)
                    }
                    Text("◉ ${item.optLong("views")}",Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha=.6f),RoundedCornerShape(topEnd=8.dp)).padding(5.dp),color=Color.White,fontSize=12.sp)
                }
            }
        }
    }
}

@Composable fun ContentInsightScreen(vm:SparkViewModel,id:String) {
    var period by remember{mutableIntStateOf(28)}
    var retry by remember{mutableIntStateOf(0)}
    var loading by remember(id,period,vm.revision,retry){mutableStateOf(true)}
    var content by remember(id,period,vm.revision,retry){mutableStateOf<JSONObject?>(null)}
    var error by remember(id,period,vm.revision,retry){mutableStateOf<String?>(null)}
    LaunchedEffect(id,period,vm.revision,retry){
        try {
            val result=JSONObject(vm.api.request("/rest/v1/rpc/sparknew_content_analytics","POST",json("days_back" to period)))
            content=result.optJSONArray("content")?.rows()?.firstOrNull{it.id()==id}
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Could not load data"}finally{loading=false}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text(if(content?.s("kind")=="reel")"Reel insights" else "Post insights",fontSize=26.sp,fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf(28,7,1).forEach{days->FilterChip(selected=period==days,onClick={period=days},label={Text(if(days==1)"Today" else "$days days")})}
        }
        if(loading)CircularProgressIndicator()
        error?.let{Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}){Text("Retry")}}
        if(!loading&&error==null&&content==null)Text("Insights are unavailable for this content. It may have been removed or be outside the available reporting history.")
        content?.let{item->
            Text(item.s("body").ifBlank{"Media post"},maxLines=3)
            if(item.s("media_path").isNotBlank())PrivateImage(vm,item.s("media_path"),Modifier.fillMaxWidth().height(240.dp),videoFrame=item.s("media_type")=="video")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                InsightMetric("Views",item.optLong("views"),Modifier.weight(1f))
                InsightMetric("Engagement",item.optLong("reactions")+item.optLong("comments"),Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                InsightMetric("Reactions",item.optLong("reactions"),Modifier.weight(1f))
                InsightMetric("Comments",item.optLong("comments"),Modifier.weight(1f))
            }
            if(item.s("kind")=="reel") {
                Text("Watch performance",fontSize=20.sp,fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    InsightMetric("Plays",item.optLong("plays"),Modifier.weight(1f))
                    InsightMetric("Unique viewers",item.optLong("unique_viewers"),Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    ReelInsightMetric("Average view duration","${item.optDouble("avg_view_seconds")}s",Modifier.weight(1f))
                    ReelInsightMetric("Total watch time","${item.optDouble("total_watch_seconds")}s",Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    ReelInsightMetric("Watched to end","${item.optDouble("completion_rate")}%",Modifier.weight(1f))
                    ReelInsightMetric("Video length","${item.optDouble("avg_video_seconds")}s",Modifier.weight(1f))
                }
                Text("Watch metrics collect from this app version onward. A play counts after one second of actual playback.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
            }
            Text("Views count distinct signed-in viewers once per day.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun InsightMetric(label:String,value:Long,modifier:Modifier){
    OutlinedCard(modifier){Column(Modifier.padding(15.dp)){Text(label);Text(value.toString(),fontSize=24.sp,fontWeight=FontWeight.Bold)}}
}
@Composable private fun ReelInsightMetric(label:String,value:String,modifier:Modifier){
    OutlinedCard(modifier){Column(Modifier.padding(12.dp)){Text(label,fontSize=12.sp);Text(value,fontSize=21.sp,fontWeight=FontWeight.Bold)}}
}

@Composable fun CommunityInbox(vm:SparkViewModel) {
    var retry by remember{mutableIntStateOf(0)}
    var loading by remember(vm.revision,retry){mutableStateOf(true)}
    var pending by remember(vm.revision,retry){mutableStateOf<List<JSONObject>>(emptyList())}
    var error by remember(vm.revision,retry){mutableStateOf<String?>(null)}
    LaunchedEffect(vm.revision,retry){
        try {
            val raw=vm.api.request("/rest/v1/rpc/sparknew_pending_comments?select=*,author:sparknew_profiles!author_id(display_name,avatar_path),post:sparknew_posts!post_id(body,media_path,media_type)&limit=100")
            pending=org.json.JSONArray(raw).rows()
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Could not load data"}finally{loading=false}
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Comments to reply to",fontSize=23.sp,fontWeight=FontWeight.Bold)}
        if(loading)item{CircularProgressIndicator()}
        error?.let{item{Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}){Text("Retry")}}}
        if(pending.isEmpty()&&error==null&&!loading)item{Text("All caught up. New comments on your posts will appear here.")}
        items(pending,key={it.id()}){comment->
            OutlinedCard(Modifier.fillMaxWidth().clickable{vm.go("Comments",comment.s("post_id"),comment.id())}){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                    Avatar(vm,comment.child("author"),44)
                    Column(Modifier.weight(1f).padding(start=12.dp)){
                        Text(comment.child("author").s("display_name"),fontWeight=FontWeight.Bold)
                        Text(comment.s("body"),maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("On: ${comment.child("post").s("body").ifBlank{"Your post"}}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                    }
                    if(comment.child("post").s("media_path").isNotBlank())PrivateImage(vm,comment.child("post").s("media_path"),Modifier.padding(start=8.dp).size(56.dp),videoFrame=comment.child("post").s("media_type")=="video",targetPx=160)
                    Icon(Icons.Outlined.Reply,"Reply",tint=Blue)
                }
            }
        }
    }
}
