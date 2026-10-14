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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nalaro.pixelstudio.ui.theme.AccentPrimary
import com.nalaro.pixelstudio.ui.theme.SurfaceRaised
import kotlin.math.min

@Composable
fun BeforeAfterSlider(
    originalBitmap: Bitmap?,
    processedBitmap: Bitmap?,
    showFullProcessedOnly: Boolean = false,
    onCanvasTouch: ((normX: Float, normY: Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceRaised),
        contentAlignment = Alignment.Center
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val totalHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val boxWidthDp = maxWidth

        if (originalBitmap != null && processedBitmap != null &&
            !originalBitmap.isRecycled && !processedBitmap.isRecycled
        ) {
            val origImage = remember(originalBitmap) { originalBitmap.asImageBitmap() }
            val procImage = remember(processedBitmap) { processedBitmap.asImageBitmap() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(totalWidthPx, totalHeightPx, showFullProcessedOnly, onCanvasTouch != null) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            if (onCanvasTouch != null) {
                                val nx = (change.position.x / totalWidthPx).coerceIn(0f, 1f)
                                val ny = (change.position.y / totalHeightPx).coerceIn(0f, 1f)
                                onCanvasTouch(nx, ny)
                            } else if (!showFullProcessedOnly) {
                                splitFraction = (change.position.x / totalWidthPx).coerceIn(0.05f, 0.95f)
                            }
                        }
                    }
            ) {
                val canvasW = size.width.toInt()
                val canvasH = size.height.toInt()
                if (canvasW <= 0 || canvasH <= 0) return@Canvas

                // Draw transparency checkerboard background so transparent cutouts are unmistakably visible
                val tileSize = 14.dp.toPx().coerceAtLeast(8f)
                val lightChecker = Color(0xFFF3F4F6)
                val darkChecker = Color(0xFFE2E5EA)
                var yPos = 0f
                var rowIdx = 0
                while (yPos < size.height) {
                    var xPos = 0f
                    var colIdx = 0
                    val cellH = min(tileSize, size.height - yPos)
                    while (xPos < size.width) {
                        val cellW = min(tileSize, size.width - xPos)
                        drawRect(
                            color = if ((rowIdx + colIdx) % 2 == 0) lightChecker else darkChecker,
                            topLeft = Offset(xPos, yPos),
                            size = Size(cellW, cellH)
                        )
                        xPos += tileSize
                        colIdx++
                    }
                    yPos += tileSize
                    rowIdx++
                }

                // Compute aspect-fit destination rectangle so images are never stretched
                val imgW = processedBitmap.width.coerceAtLeast(1).toFloat()
                val imgH = processedBitmap.height.coerceAtLeast(1).toFloat()
                val scale = min(size.width / imgW, size.height / imgH)
                val dstW = (imgW * scale).toInt().coerceAtLeast(1)
                val dstH = (imgH * scale).toInt().coerceAtLeast(1)
                val dstOffsetX = ((canvasW - dstW) / 2).coerceAtLeast(0)
                val dstOffsetY = ((canvasH - dstH) / 2).coerceAtLeast(0)

                // Draw Processed (Full)
                drawImage(
                    image = procImage,
                    dstOffset = IntOffset(dstOffsetX, dstOffsetY),
                    dstSize = IntSize(dstW, dstH)
                )

                if (!showFullProcessedOnly) {
                    val splitX = (size.width * splitFraction).toInt().coerceIn(0, canvasW)

                    // Draw Original clipped to the left side of the divider
                    if (splitX > 0 && originalBitmap.width > 0 && originalBitmap.height > 0) {
                        clipRect(
                            left = 0f,
                            top = 0f,
                            right = splitX.toFloat(),
                            bottom = size.height
                        ) {
                            drawImage(
                                image = origImage,
                                dstOffset = IntOffset(dstOffsetX, dstOffsetY),
                                dstSize = IntSize(dstW, dstH)
                            )
                        }
                    }

                    // Draw Divider line
                    drawLine(
                        color = AccentPrimary,
                        start = Offset(splitX.toFloat(), 0f),
                        end = Offset(splitX.toFloat(), size.height),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }

            if (!showFullProcessedOnly && onCanvasTouch == null) {
                val handleOffsetDp = (boxWidthDp * splitFraction) - (boxWidthDp / 2f)
                Box(
                    modifier = Modifier
                        .offset(x = handleOffsetDp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = "Geser untuk membandingkan",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Asli",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "Hasil Proses",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(AccentPrimary.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (onCanvasTouch != null) {
                Text(
                    text = "Mode Kuas Aktif • Usap pada gambar untuk merapikan area",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        } else {
            Text(text = "Belum ada gambar", color = Color.Gray)
        }
    }
}
