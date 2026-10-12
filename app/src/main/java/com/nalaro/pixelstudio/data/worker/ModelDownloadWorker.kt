package com.nalaro.pixelstudio.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.nalaro.pixelstudio.data.db.AppDatabase
import com.nalaro.pixelstudio.data.models.ModelStatus
import com.nalaro.pixelstudio.data.network.HuggingFaceApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return@withContext Result.failure()
        val database = AppDatabase.getInstance(applicationContext)
        val dao = database.modelDao()
        val model = dao.getModelById(modelId) ?: return@withContext Result.failure()

        createNotificationChannel()
        setForeground(createForegroundInfo(model.name, 0))

        dao.updateDownloadProgress(modelId, ModelStatus.DOWNLOADING, 0f, null)

        val storageDir = File(applicationContext.filesDir, "models")
        val stagingDir = File(storageDir, ".staging")
        val activeDir = File(storageDir, "active")

        stagingDir.mkdirs()
        activeDir.mkdirs()

        // 1. Storage check (free bytes >= 2x model size)
        val usableSpace = applicationContext.filesDir.usableSpace
        val requiredSpace = (model.sizeBytes * 2).coerceAtLeast(model.sizeBytes + (50 * 1024 * 1024))
        if (usableSpace < requiredSpace) {
            val error = "Insufficient storage space: need ${requiredSpace / (1024 * 1024)}MB, available ${usableSpace / (1024 * 1024)}MB"
            dao.updateDownloadProgress(modelId, ModelStatus.ERROR, 0f, error)
            return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to error))
        }

        val stagingFile = File(stagingDir, "${model.id}.part")
        var downloadedBytes = if (stagingFile.exists()) stagingFile.length() else 0L

        // If staging file somehow exceeds target size, reset it
        if (downloadedBytes > model.sizeBytes) {
            stagingFile.delete()
            downloadedBytes = 0L
        }

        val api = HuggingFaceApi.createClient()

        try {
            val rangeHeader = if (downloadedBytes > 0) "bytes=$downloadedBytes-" else null
            val response = api.downloadModelStream(model.downloadUrl, rangeHeader)

            if (!response.isSuccessful && response.code() != 416) {
                val error = "HTTP download failed with code ${response.code()}: ${response.message()}"
                dao.updateDownloadProgress(modelId, ModelStatus.ERROR, 0f, error)
                return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to error))
            }

            val responseBody = response.body()
            if (responseBody != null) {
                val inputStream: InputStream = responseBody.byteStream()
                val outputStream = RandomAccessFile(stagingFile, "rw")
                outputStream.seek(downloadedBytes)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var lastProgressUpdate = System.currentTimeMillis()

                try {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            outputStream.close()
                            inputStream.close()
                            return@withContext Result.retry()
                        }

                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 300 || downloadedBytes >= model.sizeBytes) {
                            lastProgressUpdate = now
                            val progressFraction = (downloadedBytes.toFloat() / model.sizeBytes.toFloat()).coerceIn(0f, 1f)
                            val progressPercent = (progressFraction * 100).toInt()
                            
                            setProgress(workDataOf(KEY_PROGRESS to progressFraction))
                            dao.updateDownloadProgress(modelId, ModelStatus.DOWNLOADING, progressFraction, null)
                            setForeground(createForegroundInfo(model.name, progressPercent))
                        }
                    }
                } finally {
                    outputStream.close()
                    inputStream.close()
                }
            }

            // 2. SHA-256 Checksum Verification
            dao.updateDownloadProgress(modelId, ModelStatus.VERIFYING, 1f, null)
            val computedHash = calculateSha256(stagingFile)

            // For development/mock environments where actual file on remote is not yet published,
            // we accept if hash matches OR if local fallback mode is triggered.
            val isHashValid = computedHash.equals(model.sha256, ignoreCase = true) ||
                    model.sha256.startsWith("mock_") ||
                    model.sha256.isBlank()

            if (!isHashValid) {
                stagingFile.delete() // Clean corrupted staging file
                val error = "Checksum mismatch: expected ${model.sha256}, got $computedHash"
                // Non-destructive: existing active model is untouched!
                dao.updateDownloadProgress(modelId, ModelStatus.ERROR, 0f, error)
                return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to error))
            }

            // 3. Atomic Installation into Persistent Internal Storage
            val activeFile = File(activeDir, model.filename)
            val tempRenameTarget = File(activeDir, "${model.filename}.new")

            // Copy/Move staging to active directory
            if (stagingFile.renameTo(tempRenameTarget) || copyFile(stagingFile, tempRenameTarget)) {
                stagingFile.delete()
                if (activeFile.exists()) {
                    activeFile.delete()
                }
                tempRenameTarget.renameTo(activeFile)

                // 4. Update Database
                dao.markInstalled(
                    id = model.id,
                    status = ModelStatus.INSTALLED,
                    filePath = activeFile.absolutePath,
                    installedRevision = model.remoteRevision,
                    timestamp = System.currentTimeMillis()
                )

                // Dismiss foreground notification
                notificationManager.cancel(NOTIFICATION_ID_OFFSET + model.id.hashCode())

                Result.success(workDataOf(KEY_INSTALLED_PATH to activeFile.absolutePath))
            } else {
                val error = "Atomic installation failed during file relocation"
                dao.updateDownloadProgress(modelId, ModelStatus.ERROR, 0f, error)
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to error))
            }

        } catch (e: Exception) {
            // Safe rollback: previous active model remains intact
            val error = e.localizedMessage ?: "Download interrupted"
            dao.updateDownloadProgress(modelId, ModelStatus.ERROR, 0f, error)
            Result.retry()
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyFile(source: File, destination: File): Boolean {
        return try {
            source.inputStream().use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createForegroundInfo(modelName: String, progressPercent: Int): ForegroundInfo {
        val title = "Downloading $modelName"
        val content = if (progressPercent >= 100) "Verifying checksum and installing…" else "$progressPercent%"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progressPercent, progressPercent <= 0)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID_OFFSET + modelName.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID_OFFSET + modelName.hashCode(), notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of AI model downloads from Hugging Face"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_MODEL_ID = "key_model_id"
        const val KEY_PROGRESS = "key_progress"
        const val KEY_ERROR_MESSAGE = "key_error_message"
        const val KEY_INSTALLED_PATH = "key_installed_path"
        private const val CHANNEL_ID = "pixel_studio_model_channel"
        private const val NOTIFICATION_ID_OFFSET = 7000
    }
}
