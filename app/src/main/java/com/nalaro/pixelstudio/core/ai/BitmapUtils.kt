package com.nalaro.pixelstudio.core.ai

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class DecodedImageAsset(
    val bitmap: Bitmap,
    val displayName: String,
    val sizeBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int
)

object BitmapUtils {

    fun createResizedBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(source, targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1), true)
    }

    fun applyAlphaMask(original: Bitmap, alphaMask: FloatArray, maskWidth: Int, maskHeight: Int): Bitmap {
        val width = original.width
        val height = original.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val origPixels = IntArray(width * height)
        original.getPixels(origPixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(width * height)

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
        bitmap.compress(format, quality.coerceIn(1, 100), stream)
        return stream.toByteArray()
    }

    /**
     * Memory-safe image decoding with automatic downsampling (preventing OOM on 24MP+ camera photos)
     * and EXIF orientation normalization.
     */
    fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): DecodedImageAsset? {
        return try {
            val resolver = context.contentResolver
            var displayName = "IMAGE.PNG"
            var fileSize = 0L

            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            // 1. Decode bounds only
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val rawWidth = boundsOptions.outWidth
            val rawHeight = boundsOptions.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) return null

            // 2. Calculate power-of-2 inSampleSize
            var inSampleSize = 1
            val longestSide = max(rawWidth, rawHeight)
            if (longestSide > maxDimension) {
                val halfSide = longestSide / 2
                while ((halfSide / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            // 3. Read EXIF orientation
            val exifOrientation = resolver.openInputStream(uri)?.use { stream ->
                try {
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } catch (_: Throwable) {
                    ExifInterface.ORIENTATION_NORMAL
                }
            } ?: ExifInterface.ORIENTATION_NORMAL

            // 4. Decode sampled mutable/software ARGB_8888 bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val oriented = applyExifOrientation(decoded, exifOrientation)
            val finalSize = if (fileSize > 0L) fileSize else (oriented.width.toLong() * oriented.height.toLong() * 2L)

            DecodedImageAsset(
                bitmap = oriented,
                displayName = displayName,
                sizeBytes = finalSize,
                originalWidth = rawWidth,
                originalHeight = rawHeight
            )
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (_: Throwable) {
            bitmap
        }
    }
}
