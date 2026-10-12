package com.nalaro.pixelstudio.core.ai

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

enum class BrushMode {
    ERASE,
    RESTORE,
    REFINE
}

class MaskRefiner {

    fun applyBrush(
        currentBitmap: Bitmap,
        originalBitmap: Bitmap,
        touchX: Float,
        touchY: Float,
        brushRadius: Float,
        brushHardness: Float,
        mode: BrushMode
    ): Bitmap {
        val width = currentBitmap.width
        val height = currentBitmap.height
        val mutableBitmap = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)

        val minX = (touchX - brushRadius).toInt().coerceIn(0, width - 1)
        val maxX = (touchX + brushRadius).toInt().coerceIn(0, width - 1)
        val minY = (touchY - brushRadius).toInt().coerceIn(0, height - 1)
        val maxY = (touchY + brushRadius).toInt().coerceIn(0, height - 1)

        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        val currentPixels = IntArray(patchW * patchH)
        val origPixels = IntArray(patchW * patchH)

        mutableBitmap.getPixels(currentPixels, 0, patchW, minX, minY, patchW, patchH)
        originalBitmap.getPixels(origPixels, 0, patchW, minX, minY, patchW, patchH)

        val hardnessFactor = brushHardness.coerceIn(0.1f, 1.0f)

        for (py in 0 until patchH) {
            val actualY = minY + py
            val dy = actualY - touchY
            for (px in 0 until patchW) {
                val actualX = minX + px
                val dx = actualX - touchX
                val distance = sqrt(dx * dx + dy * dy)

                if (distance <= brushRadius) {
                    val normalizedDist = distance / brushRadius
                    val falloff = if (normalizedDist <= hardnessFactor) {
                        1.0f
                    } else {
                        ((1.0f - normalizedDist) / (1.0f - hardnessFactor)).coerceIn(0f, 1f)
                    }

                    val index = py * patchW + px
                    val currColor = currentPixels[index]
                    val origColor = origPixels[index]

                    val currAlpha = Color.alpha(currColor)
                    val origAlpha = Color.alpha(origColor)

                    val targetAlpha = when (mode) {
                        BrushMode.ERASE -> (currAlpha * (1.0f - falloff)).toInt().coerceIn(0, 255)
                        BrushMode.RESTORE -> (currAlpha + (origAlpha - currAlpha) * falloff).toInt().coerceIn(0, 255)
                        BrushMode.REFINE -> {
                            // High-pass edge sharpening on alpha boundary
                            if (currAlpha > 128) 255 else 0
                        }
                    }

                    currentPixels[index] = Color.argb(
                        targetAlpha,
                        Color.red(origColor),
                        Color.green(origColor),
                        Color.blue(origColor)
                    )
                }
            }
        }

        mutableBitmap.setPixels(currentPixels, 0, patchW, minX, minY, patchW, patchH)
        return mutableBitmap
    }
}
