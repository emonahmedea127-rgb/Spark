package com.example.sociva.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SocivaIndigo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CropType(val label: String, val aspectRatio: Float) {
  PROFILE("Profile Picture", 1.0f),
  COVER("Cover Photo", 2.3f)
}

@Composable
fun ImageCropperModal(
  sourceBitmap: Bitmap,
  cropType: CropType,
  onCropCompleted: (Bitmap) -> Unit,
  onDismiss: () -> Unit
) {
  var scale by remember { mutableStateOf(1.0f) }
  var offsetX by remember { mutableStateOf(0f) }
  var offsetY by remember { mutableStateOf(0f) }
  var rotationDegrees by remember { mutableStateOf(0f) }
  var isProcessing by remember { mutableStateOf(false) }

  var containerSize by remember { mutableStateOf(IntSize.Zero) }
  val coroutineScope = rememberCoroutineScope()

  val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
    scale = (scale * zoomChange).coerceIn(1.0f, 4.5f)
    offsetX += panChange.x
    offsetY += panChange.y
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0F172A)),
      color = Color(0xFF0F172A)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .systemBarsPadding()
      ) {
        // 1. Top Header Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          IconButton(onClick = onDismiss) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Cancel",
              tint = Color.White
            )
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = if (cropType == CropType.PROFILE) "Crop Profile Picture" else "Adjust Cover Photo",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
            Text(
              text = "Drag to reposition • Pinch to zoom",
              color = Color(0xFF94A3B8),
              fontSize = 12.sp
            )
          }

          IconButton(
            onClick = {
              scale = 1.0f
              offsetX = 0f
              offsetY = 0f
              rotationDegrees = 0f
            }
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Reset Alignment",
              tint = Color.White
            )
          }
        }

        // 2. Interactive Cropper Canvas Area
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .onSizeChanged { containerSize = it }
            .transformable(state = transformableState)
            .pointerInput(Unit) {
              detectDragGestures { change, dragAmount ->
                change.consume()
                offsetX += dragAmount.x
                offsetY += dragAmount.y
              }
            },
          contentAlignment = Alignment.Center
        ) {
          if (containerSize.width > 0 && containerSize.height > 0) {
            val viewW = containerSize.width.toFloat()
            val viewH = containerSize.height.toFloat()

            // Calculate crop window
            val cropBoxWidth: Float
            val cropBoxHeight: Float
            if (cropType == CropType.PROFILE) {
              val size = min(viewW * 0.85f, viewH * 0.55f)
              cropBoxWidth = size
              cropBoxHeight = size
            } else {
              val w = viewW * 0.92f
              cropBoxWidth = w
              cropBoxHeight = w / cropType.aspectRatio
            }

            val cropRect = Rect(
              left = (viewW - cropBoxWidth) / 2f,
              top = (viewH - cropBoxHeight) / 2f,
              right = (viewW + cropBoxWidth) / 2f,
              bottom = (viewH + cropBoxHeight) / 2f
            )

            // Canvas drawing image and overlay
            val imageBitmap = remember(sourceBitmap, rotationDegrees) {
              if (rotationDegrees == 0f) {
                sourceBitmap.asImageBitmap()
              } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(
                  sourceBitmap,
                  0,
                  0,
                  sourceBitmap.width,
                  sourceBitmap.height,
                  matrix,
                  true
                ).asImageBitmap()
              }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
              // 1. Draw base image with current transform
              val imgW = imageBitmap.width.toFloat()
              val imgH = imageBitmap.height.toFloat()

              // Base scale to fit within view
              val fitScale = max(cropBoxWidth / imgW, cropBoxHeight / imgH) * scale
              val dstW = imgW * fitScale
              val dstH = imgH * fitScale

              val dstLeft = (viewW - dstW) / 2f + offsetX
              val dstTop = (viewH - dstH) / 2f + offsetY

              drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(dstLeft.roundToInt(), dstTop.roundToInt()),
                dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt())
              )

              // 2. Dim Scrim overlay with cut-out hole
              val path = Path().apply {
                if (cropType == CropType.PROFILE) {
                  addOval(cropRect)
                } else {
                  addRect(cropRect)
                }
              }

              clipPath(path, clipOp = ClipOp.Difference) {
                drawRect(color = Color(0xAA000000))
              }

              // 3. Draw boundary guide
              if (cropType == CropType.PROFILE) {
                drawCircle(
                  color = Color.White,
                  radius = cropBoxWidth / 2f,
                  center = cropRect.center,
                  style = Stroke(width = 2.dp.toPx())
                )
              } else {
                drawRect(
                  color = Color.White,
                  topLeft = cropRect.topLeft,
                  size = cropRect.size,
                  style = Stroke(width = 2.dp.toPx())
                )
                // Thirds guide lines
                val oneThirdW = cropBoxWidth / 3f
                val oneThirdH = cropBoxHeight / 3f
                drawLine(
                  color = Color(0x66FFFFFF),
                  start = Offset(cropRect.left + oneThirdW, cropRect.top),
                  end = Offset(cropRect.left + oneThirdW, cropRect.bottom),
                  strokeWidth = 1.dp.toPx()
                )
                drawLine(
                  color = Color(0x66FFFFFF),
                  start = Offset(cropRect.left + oneThirdW * 2, cropRect.top),
                  end = Offset(cropRect.left + oneThirdW * 2, cropRect.bottom),
                  strokeWidth = 1.dp.toPx()
                )
                drawLine(
                  color = Color(0x66FFFFFF),
                  start = Offset(cropRect.left, cropRect.top + oneThirdH),
                  end = Offset(cropRect.right, cropRect.top + oneThirdH),
                  strokeWidth = 1.dp.toPx()
                )
                drawLine(
                  color = Color(0x66FFFFFF),
                  start = Offset(cropRect.left, cropRect.top + oneThirdH * 2),
                  end = Offset(cropRect.right, cropRect.top + oneThirdH * 2),
                  strokeWidth = 1.dp.toPx()
                )
              }
            }
          }
        }

        // 3. Fine-tuning Toolbar: Zoom Slider, 90° Rotate
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
          shape = RoundedCornerShape(16.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.ZoomIn,
                  contentDescription = "Zoom",
                  tint = Color(0xFF94A3B8),
                  modifier = Modifier.size(20.dp)
                )
                Text(
                  text = "Zoom: ${"%.1f".format(scale)}x",
                  color = Color.White,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium
                )
              }

              OutlinedButton(
                onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.RotateRight,
                  contentDescription = "Rotate 90 degrees",
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rotate", fontSize = 12.sp)
              }
            }

            Slider(
              value = scale,
              onValueChange = { scale = it },
              valueRange = 1.0f..4.0f,
              colors = SliderDefaults.colors(
                thumbColor = SocivaIndigo,
                activeTrackColor = SocivaIndigo,
                inactiveTrackColor = Color(0xFF334155)
              ),
              modifier = Modifier.fillMaxWidth()
            )
          }
        }

        // 4. Action Buttons (Cancel / Confirm Crop)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
          ) {
            Text("Cancel", fontWeight = FontWeight.SemiBold)
          }

          Button(
            onClick = {
              if (isProcessing) return@Button
              isProcessing = true

              coroutineScope.launch {
                val cropped = withContext(Dispatchers.IO) {
                  extractCroppedBitmap(
                    sourceBitmap = sourceBitmap,
                    cropType = cropType,
                    containerSize = containerSize,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    rotationDegrees = rotationDegrees
                  )
                }
                isProcessing = false
                onCropCompleted(cropped)
              }
            },
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("apply_crop_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SocivaIndigo)
          ) {
            if (isProcessing) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
              )
            } else {
              Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Save & Apply", fontWeight = FontWeight.Bold)
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}

/**
 * Helper to calculate and extract cropped bitmap.
 */
private fun extractCroppedBitmap(
  sourceBitmap: Bitmap,
  cropType: CropType,
  containerSize: IntSize,
  scale: Float,
  offsetX: Float,
  offsetY: Float,
  rotationDegrees: Float
): Bitmap {
  return try {
    // 1. Rotate if needed
    val rotated = if (rotationDegrees != 0f) {
      val matrix = Matrix().apply { postRotate(rotationDegrees) }
      Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true)
    } else {
      sourceBitmap
    }

    val viewW = containerSize.width.toFloat()
    val viewH = containerSize.height.toFloat()

    val cropBoxWidth: Float
    val cropBoxHeight: Float
    if (cropType == CropType.PROFILE) {
      val size = min(viewW * 0.85f, viewH * 0.55f)
      cropBoxWidth = size
      cropBoxHeight = size
    } else {
      val w = viewW * 0.92f
      cropBoxWidth = w
      cropBoxHeight = w / cropType.aspectRatio
    }

    val cropRect = Rect(
      left = (viewW - cropBoxWidth) / 2f,
      top = (viewH - cropBoxHeight) / 2f,
      right = (viewW + cropBoxWidth) / 2f,
      bottom = (viewH + cropBoxHeight) / 2f
    )

    val imgW = rotated.width.toFloat()
    val imgH = rotated.height.toFloat()

    val fitScale = max(cropBoxWidth / imgW, cropBoxHeight / imgH) * scale
    val dstW = imgW * fitScale
    val dstH = imgH * fitScale

    val dstLeft = (viewW - dstW) / 2f + offsetX
    val dstTop = (viewH - dstH) / 2f + offsetY

    // Map crop window back to rotated bitmap space
    val normalizedCropX = ((cropRect.left - dstLeft) / dstW * imgW).roundToInt()
    val normalizedCropY = ((cropRect.top - dstTop) / dstH * imgH).roundToInt()
    val normalizedCropW = (cropBoxWidth / dstW * imgW).roundToInt()
    val normalizedCropH = (cropBoxHeight / dstH * imgH).roundToInt()

    val safeX = normalizedCropX.coerceIn(0, rotated.width - 1)
    val safeY = normalizedCropY.coerceIn(0, rotated.height - 1)
    val safeW = normalizedCropW.coerceIn(1, rotated.width - safeX)
    val safeH = normalizedCropH.coerceIn(1, rotated.height - safeY)

    Bitmap.createBitmap(rotated, safeX, safeY, safeW, safeH)
  } catch (e: Exception) {
    sourceBitmap
  }
}
