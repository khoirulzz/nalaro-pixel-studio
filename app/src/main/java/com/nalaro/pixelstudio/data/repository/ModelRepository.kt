package com.nalaro.pixelstudio.data.repository

import android.content.Context
import androidx.work.*
import com.nalaro.pixelstudio.data.db.AppDatabase
import com.nalaro.pixelstudio.data.models.*
import com.nalaro.pixelstudio.data.network.HuggingFaceApi
import com.nalaro.pixelstudio.data.preferences.StudioPreferences
import com.nalaro.pixelstudio.data.worker.ModelDownloadWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class ModelRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    private val modelDao by lazy { database.modelDao() }
    private val preferences: StudioPreferences by lazy { StudioPreferences(appContext) }
    private val api: HuggingFaceApi by lazy { HuggingFaceApi.createClient() }
    private val workManager: WorkManager by lazy { WorkManager.getInstance(appContext) }

    val allModels: Flow<List<ModelEntity>>
        get() = modelDao.observeAllModels().catch { emit(emptyList()) }

    val isWifiOnly: Flow<Boolean>
        get() = preferences.isWifiOnly

    val selectedModelId: Flow<String>
        get() = preferences.selectedModelId

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val existing = modelDao.getAllModels()
            if (existing.isEmpty()) {
                seedFromAssets()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private suspend fun seedFromAssets() {
        try {
            val json = appContext.assets.open("models.json").bufferedReader().use { it.readText() }
            val moshi = HuggingFaceApi.createMoshi()
            val adapter = moshi.adapter(ManifestResponse::class.java)
            val manifest = adapter.fromJson(json)

            if (manifest != null) {
                syncManifestWithDatabase(manifest)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    suspend fun checkForUpdates(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val lastCheck = preferences.lastUpdateCheckTime.first()
            val now = System.currentTimeMillis()
            val throttleMillis = TimeUnit.HOURS.toMillis(6)

            if (!force && lastCheck > 0L && (now - lastCheck < throttleMillis)) {
                return@withContext false // Throttled
            }

            // Record check timestamp upfront so offline/404 responses still respect the 6h throttle
            preferences.recordUpdateCheckTime(now)

            val manifestUrl = HuggingFaceApi.getManifestUrl()
            val response = api.getManifest(manifestUrl)

            if (response.isSuccessful && response.body() != null) {
                val manifest = response.body()!!
                syncManifestWithDatabase(manifest)
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun syncManifestWithDatabase(manifest: ManifestResponse) {
        val existingModels = modelDao.getAllModels().associateBy { it.id }

        val entities = manifest.models.map { dto ->
            val existing = existingModels[dto.id]
            val activeFile = File(appContext.filesDir, "models/active/${dto.filename}")
            val isFilePresent = activeFile.exists() && activeFile.length() > 0

            val currentStatus = when {
                existing != null && existing.status == ModelStatus.DOWNLOADING -> ModelStatus.DOWNLOADING
                isFilePresent && existing?.installedRevision != null && existing.installedRevision != dto.revision -> ModelStatus.UPDATE_AVAILABLE
                isFilePresent -> ModelStatus.INSTALLED
                else -> ModelStatus.NOT_INSTALLED
            }

            ModelEntity(
                id = dto.id,
                name = dto.name,
                task = dto.task,
                version = dto.version,
                remoteRevision = dto.revision,
                installedRevision = if (isFilePresent) existing?.installedRevision ?: dto.revision else null,
                filename = dto.filename,
                downloadUrl = dto.downloadUrl,
                sizeBytes = dto.sizeBytes,
                sha256 = dto.sha256,
                minAppVersionCode = dto.minAppVersionCode,
                ramRequirementMb = dto.ramRequirementMb,
                license = dto.license,
                licenseUrl = dto.licenseUrl,
                description = dto.description,
                isRecommended = dto.isRecommended,
                status = currentStatus,
                localFilePath = if (isFilePresent) activeFile.absolutePath else null,
                downloadProgress = if (isFilePresent) 1f else 0f,
                installedTimestamp = if (isFilePresent) existing?.installedTimestamp ?: System.currentTimeMillis() else null
            )
        }

        modelDao.insertOrUpdateAll(entities)
    }

    suspend fun enqueueDownload(modelId: String) = withContext(Dispatchers.IO) {
        try {
            val wifiOnly = preferences.isWifiOnly.first()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()

            val downloadWork = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to modelId))
                .setConstraints(constraints)
                .addTag("model_download_$modelId")
                .build()

            workManager.enqueueUniqueWork(
                "model_download_$modelId",
                ExistingWorkPolicy.REPLACE,
                downloadWork
            )

            modelDao.updateDownloadProgress(modelId, ModelStatus.DOWNLOADING, 0f, null)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        try {
            val model = modelDao.getModelById(modelId) ?: return@withContext
            workManager.cancelUniqueWork("model_download_$modelId")

            // Remove active file
            if (model.localFilePath != null) {
                val file = File(model.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
            }

            // Clean staging file
            val stagingFile = File(appContext.filesDir, "models/.staging/${model.id}.part")
            if (stagingFile.exists()) {
                stagingFile.delete()
            }

            modelDao.markUninstalled(modelId)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        preferences.setWifiOnly(enabled)
    }

    suspend fun setSelectedModel(modelId: String) {
        preferences.setSelectedModelId(modelId)
    }

    fun getTotalStorageUsedBytes(): Long {
        return try {
            val activeDir = File(appContext.filesDir, "models/active")
            if (!activeDir.exists()) return 0L
            activeDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ModelRepository? = null

        fun getInstance(context: Context): ModelRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
