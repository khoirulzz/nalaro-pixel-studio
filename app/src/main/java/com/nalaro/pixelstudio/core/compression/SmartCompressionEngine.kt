package com.nalaro.pixelstudio.core.compression

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.nalaro.pixelstudio.core.ai.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

class SmartCompressionEngine {

    suspend fun compress(
        sourceBitmap: Bitmap,
        originalSizeBytes: Long,
        mode: CompressionMode = CompressionMode.SMART,
        targetFormat: ImageFormat? = null
    ): CompressionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val candidates = mutableListOf<CompressionCandidate>()

        val ssimThreshold = when (mode) {
            CompressionMode.LOSSLESS -> 0.999
            CompressionMode.SMART -> 0.94
            CompressionMode.MAXIMUM -> 0.88
        }

        val hasAlpha = sourceBitmap.hasAlpha()

        // Generate trial encodes based on mode
        when (mode) {
            CompressionMode.LOSSLESS -> {
                // WebP Lossless or PNG
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val webpBytes = BitmapUtils.compressToByteArray(sourceBitmap, Bitmap.CompressFormat.WEBP_LOSSLESS, 100)
                    val ssim = evaluateSsim(sourceBitmap, webpBytes)
                    candidates.add(createCandidate(ImageFormat.WEBP, 100, webpBytes, originalSizeBytes, ssim))
                }
                val pngBytes = BitmapUtils.compressToByteArray(sourceBitmap, Bitmap.CompressFormat.PNG, 100)
                val ssimPng = evaluateSsim(sourceBitmap, pngBytes)
                candidates.add(createCandidate(ImageFormat.PNG, 100, pngBytes, originalSizeBytes, ssimPng))
            }
            CompressionMode.SMART -> {
                val qualities = listOf(84, 76, 68)
                for (q in qualities) {
                    // Try WebP Lossy
                    val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    val webpBytes = BitmapUtils.compressToByteArray(sourceBitmap, webpFormat, q)
                    val ssim = evaluateSsim(sourceBitmap, webpBytes)
                    candidates.add(createCandidate(ImageFormat.WEBP, q, webpBytes, originalSizeBytes, ssim))

                    // Try JPEG if image has no alpha or alpha is opaque
                    if (!hasAlpha) {
                        val jpegBytes = BitmapUtils.compressToByteArray(sourceBitmap, Bitmap.CompressFormat.JPEG, q)
                        val ssimJpg = evaluateSsim(sourceBitmap, jpegBytes)
                        candidates.add(createCandidate(ImageFormat.JPEG, q, jpegBytes, originalSizeBytes, ssimJpg))
                    }
                }
            }
            CompressionMode.MAXIMUM -> {
                val qualities = listOf(62, 50, 40)
                for (q in qualities) {
                    val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    val webpBytes = BitmapUtils.compressToByteArray(sourceBitmap, webpFormat, q)
                    val ssim = evaluateSsim(sourceBitmap, webpBytes)
                    candidates.add(createCandidate(ImageFormat.WEBP, q, webpBytes, originalSizeBytes, ssim))
                }
            }
        }

        // Filter by target format if specifically requested
        val filtered = if (targetFormat != null) {
            candidates.filter { it.format == targetFormat }
        } else {
            candidates
        }

        // Select the smallest candidate that satisfies the quality gate (SSIM >= threshold)
        val passingCandidates = filtered.filter { it.ssimScore >= ssimThreshold }
        val best = passingCandidates.minByOrNull { it.sizeBytes }
            ?: filtered.maxByOrNull { it.ssimScore }
            ?: candidates.first()

        val elapsed = System.currentTimeMillis() - startTime
        return@withContext CompressionResult(
            originalBytes = originalSizeBytes,
            bestCandidate = best,
            allCandidates = candidates,
            processingTimeMs = elapsed
        )
    }

    private fun evaluateSsim(original: Bitmap, compressedBytes: ByteArray): Double {
        val decoded = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size) ?: return 0.0
        return SSIMCalculator.calculateSSIM(original, decoded)
    }

    private fun createCandidate(
        format: ImageFormat,
        quality: Int,
        bytes: ByteArray,
        originalSizeBytes: Long,
        ssim: Double
    ): CompressionCandidate {
        val savings = if (originalSizeBytes > 0) {
            max(0f, (1f - (bytes.size.toFloat() / originalSizeBytes.toFloat())) * 100f)
        } else 0f

        return CompressionCandidate(
            format = format,
            quality = quality,
            encodedBytes = bytes,
            ssimScore = ssim,
            savingsPct = savings
        )
    }
}
