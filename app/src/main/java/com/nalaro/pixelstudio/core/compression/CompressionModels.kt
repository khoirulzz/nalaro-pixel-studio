package com.nalaro.pixelstudio.core.compression

import android.graphics.Bitmap

enum class CompressionMode {
    SMART,
    LOSSLESS,
    MAXIMUM
}

enum class ImageFormat(val extension: String, val mimeType: String) {
    WEBP("webp", "image/webp"),
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png")
}

data class CompressionCandidate(
    val format: ImageFormat,
    val quality: Int,
    val encodedBytes: ByteArray,
    val sizeBytes: Long = encodedBytes.size.toLong(),
    val ssimScore: Double,
    val savingsPct: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CompressionCandidate
        return format == other.format && quality == other.quality && sizeBytes == other.sizeBytes
    }

    override fun hashCode(): Int {
        var result = format.hashCode()
        result = 31 * result + quality
        result = 31 * result + sizeBytes.hashCode()
        return result
    }
}

data class CompressionResult(
    val originalBytes: Long,
    val bestCandidate: CompressionCandidate,
    val allCandidates: List<CompressionCandidate>,
    val processingTimeMs: Long,
    val isSmallerThanOriginal: Boolean = bestCandidate.sizeBytes < originalBytes
)
