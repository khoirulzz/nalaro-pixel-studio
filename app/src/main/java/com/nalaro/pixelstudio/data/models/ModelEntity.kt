package com.nalaro.pixelstudio.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ModelStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    INSTALLED,
    UPDATE_AVAILABLE,
    ERROR
}

@Entity(tableName = "ai_models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val task: String,
    val version: String,
    val remoteRevision: String,
    val installedRevision: String? = null,
    val filename: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val minAppVersionCode: Int,
    val ramRequirementMb: Int,
    val license: String,
    val licenseUrl: String,
    val description: String,
    val isRecommended: Boolean,
    val status: ModelStatus = ModelStatus.NOT_INSTALLED,
    val localFilePath: String? = null,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null,
    val lastCheckedTimestamp: Long = System.currentTimeMillis(),
    val installedTimestamp: Long? = null
)
