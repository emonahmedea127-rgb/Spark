package com.spark.social
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PhotoCropTest {
    @Test fun repositionExportsTheChosenPartOfPhoto() {
        val source=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888)
        Canvas(source).apply {drawColor(Color.RED);drawRect(200f,0f,400f,200f,Paint().apply {color=Color.BLUE})}
        val left=croppedBitmap(source,IntSize(200,200),1f,Offset(10000f,0f),false)
        val right=croppedBitmap(source,IntSize(200,200),1f,Offset(-10000f,0f),false)
        assertEquals(1024,left.width);assertEquals(1024,left.height)
        assertEquals(Color.RED,left.getPixel(512,512));assertEquals(Color.BLUE,right.getPixel(512,512))
        source.recycle();left.recycle();right.recycle()
    }
    @Test fun zoomAndPanNeverExposeEmptyEdges() {
        for(zoom in listOf(.1f,1f,2f,4f,99f))for(pan in listOf(Offset(10000f,-10000f),Offset(-10000f,10000f))) {
            val t=cropTransform(200,400,320,180,zoom,pan)
            assertTrue(t.left<=.01f);assertTrue(t.top<=.01f)
            assertTrue(t.left+200*t.scale>=319.99f);assertTrue(t.top+400*t.scale>=179.99f)
        }
        val source=Bitmap.createBitmap(200,400,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GREEN)}
        val cover=croppedBitmap(source,IntSize(320,180),3f,Offset(10000f,-10000f),true)
        assertEquals(1600,cover.width);assertEquals(900,cover.height)
        assertEquals(Color.GREEN,cover.getPixel(0,0));assertEquals(Color.GREEN,cover.getPixel(1599,899))
        source.recycle();cover.recycle()
    }
}
