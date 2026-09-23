package com.spark.social

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.SystemClock
import android.util.LruCache
import coil.ImageLoader
import coil.memory.MemoryCache
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Bounded, account-scoped RAM cache. No private photos are written to disk. */
object FastImages {
    private data class Entry(val bytes:ByteArray,val created:Long)
    private const val TTL=5*60*1000L
    private val cache=object:LruCache<String,Entry>(32*1024*1024) {
        override fun sizeOf(key:String,value:Entry)=value.bytes.size
    }
    private val locks=Array(32) { Mutex() }
    private var owner=""
    private var generation=0
    private var shared:ImageLoader?=null
    @Synchronized fun loader(context:Context):ImageLoader = shared?:ImageLoader.Builder(context.applicationContext)
        .memoryCache { MemoryCache.Builder(context.applicationContext).maxSizePercent(.15).build() }
        .diskCache(null).build().also { shared=it }
    @Synchronized fun clear() { generation++;owner="";cache.evictAll();shared?.memoryCache?.clear() }
    @Synchronized private fun scope(account:String):Int {
        if(owner!=account) { clear();owner=account };return generation
    }
    suspend fun bytes(api:SparkApi,path:String):ByteArray {
        check(api.signedIn) { "Please sign in again." }
        val account=api.userId
        return cached(account,path,{api.downloadMedia(path)},{api.userId==account&&api.signedIn})
    }
    internal suspend fun cached(account:String,path:String,fetch:suspend ()->ByteArray,valid:()->Boolean={true}):ByteArray {
        val epoch=scope(account)
        val key="$account:$path"
        return locks[(key.hashCode() and Int.MAX_VALUE)%locks.size].withLock {
            synchronized(this) { cache.get(key)?.takeIf { SystemClock.elapsedRealtime()-it.created<TTL } }?.let {
                check(valid());return@withLock it.bytes
            }
            val bytes=fetch()
            synchronized(this) {
                check(owner==account&&epoch==generation&&valid()) { "Media session changed." }
                cache.put(key,Entry(bytes,SystemClock.elapsedRealtime()))
            }
            bytes
        }
    }
}

/** Bounds decoded pixels, applies EXIF rotation and removes camera metadata. */
internal fun optimizePhoto(input:ByteArray,maxEdge:Int=2048):Pair<ByteArray,String> {
    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    BitmapFactory.decodeByteArray(input,0,input.size,bounds)
    require(bounds.outWidth>0&&bounds.outHeight>0) { "This photo cannot be decoded." }
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxEdge*2)sample*=2
    val decoded=BitmapFactory.decodeByteArray(input,0,input.size,BitmapFactory.Options().apply { inSampleSize=sample })?:error("Cannot open photo.")
    val orientation=runCatching { ExifInterface(ByteArrayInputStream(input)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(1)
    val matrix=Matrix().apply {
        when(orientation) {
            2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
            5->{setRotate(90f);postScale(-1f,1f)}
            6->setRotate(90f);7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)
        }
        val ratio=(maxEdge.toFloat()/maxOf(decoded.width,decoded.height)).coerceAtMost(1f)
        postScale(ratio,ratio)
    }
    val bitmap=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
    val alpha=bitmap.hasAlpha()
    val output=ByteArrayOutputStream()
    @Suppress("DEPRECATION") val format=if(alpha)Bitmap.CompressFormat.WEBP else Bitmap.CompressFormat.JPEG
    bitmap.compress(format,82,output)
    if(bitmap!==decoded)decoded.recycle()
    bitmap.recycle()
    return output.toByteArray() to if(alpha)"image/webp" else "image/jpeg"
}
