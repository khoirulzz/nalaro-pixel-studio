package com.nalaro.pixelstudio.core.ai

import android.graphics.*
import java.io.ByteArrayOutputStream

object BitmapUtils {

    fun createResizedBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    fun applyAlphaMask(original: Bitmap, alphaMask: FloatArray, maskWidth: Int, maskHeight: Int): Bitmap {
        val width = original.width
        val height = original.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val origPixels = IntArray(width * height)
        original.getPixels(origPixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(width * height)

        // Bilinear or nearest sampling from mask dimensions to original dimensions
        val scaleX = maskWidth.toFloat() / width.toFloat()
        val scaleY = maskHeight.toFloat() / height.toFloat()

        for (y in 0 until height) {
            val maskY = (y * scaleY).toInt().coerceIn(0, maskHeight - 1)
            val maskRowOffset = maskY * maskWidth
            val origRowOffset = y * width

            for (x in 0 until width) {
                val maskX = (x * scaleX).toInt().coerceIn(0, maskWidth - 1)
                val alphaFloat = alphaMask[maskRowOffset + maskX].coerceIn(0f, 1f)
                val alphaInt = (alphaFloat * 255f).toInt()

                val origColor = origPixels[origRowOffset + x]
                val r = Color.red(origColor)
                val g = Color.green(origColor)
                val b = Color.blue(origColor)
                val originalAlpha = Color.alpha(origColor)

                val finalAlpha = ((originalAlpha * alphaInt) / 255).coerceIn(0, 255)
                outPixels[origRowOffset + x] = Color.argb(finalAlpha, r, g, b)
            }
        }

        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    fun compressToByteArray(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return stream.toByteArray()
    }
}
