package com.nalaro.pixelstudio.core.converter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import com.nalaro.pixelstudio.core.ai.BitmapUtils
import com.nalaro.pixelstudio.core.compression.ImageFormat

data class ConversionResult(
    val outputBytes: ByteArray,
    val outputFormat: ImageFormat,
    val hadTransparencyFlattened: Boolean,
    val finalWidth: Int,
    val finalHeight: Int
)

class FormatConverter {

    fun convert(
        source: Bitmap,
        targetFormat: ImageFormat,
        quality: Int = 90,
        matteColor: Int = Color.WHITE
    ): ConversionResult {
        val hasAlpha = source.hasAlpha()
        var flattened = false

        val processedBitmap = if (targetFormat == ImageFormat.JPEG && hasAlpha) {
            flattened = true
            // Matte composite onto solid color
            val flattenedBmp = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flattenedBmp)
            canvas.drawColor(matteColor)
            canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            flattenedBmp
        } else {
            source
        }

        val compressFormat = when (targetFormat) {
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }

        val bytes = BitmapUtils.compressToByteArray(processedBitmap, compressFormat, quality.coerceIn(1, 100))

        return ConversionResult(
            outputBytes = bytes,
            outputFormat = targetFormat,
            hadTransparencyFlattened = flattened,
            finalWidth = processedBitmap.width,
            finalHeight = processedBitmap.height
        )
    }

    fun generateBatchFilename(originalName: String, targetFormat: ImageFormat, index: Int): String {
        val baseName = originalName.substringBeforeLast(".")
        return "$baseName-${targetFormat.extension}-$index.${targetFormat.extension}"
    }
}
