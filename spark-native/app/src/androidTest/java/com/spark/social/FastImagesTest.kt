package com.spark.social

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class FastImagesTest {
    @Test fun duplicateRequestsFetchOnceAndAccountSwitchDoesNotReusePrivateBytes()=runBlocking {
        FastImages.clear()
        val requests=AtomicInteger()
        val values=(1..8).map { async { FastImages.cached("user-a","photo",{requests.incrementAndGet();delay(50);byteArrayOf(1,2,3)}) } }.awaitAll()
        assertEquals(1,requests.get())
        values.forEach { assertArrayEquals(byteArrayOf(1,2,3),it) }
        val other=FastImages.cached("user-b","photo",{requests.incrementAndGet();byteArrayOf(9)})
        assertArrayEquals(byteArrayOf(9),other);assertEquals(2,requests.get())
        FastImages.clear()
        FastImages.cached("user-b","photo",{requests.incrementAndGet();byteArrayOf(9)})
        assertEquals(3,requests.get())
    }
    @Test fun logoutRejectsAnInflightPrivateDownload()=runBlocking {
        FastImages.clear()
        val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        val result=async { runCatching { FastImages.cached("user-a","late",{started.complete(Unit);finish.await();byteArrayOf(1)}) } }
        started.await();FastImages.clear();finish.complete(Unit)
        assertTrue(result.await().isFailure)
    }
    @Test fun uploadPhotoIsBoundedAndSmaller() {
        val bitmap=Bitmap.createBitmap(4096,3072,Bitmap.Config.RGB_565)
        val canvas=Canvas(bitmap);val paint=Paint()
        for(x in 0 until 4096 step 8) { paint.color=Color.rgb(x%256,(x/16)%256,100);canvas.drawRect(x.toFloat(),0f,(x+8).toFloat(),3072f,paint) }
        val original=ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG,100,this) }.toByteArray();bitmap.recycle()
        val optimized=optimizePhoto(original)
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(optimized.first,0,optimized.first.size,bounds)
        assertEquals(2048,bounds.outWidth);assertEquals(1536,bounds.outHeight)
        assertTrue("Optimized upload should reduce encoded bytes",optimized.first.size<original.size)
        assertEquals("image/jpeg",optimized.second)
    }
    @Test fun exifOrientationIsAppliedBeforeMetadataIsRemoved() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=java.io.File(context.cacheDir,"orientation-test.jpg")
        val bitmap=Bitmap.createBitmap(400,200,Bitmap.Config.RGB_565)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) };bitmap.recycle()
        ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION,"6");saveAttributes() }
        val bytes=optimizePhoto(file.readBytes()).first;file.delete()
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        assertEquals(200,bounds.outWidth);assertEquals(400,bounds.outHeight)
    }
    @Test fun byteBufferPhotosDecodeAndSecondRequestUsesBitmapCache()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val image=Bitmap.createBitmap(800,600,Bitmap.Config.RGB_565)
        val bytes=ByteArrayOutputStream().apply { image.compress(Bitmap.CompressFormat.JPEG,85,this) }.toByteArray();image.recycle()
        val loader=FastImages.loader(context)
        val request=ImageRequest.Builder(context).data(ByteBuffer.wrap(bytes)).size(600,600).memoryCacheKey("fixture-photo").build()
        assertTrue(loader.execute(request) is SuccessResult)
        val second=loader.execute(request)
        assertTrue(second is SuccessResult)
        assertEquals(DataSource.MEMORY_CACHE,(second as SuccessResult).dataSource)
        FastImages.clear()
    }
}
