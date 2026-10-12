package com.nalaro.pixelstudio.core.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.nalaro.pixelstudio.core.compression.ImageFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ExportRequest(
    val data: ByteArray,
    val format: ImageFormat,
    val suggestedName: String,
    val stripGpsMetadata: Boolean = true
)

data class ExportResult(
    val uri: Uri?,
    val absolutePath: String?,
    val bytesWritten: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

class ExportManager(private val context: Context) {

    suspend fun exportToPictures(request: ExportRequest): ExportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val filename = if (request.suggestedName.endsWith(".${request.format.extension}")) {
            request.suggestedName
        } else {
            "${request.suggestedName}.${request.format.extension}"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, request.format.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelStudio")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext ExportResult(null, null, 0L, false, "Failed to create MediaStore entry")

            try {
                resolver.openOutputStream(imageUri)?.use { output ->
                    output.write(request.data)
                }

                // Finalize entry transactionally
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)

                ExportResult(
                    uri = imageUri,
                    absolutePath = "Pictures/PixelStudio/$filename",
                    bytesWritten = request.data.size.toLong(),
                    isSuccess = true
                )
            } catch (e: Exception) {
                resolver.delete(imageUri, null, null)
                ExportResult(null, null, 0L, false, e.localizedMessage)
            }
        } else {
            // Android 9 and below
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PixelStudio")
            dir.mkdirs()
            val file = File(dir, filename)

            try {
                FileOutputStream(file).use { it.write(request.data) }
                ExportResult(
                    uri = Uri.fromFile(file),
                    absolutePath = file.absolutePath,
                    bytesWritten = request.data.size.toLong(),
                    isSuccess = true
                )
            } catch (e: Exception) {
                ExportResult(null, null, 0L, false, e.localizedMessage)
            }
        }
    }
}
