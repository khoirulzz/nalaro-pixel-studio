package com.nalaro.pixelstudio.core.export

import android.content.ContentUris
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

data class ExportedImageItem(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateModifiedMillis: Long,
    val mimeType: String
)

class ExportManager(private val context: Context) {

    suspend fun exportToPictures(request: ExportRequest): ExportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val filename = if (request.suggestedName.endsWith(".${request.format.extension}", ignoreCase = true)) {
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
                ?: return@withContext ExportResult(null, null, 0L, false, "Gagal membuat entri MediaStore")

            try {
                resolver.openOutputStream(imageUri)?.use { output ->
                    output.write(request.data)
                }

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

    /**
     * Queries real exported images created by Nalaro Pixel Studio in Pictures/PixelStudio.
     */
    suspend fun listExportedImages(limit: Int = 50): List<ExportedImageItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ExportedImageItem>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.RELATIVE_PATH
                )
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%PixelStudio%")
                val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                    val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                    while (cursor.moveToNext() && items.size < limit) {
                        val id = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        val name = cursor.getString(nameCol) ?: "Nalaro_Export.webp"
                        val size = cursor.getLong(sizeCol)
                        val width = cursor.getInt(widthCol)
                        val height = cursor.getInt(heightCol)
                        val dateSec = cursor.getLong(dateCol)
                        val mime = cursor.getString(mimeCol) ?: "image/webp"

                        items.add(
                            ExportedImageItem(
                                uri = contentUri,
                                displayName = name,
                                sizeBytes = size,
                                width = width,
                                height = height,
                                dateModifiedMillis = dateSec * 1000L,
                                mimeType = mime
                            )
                        )
                    }
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PixelStudio")
                if (dir.exists()) {
                    dir.listFiles()
                        ?.filter { it.isFile && it.length() > 0 }
                        ?.sortedByDescending { it.lastModified() }
                        ?.take(limit)
                        ?.forEach { file ->
                            val ext = file.extension.lowercase()
                            val mime = when (ext) {
                                "png" -> "image/png"
                                "jpg", "jpeg" -> "image/jpeg"
                                else -> "image/webp"
                            }
                            items.add(
                                ExportedImageItem(
                                    uri = Uri.fromFile(file),
                                    displayName = file.name,
                                    sizeBytes = file.length(),
                                    width = 0,
                                    height = 0,
                                    dateModifiedMillis = file.lastModified(),
                                    mimeType = mime
                                )
                            )
                        }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        items
    }

    suspend fun deleteExportedImage(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return@withContext false
                File(path).delete()
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
        } catch (_: Throwable) {
            false
        }
    }
}
