package com.nalaro.pixelstudio.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nalaro.pixelstudio.ui.theme.AccentPrimary
import com.nalaro.pixelstudio.ui.theme.SurfaceRaised

@Composable
fun BeforeAfterSlider(
    originalBitmap: Bitmap?,
    processedBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableStateOf(0.5f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceRaised),
        contentAlignment = Alignment.Center
    ) {
        val totalWidth = constraints.maxWidth.toFloat()
        val totalHeight = constraints.maxHeight.toFloat()

        if (originalBitmap != null && processedBitmap != null) {
            val origImage = remember(originalBitmap) { originalBitmap.asImageBitmap() }
            val procImage = remember(processedBitmap) { processedBitmap.asImageBitmap() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            splitFraction = (change.position.x / totalWidth).coerceIn(0.05f, 0.95f)
                        }
                    }
            ) {
                val canvasW = size.width.toInt()
                val canvasH = size.height.toInt()
                val splitX = (size.width * splitFraction).toInt()

                // Draw Processed (Right / Full background)
                drawImage(
                    image = procImage,
                    dstSize = IntSize(canvasW, canvasH)
                )

                // Draw Original (Left / Clipped)
                if (splitX > 0) {
                    val srcRatioX = originalBitmap.width.toFloat() / canvasW.toFloat()
                    val clipSrcW = (splitX * srcRatioX).toInt().coerceIn(1, originalBitmap.width)

                    drawImage(
                        image = origImage,
                        srcOffset = IntOffset(0, 0),
                        srcSize = IntSize(clipSrcW, originalBitmap.height),
                        dstOffset = IntOffset(0, 0),
                        dstSize = IntSize(splitX, canvasH)
                    )
                }

                // Draw Divider line
                drawLine(
                    color = AccentPrimary,
                    start = androidx.compose.ui.geometry.Offset(splitX.toFloat(), 0f),
                    end = androidx.compose.ui.geometry.Offset(splitX.toFloat(), size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Divider Handle badge
            Box(
                modifier = Modifier
                    .offset(x = ((totalWidth * splitFraction) - 20.dp.value).dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CompareArrows,
                    contentDescription = "Slide to compare",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Left / Right Indicator chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Original",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = "Processed",
                    color = AccentPrimary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            Text(text = "No image loaded", color = Color.Gray)
        }
    }
}
