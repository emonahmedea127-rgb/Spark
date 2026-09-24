@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.spark.social

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.*
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class MapPlace(val name:String,val latitude:Double,val longitude:Double)
fun mapWorld(latitude:Double,longitude:Double,zoom:Int):Pair<Double,Double> {
 val size=256.0*(1 shl zoom);val lat=Math.toRadians(latitude.coerceIn(-85.05112878,85.05112878))
 return (longitude+180.0)/360.0*size to (1.0-ln(tan(lat)+1/cos(lat))/Math.PI)/2.0*size
}
fun worldPlace(x:Double,y:Double,zoom:Int):MapPlace {
 val size=256.0*(1 shl zoom);val wrap=((x%size)+size)%size
 return MapPlace("",Math.toDegrees(atan(sinh(Math.PI*(1-2*y.coerceIn(0.0,size)/size)))),wrap/size*360-180)
}
object SparkMapTiles {
 private var loader:ImageLoader?=null
 @Synchronized fun loader(context:Context):ImageLoader=loader?:ImageLoader.Builder(context.applicationContext)
  .okHttpClient(OkHttpClient.Builder().cache(Cache(File(context.cacheDir,"map-http"),64L*1024*1024)).connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
   .addInterceptor{chain->chain.proceed(chain.request().newBuilder().header("User-Agent","SparkAndroid/1.6 (+https://github.com/emonahmedea127-rgb/Spark)").build())}.build())
  .diskCache(null).build().also{loader=it}
}
@Suppress("DEPRECATION")
suspend fun searchMapPlaces(context:Context,query:String):List<MapPlace> = withContext(Dispatchers.IO) {
 check(Geocoder.isPresent()){ "Place search is unavailable on this device. Move the map to choose a pin." }
 Geocoder(context,Locale.getDefault()).getFromLocationName(query.trim(),6).orEmpty().map {a->
  MapPlace(listOfNotNull(a.featureName,a.locality,a.adminArea,a.countryName).distinct().joinToString(", ").take(160),a.latitude,a.longitude)
 }
}
@Suppress("DEPRECATION")
suspend fun nameMapPin(context:Context,lat:Double,lon:Double):String = withContext(Dispatchers.IO) {
 if(!Geocoder.isPresent())return@withContext ""
 val a=Geocoder(context,Locale.getDefault()).getFromLocation(lat,lon,1)?.firstOrNull()?:return@withContext ""
 listOfNotNull(a.locality?:a.subAdminArea,a.adminArea,a.countryName).distinct().joinToString(", ").take(160)
}
@Composable fun LocationPicker(initial:MapPlace?,onClose:()->Unit,onPick:(MapPlace)->Unit,search:(suspend(String)->List<MapPlace>)? = null,resolve:(suspend(Double,Double)->String)? = null,loadTiles:Boolean=true) {
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var lat by rememberSaveable{mutableDoubleStateOf(initial?.latitude?:23.8103)}
 var lon by rememberSaveable{mutableDoubleStateOf(initial?.longitude?:90.4125)}
 var zoom by rememberSaveable{mutableIntStateOf(11)}
 var label by rememberSaveable{mutableStateOf(initial?.name?:"")}
 var query by rememberSaveable{mutableStateOf("")}
 var results by remember{mutableStateOf<List<MapPlace>>(emptyList())}
 var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 var moved by rememberSaveable{mutableStateOf(initial!=null)}
 var generation by remember{mutableIntStateOf(0)}
 Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
  Surface(Modifier.fillMaxSize()){Scaffold(topBar={TopAppBar(title={Text("Choose location")},navigationIcon={IconButton(enabled=!busy,onClick=onClose){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back")}})},bottomBar={
   Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
    Text(label.ifBlank{if(moved)String.format(Locale.US,"Pin: %.4f, %.4f",lat,lon) else "Move the map to position the pin"})
    Button(enabled=moved&&!busy,onClick={scope.launch {
     busy=true;error=null
     try{val name=if(label.isNotBlank())label else try{withTimeout(10000){resolve?.invoke(lat,lon)?:nameMapPin(context,lat,lon)}}catch(e:TimeoutCancellationException){""}catch(e:CancellationException){throw e}catch(_:Exception){""}
      onPick(MapPlace(name.ifBlank{String.format(Locale.US,"Map pin %.4f, %.4f",lat,lon)},lat,lon))
     }finally{busy=false}
    }},modifier=Modifier.fillMaxWidth()){Text(if(busy)"Finding location…" else "Use this location")}
   }
  }) {padding->Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
   Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(query,{query=it},label={Text("Search city or place")},singleLine=true,modifier=Modifier.weight(1f),enabled=!busy)
    IconButton(enabled=query.trim().length>=2&&!busy,onClick={val token=++generation;scope.launch{busy=true;error=null;try{
     results=withTimeout(12000){search?.invoke(query)?:searchMapPlaces(context,query)}
     if(results.isEmpty())error="No places found. Try a nearby city or choose on the map."
    }catch(e:TimeoutCancellationException){error="Search timed out. Retry or choose on the map."}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Couldn't search. Choose on the map."}finally{if(token==generation)busy=false}}}){Icon(Icons.Outlined.Search,"Search places")}}
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   error?.let{Text(it,Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error)}
   if(results.isNotEmpty())LazyColumn(Modifier.heightIn(max=180.dp)) {items(results){place->ListItem(headlineContent={Text(place.name)},leadingContent={Icon(Icons.Outlined.Place,null)},modifier=Modifier.clickable{lat=place.latitude;lon=place.longitude;label=place.name;moved=true;results=emptyList();zoom=12})}}
   SlippyMap(lat,lon,zoom,{p->lat=p.latitude;lon=p.longitude;label="";moved=true},onZoom={zoom=it},modifier=Modifier.weight(1f).fillMaxWidth().padding(top=10.dp),loadTiles=loadTiles,enabled=!busy)
  }}}
 }
}
@Composable fun SlippyMap(latitude:Double,longitude:Double,zoom:Int,onMove:(MapPlace)->Unit,onZoom:(Int)->Unit,modifier:Modifier=Modifier,loadTiles:Boolean=true,enabled:Boolean=true) {
 val context=LocalContext.current;val density=LocalDensity.current;val uri=LocalUriHandler.current
 var tileError by remember {mutableStateOf(false)}
 var retry by remember {mutableIntStateOf(0)}
 val currentMove by rememberUpdatedState(onMove)
 val center by rememberUpdatedState(mapWorld(latitude,longitude,zoom))
 BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surfaceVariant).clipToBounds().testTag("location-map").pointerInput(zoom,enabled){if(enabled)detectDragGestures{change,drag->change.consume();currentMove(worldPlace(center.first-drag.x/density.density,center.second-drag.y/density.density,zoom))}}.pointerInput(zoom,enabled){if(enabled)detectTapGestures{tap->currentMove(worldPlace(center.first+(tap.x-size.width/2)/density.density,center.second+(tap.y-size.height/2)/density.density,zoom))}}) {
  val width=maxWidth.value.toDouble();val height=maxHeight.value.toDouble();val originX=center.first-width/2;val originY=center.second-height/2
  val count=1 shl zoom
  if(loadTiles)for(x in floor(originX/256).toInt()..floor((originX+width)/256).toInt())for(y in floor(originY/256).toInt()..floor((originY+height)/256).toInt())if(y in 0 until count)key(zoom,x,y){
   val wrapped=((x%count)+count)%count
   AsyncImage(model=ImageRequest.Builder(context).data("https://tile.openstreetmap.org/$zoom/$wrapped/$y.png").setParameter("retry",retry).crossfade(false).build(),onError={tileError=true},imageLoader=SparkMapTiles.loader(context),contentDescription=null,
    modifier=Modifier.offset{IntOffset(((x*256-originX)*density.density).roundToInt(),((y*256-originY)*density.density).roundToInt())}.requiredSize(256.dp))
  }
  Icon(Icons.Outlined.LocationOn,"Selected map pin",Modifier.align(Alignment.Center).offset(y=(-18).dp).size(40.dp),tint=MaterialTheme.colorScheme.primary)
  Column(Modifier.align(Alignment.TopEnd).padding(12.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(12.dp))){IconButton(enabled=enabled&&zoom<18,onClick={onZoom(zoom+1)}){Icon(Icons.Outlined.Add,"Zoom in")};IconButton(enabled=enabled&&zoom>2,onClick={onZoom(zoom-1)}){Icon(Icons.Outlined.Remove,"Zoom out")}}
  if(tileError)TextButton(onClick={tileError=false;retry++},modifier=Modifier.align(Alignment.TopStart).background(MaterialTheme.colorScheme.surface.copy(alpha=.95f))){Text("Map unavailable · Retry")}
  Text("© OpenStreetMap contributors",Modifier.align(Alignment.BottomStart).background(MaterialTheme.colorScheme.surface.copy(alpha=.95f)).clickable{uri.openUri("https://www.openstreetmap.org/copyright")}.padding(6.dp),style=MaterialTheme.typography.labelSmall)
 }
}
