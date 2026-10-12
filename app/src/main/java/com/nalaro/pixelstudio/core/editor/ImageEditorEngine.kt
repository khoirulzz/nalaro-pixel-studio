package com.nalaro.pixelstudio.core.editor

import android.graphics.*

data class EditorAdjustments(
    val brightness: Float = 0f, // -100f to +100f
    val contrast: Float = 1f,    // 0.5f to 2.0f
    val saturation: Float = 1f,  // 0.0f to 2.0f
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
)

class ImageEditorEngine {

    fun applyEdits(source: Bitmap, adjustments: EditorAdjustments): Bitmap {
        var processed = source

        // 1. Rotation and Flips
        if (adjustments.rotationDegrees != 0f || adjustments.flipHorizontal || adjustments.flipVertical) {
            val matrix = Matrix()
            if (adjustments.rotationDegrees != 0f) {
                matrix.postRotate(adjustments.rotationDegrees)
            }
            val scaleX = if (adjustments.flipHorizontal) -1f else 1f
            val scaleY = if (adjustments.flipVertical) -1f else 1f
            if (scaleX != 1f || scaleY != 1f) {
                matrix.postScale(scaleX, scaleY)
            }
            processed = Bitmap.createBitmap(processed, 0, 0, processed.width, processed.height, matrix, true)
        }

        // 2. Color adjustments (Brightness, Contrast, Saturation)
        if (adjustments.brightness != 0f || adjustments.contrast != 1f || adjustments.saturation != 1f) {
            val result = Bitmap.createBitmap(processed.width, processed.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)

            val colorMatrix = ColorMatrix()

            // Saturation
            if (adjustments.saturation != 1f) {
                colorMatrix.setSaturation(adjustments.saturation)
            }

            // Contrast & Brightness
            val contrast = adjustments.contrast
            val brightness = adjustments.brightness
            val scale = contrast
            val translate = (-0.5f * contrast + 0.5f) * 255f + brightness

            val cbMatrix = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))

            colorMatrix.postConcat(cbMatrix)
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(processed, 0f, 0f, paint)
            return result
        }

        return processed
    }

    fun crop(source: Bitmap, cropRect: Rect): Bitmap {
        val safeLeft = cropRect.left.coerceIn(0, source.width - 1)
        val safeTop = cropRect.top.coerceIn(0, source.height - 1)
        val safeWidth = cropRect.width().coerceIn(1, source.width - safeLeft)
        val safeHeight = cropRect.height().coerceIn(1, source.height - safeTop)
        return Bitmap.createBitmap(source, safeLeft, safeTop, safeWidth, safeHeight)
    }
}
