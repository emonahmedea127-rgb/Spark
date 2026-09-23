package com.spark.social

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

internal data class PhotoCrop(val scale:Float,val left:Float,val top:Float)
internal fun cropTransform(imageWidth:Int,imageHeight:Int,frameWidth:Int,frameHeight:Int,zoom:Float,pan:Offset):PhotoCrop {
    require(imageWidth>0&&imageHeight>0&&frameWidth>0&&frameHeight>0)
    val scale=max(frameWidth.toFloat()/imageWidth,frameHeight.toFloat()/imageHeight)*zoom.coerceIn(1f,4f)
    val maxX=((imageWidth*scale-frameWidth)/2).coerceAtLeast(0f)
    val maxY=((imageHeight*scale-frameHeight)/2).coerceAtLeast(0f)
    return PhotoCrop(scale,-maxX+pan.x.coerceIn(-maxX,maxX),-maxY+pan.y.coerceIn(-maxY,maxY))
}
internal fun croppedBitmap(bitmap:Bitmap,frame:IntSize,zoom:Float,pan:Offset,cover:Boolean):Bitmap {
    val transform=cropTransform(bitmap.width,bitmap.height,frame.width,frame.height,zoom,pan)
    val width=if(cover)1600 else 1024;val height=if(cover)900 else 1024
    val factor=width.toFloat()/frame.width
    return Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply {
        val matrix=Matrix().apply {setScale(transform.scale*factor,transform.scale*factor);postTranslate(transform.left*factor,transform.top*factor)}
        AndroidCanvas(this).apply {drawColor(android.graphics.Color.WHITE);drawBitmap(bitmap,matrix,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))}
    }
}

@Composable fun ProfilePhotoCropper(vm:SparkViewModel,uri:Uri,field:String,onClose:()->Unit) {
    val context=LocalContext.current
    val cover=field=="cover_path"
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf<String?>(null) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var pan by remember(uri) { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(uri) {
        try { bitmap=withContext(Dispatchers.IO) {
            val bytes=context.contentResolver.openInputStream(uri)?.use { input->
                val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {val n=input.read(buffer);if(n<0)break;require(out.size()+n<=25*1024*1024) {"Choose a photo under 25 MB."};out.write(buffer,0,n)}
                out.toByteArray()
            }?:error("Cannot open this photo.")
            val normalized=optimizePhoto(bytes).first
            BitmapFactory.decodeByteArray(normalized,0,normalized.size)?:error("Choose a valid photo.")
        } }catch(e:CancellationException) {throw e}catch(e:Exception) {failed=e.message}
    }
    Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(enabled=!busy,onClick=onClose) {Icon(Icons.Outlined.Close,"Cancel crop",tint=Color.White)}
                Text(if(cover)"Adjust cover photo" else "Adjust profile picture",color=Color.White)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                val bm=bitmap
                if(bm!=null) {
                    val aspect=if(cover)16f/9f else 1f
                    val width=minOf(maxWidth-32.dp,maxHeight*aspect)
                    val image=remember(bm) {bm.asImageBitmap()}
                    Canvas(Modifier.width(width).aspectRatio(aspect).onSizeChanged {frame=it}.pointerInput(bm,busy) {
                        detectTransformGestures {_,move,scale,_->
                            if(!busy&&frame.width>0) {
                                zoom=(zoom*scale).coerceIn(1f,4f)
                                val t=cropTransform(bm.width,bm.height,frame.width,frame.height,zoom,pan+move)
                                val maxX=(bm.width*t.scale-frame.width)/2;val maxY=(bm.height*t.scale-frame.height)/2
                                pan=Offset(t.left+maxX,t.top+maxY)
                            }
                        }
                    }) {
                        val t=cropTransform(bm.width,bm.height,size.width.roundToInt(),size.height.roundToInt(),zoom,pan)
                        drawImage(image,dstOffset=IntOffset(t.left.roundToInt(),t.top.roundToInt()),dstSize=IntSize((bm.width*t.scale).roundToInt(),(bm.height*t.scale).roundToInt()))
                        if(!cover) {
                            val mask=Path().apply {fillType=PathFillType.EvenOdd;addRect(Rect(0f,0f,size.width,size.height));addOval(Rect(0f,0f,size.width,size.height))}
                            drawPath(mask,Color.Black.copy(alpha=.65f));drawCircle(Color.White,style=Stroke(2.dp.toPx()))
                        }else {
                            drawRect(Color.White,style=Stroke(2.dp.toPx()))
                            for(i in 1..2) {drawLine(Color.White.copy(alpha=.35f),Offset(size.width*i/3,0f),Offset(size.width*i/3,size.height));drawLine(Color.White.copy(alpha=.35f),Offset(0f,size.height*i/3),Offset(size.width,size.height*i/3))}
                        }
                    }
                }else if(failed!=null)Text(failed!!,color=Color.White,modifier=Modifier.padding(24.dp))
                else CircularProgressIndicator(color=Color.White)
            }
            Text("Drag to position · Pinch or slide to zoom",color=Color.White,modifier=Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
            Slider(value=zoom,onValueChange={zoom=it},valueRange=1f..4f,enabled=!busy&&bitmap!=null,modifier=Modifier.padding(horizontal=28.dp))
            TextButton(enabled=!busy,onClick={zoom=1f;pan=Offset.Zero},modifier=Modifier.align(Alignment.CenterHorizontally)) {Text("Reset",color=Color.White)}
            Text("Your updated photo will also be shared to your public feed.",color=Color.LightGray,modifier=Modifier.padding(horizontal=24.dp,vertical=8.dp))
            Button(enabled=!busy&&bitmap!=null&&frame.width>0,onClick={
                val bm=bitmap!!;val cropFrame=frame;val cropZoom=zoom;val cropPan=pan
                busy=true
                vm.work {
                    var path:String?=null;var committed=false
                    try {
                        val bytes=withContext(Dispatchers.Default) {
                            val cropped=croppedBitmap(bm,cropFrame,cropZoom,cropPan,cover)
                            try {ByteArrayOutputStream().use {out->cropped.compress(Bitmap.CompressFormat.JPEG,88,out);out.toByteArray()}}finally {cropped.recycle()}
                        }
                        path=vm.api.uploadCroppedPhoto(bytes)
                        vm.api.update("profiles","id=eq.${vm.api.userId}",json(field to path))
                        committed=true
                        vm.me=vm.me?.let {org.json.JSONObject(it.toString()).put(field,path)}
                        vm.refresh();vm.notice="Photo updated and shared to your feed.";onClose()
                    }catch(e:Exception) {
                        if(!committed&&path!=null&&(e is IllegalArgumentException||(e is ApiException&&e.status in 400..499)))runCatching {vm.api.removeMedia(path!!)}
                        throw e
                    }finally {busy=false}
                }
            },modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp)) {
                if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Text("Update and share")
            }
        }
    }
}
