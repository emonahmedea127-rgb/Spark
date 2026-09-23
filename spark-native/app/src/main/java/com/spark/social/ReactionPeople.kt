@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.spark.social
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable fun ReactionPeopleDialog(vm:SparkViewModel,post:String,summary:List<JSONObject>,onClose:()->Unit,loadPeople:suspend(String,Int)->List<JSONObject> = {filter,offset->
    vm.api.rows("reactions","select=user_id,reaction,person:sparknew_profiles!user_id(id,display_name,avatar_path)&post_id=eq.$post&order=user_id&limit=50&offset=$offset"+(if(filter.isBlank())"" else "&reaction=eq.$filter"))
}) {
    var filter by remember {mutableStateOf("")}
    var people by remember {mutableStateOf(emptyList<JSONObject>())}
    var offset by remember {mutableIntStateOf(0)}
    var more by remember {mutableStateOf(true)}
    var loading by remember {mutableStateOf(false)}
    var failed by remember {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    var generation by remember {mutableIntStateOf(0)}
    LaunchedEffect(filter,retry) {
        generation++
        offset=0;people=emptyList();more=true;loading=true;failed=false
        try {
            val next=loadPeople(filter,0)
            people=next;offset=next.size;more=next.size==50
        }catch(e:CancellationException) {throw e}catch(e:Exception) {failed=true;vm.notice=e.message}finally {loading=false}
    }
    val scope=rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.8f)) {
            Row(Modifier.padding(start=20.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {Text("Reactions",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton(onClick=onClose) {Icon(Icons.Outlined.Close,"Close reactions")}}
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=filter.isBlank(),onClick={filter=""},label={Text("All ${summary.size}")})
                reactionEmoji.forEach { (value,emoji)->val count=summary.count {it.s("reaction")==value};if(count>0)FilterChip(selected=filter==value,onClick={filter=value},label={Text("$emoji $count")}) }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(people,key={it.s("user_id")}) {row->
                    val p=row.child("person")
                    Row(Modifier.fillMaxWidth().clickable(enabled=p.id().isNotBlank()) {onClose();vm.go("Profile",row.s("user_id"))}.padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Avatar(vm,p,44) {if(p.id().isNotBlank()) {onClose();vm.go("Profile",row.s("user_id"))}}
                        Text(p.s("display_name").ifBlank {"Spark member"},Modifier.weight(1f).padding(horizontal=12.dp),fontWeight=FontWeight.SemiBold)
                        Text(reactionEmoji[row.s("reaction")]?:"👍",fontSize=24.sp)
                    }
                }
                if(loading)item {Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) {CircularProgressIndicator()}}
                if(failed)item {TextButton(onClick={retry++},modifier=Modifier.fillMaxWidth()) {Text("Couldn't load reactions. Retry")}}
                else if(!loading&&people.isEmpty())item {Text("No reactions yet",Modifier.padding(24.dp))}
                else if(more&&!loading)item {TextButton(onClick={val request=generation;val selected=filter;scope.launch {
                    loading=true
                    try {
                        val next=loadPeople(selected,offset)
                        if(request==generation&&selected==filter) {people=(people+next).distinctBy {it.s("user_id")};offset+=next.size;more=next.size==50}
                    }catch(e:CancellationException) {throw e}catch(e:Exception) {if(request==generation) {failed=true;vm.notice=e.message}}finally {if(request==generation)loading=false}
                }},modifier=Modifier.fillMaxWidth()) {Text("Load more")}}
            }
        }
    }
}
