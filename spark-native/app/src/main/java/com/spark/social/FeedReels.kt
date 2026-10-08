package com.spark.social
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import org.json.JSONArray

@Composable fun FeedReels(vm:SparkViewModel) {
    Rows(vm,"feed-reels",{JSONArray(vm.api.request("/rest/v1/rpc/sparknew_friend_reels?${vm.api.postSelect}")).rows()}){reels->
        if(reels.isNotEmpty())Column(Modifier.fillMaxWidth().padding(vertical=12.dp)) {
            Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.SmartDisplay,null,tint=Blue);Text(" Reels",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=21.sp);TextButton(onClick={vm.go("Reels")}){Text("See all")}}
            LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                items(reels,key={it.id()}){reel->Card(onClick={vm.go("Reels",reel.id())},modifier=Modifier.width(164.dp).height(252.dp),shape=RoundedCornerShape(14.dp)) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF172438))) {
                        VideoThumbnail(vm,reel.s("media_path"),Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.3f)))
                        Icon(Icons.Outlined.PlayCircle,"Play reel",Modifier.align(Alignment.Center).size(48.dp),tint=Color.White)
                        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)){Text(reel.child("author").s("display_name"),color=Color.White,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(reel.s("body"),color=Color.White,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=13.sp)}
                    }
                }}
            }
            HorizontalDivider(Modifier.padding(top=16.dp))
        }
    }
}
