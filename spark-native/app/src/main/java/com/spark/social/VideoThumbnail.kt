package com.spark.social

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val frameCache=object:LruCache<String,Bitmap>(12*1024*1024) {
    override fun sizeOf(key:String,value:Bitmap)=value.byteCount
}

@Composable fun VideoThumbnail(vm:SparkViewModel,path:String,modifier:Modifier=Modifier) {
    var frame by remember(path,vm.api.userId){ mutableStateOf(frameCache.get("${vm.api.userId}:$path")) }
    LaunchedEffect(path,vm.api.userId) {
        if(path.isBlank() || frame!=null)return@LaunchedEffect
        try {
            val access=vm.api.mediaAccess(path)
            val bitmap=withContext(Dispatchers.IO){
                val retriever=MediaMetadataRetriever()
                try {
                    retriever.setDataSource(access.url,access.headers)
                    if(Build.VERSION.SDK_INT>=27)
                        retriever.getScaledFrameAtTime(1_000_000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,360,640)
                    else retriever.getFrameAtTime(1_000_000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                        Bitmap.createScaledBitmap(it,360,640,true).also { small->if(small!==it)it.recycle() }
                    }
                }finally{retriever.release()}
            }
            bitmap?.let { frameCache.put("${vm.api.userId}:$path",it);frame=it }
        }catch(e:CancellationException){throw e}catch(_:Exception){}
    }
    Box(modifier.background(Color(0xFF172438)),contentAlignment=Alignment.Center){
        if(frame!=null)Image(frame!!.asImageBitmap(),"Video thumbnail",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(Icons.Outlined.PlayCircle,"Video thumbnail unavailable",tint=Color.White)
    }
}
