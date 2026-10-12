package com.nalaro.pixelstudio.core.batch

import android.net.Uri

enum class BatchItemStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class BatchItem(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val originalSizeBytes: Long = 0L,
    val outputSizeBytes: Long = 0L,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val progress: Float = 0f,
    val errorMessage: String? = null
)

data class BatchSummary(
    val totalItems: Int,
    val completedItems: Int,
    val failedItems: Int,
    val totalInputBytes: Long,
    val totalOutputBytes: Long,
    val totalSavingsPct: Float
)
