package com.webgenius.spark

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.webgenius.spark.core.validateVideo
import java.io.ByteArrayOutputStream

suspend fun prepareVideo(context: Context,uri: Uri): ByteArray=withContext(Dispatchers.IO) {
 val metadata=MediaMetadataRetriever()
 try {
  metadata.setDataSource(context,uri)
  val duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
  require(duration in 1..60_000) { "Choose a video up to 60 seconds." }
 } finally { metadata.release() }
 val out=ByteArrayOutputStream()
 requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
  val buffer=ByteArray(32768)
  while(true) { val n=input.read(buffer);if(n<0)break;require(out.size()+n<=20*1024*1024) { "Video must be smaller than 20 MB." };out.write(buffer,0,n) }
 }
 out.toByteArray().also(::validateVideo)
}
