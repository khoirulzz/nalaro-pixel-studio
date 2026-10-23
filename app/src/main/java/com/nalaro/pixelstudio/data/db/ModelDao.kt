package com.nalaro.pixelstudio.data.db

import androidx.room.*
import com.nalaro.pixelstudio.data.models.ModelEntity
import com.nalaro.pixelstudio.data.models.ModelStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    @Query("SELECT * FROM ai_models ORDER BY isRecommended DESC, name ASC")
    fun observeAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM ai_models")
    suspend fun getAllModels(): List<ModelEntity>

    @Query("SELECT * FROM ai_models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): ModelEntity?

    @Query("SELECT * FROM ai_models WHERE id = :id LIMIT 1")
    fun observeModelById(id: String): Flow<ModelEntity?>

    @Query("SELECT * FROM ai_models WHERE status = :status")
    suspend fun getModelsByStatus(status: ModelStatus): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(model: ModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(models: List<ModelEntity>)

    @Query("UPDATE ai_models SET status = :status, downloadProgress = :progress, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateDownloadProgress(id: String, status: ModelStatus, progress: Float, errorMessage: String? = null)

    @Query("""
        UPDATE ai_models 
        SET status = :status, 
            localFilePath = :filePath, 
            installedRevision = :installedRevision,
            installedTimestamp = :timestamp,
            downloadProgress = 1.0,
            errorMessage = null 
        WHERE id = :id
    """)
    suspend fun markInstalled(id: String, status: ModelStatus, filePath: String, installedRevision: String, timestamp: Long)

    @Query("UPDATE ai_models SET status = :status, localFilePath = null, installedRevision = null, downloadProgress = 0.0 WHERE id = :id")
    suspend fun markUninstalled(id: String, status: ModelStatus = ModelStatus.NOT_INSTALLED)

    @Query("DELETE FROM ai_models WHERE id NOT IN (:validIds) AND status != 'INSTALLED'")
    suspend fun deleteExcept(validIds: List<String>)

    @Delete
    suspend fun delete(model: ModelEntity)
}
