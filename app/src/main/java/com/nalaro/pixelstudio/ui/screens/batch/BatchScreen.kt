package com.nalaro.pixelstudio.ui.screens.batch

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nalaro.pixelstudio.core.ai.BitmapUtils
import com.nalaro.pixelstudio.core.ai.EdgeRefinementMode
import com.nalaro.pixelstudio.core.ai.OnnxSegmentationEngine
import com.nalaro.pixelstudio.core.batch.BatchItem
import com.nalaro.pixelstudio.core.batch.BatchItemStatus
import com.nalaro.pixelstudio.core.compression.CompressionMode
import com.nalaro.pixelstudio.core.compression.ImageFormat
import com.nalaro.pixelstudio.core.compression.SmartCompressionEngine
import com.nalaro.pixelstudio.core.converter.FormatConverter
import com.nalaro.pixelstudio.core.export.ExportManager
import com.nalaro.pixelstudio.core.export.ExportRequest
import com.nalaro.pixelstudio.ui.components.AlertKind
import com.nalaro.pixelstudio.ui.components.StatusAlertBanner
import com.nalaro.pixelstudio.ui.components.UiAlert
import com.nalaro.pixelstudio.ui.components.formatFileSize
import com.nalaro.pixelstudio.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BatchWorkflowPreset(
    val title: String,
    val subtitle: String,
    val targetFormat: ImageFormat
) {
    SMART_COMPRESS_WEBP(
        title = "Smart Compress • WebP",
        subtitle = "Kompresi cerdas SSIM • Pertahankan resolusi • Hapus GPS",
        targetFormat = ImageFormat.WEBP
    ),
    REMOVE_BG_PNG(
        title = "Remove Background • PNG",
        subtitle = "Hapus latar otomatis (On-Device AI / Smart Cutout) • Transparan",
        targetFormat = ImageFormat.PNG
    ),
    CONVERT_JPEG(
        title = "Convert Format • JPEG",
        subtitle = "Konversi massal ke JPEG berkualitas tinggi • Hapus GPS",
        targetFormat = ImageFormat.JPEG
    ),
    CONVERT_PNG(
        title = "Convert Format • PNG",
        subtitle = "Konversi massal ke PNG Lossless • Pertahankan transparansi",
        targetFormat = ImageFormat.PNG
    )
}

@Composable
fun BatchScreen(
    initialUris: List<Uri> = emptyList(),
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<BatchItem>>(emptyList()) }
    var activePreset by remember { mutableStateOf(BatchWorkflowPreset.SMART_COMPRESS_WEBP) }
    var isRunningBatch by remember { mutableStateOf(false) }
    var currentAlert by remember { mutableStateOf<UiAlert?>(null) }

    val segmentationEngine = remember { OnnxSegmentationEngine(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose {
            segmentationEngine.closeSession()
        }
    }

    val appendUrisToQueue: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            val baseIndex = items.size
            val newItems = uris.mapIndexed { idx, uri ->
                var displayName = "Image_${baseIndex + idx + 1}.jpg"
                var fileSize = 0L
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null,
                        null,
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx >= 0 && !c.isNull(nameIdx)) {
                                displayName = c.getString(nameIdx) ?: displayName
                            }
                            if (sizeIdx >= 0 && !c.isNull(sizeIdx)) {
                                fileSize = c.getLong(sizeIdx)
                            }
                        }
                    }
                } catch (_: Throwable) {
                }

                BatchItem(
                    id = "${System.currentTimeMillis()}_${baseIndex + idx}",
                    uri = uri,
                    displayName = displayName,
                    originalSizeBytes = fileSize,
                    status = BatchItemStatus.PENDING,
                    progress = 0f
                )
            }
            items = items + newItems
            currentAlert = UiAlert(
                kind = AlertKind.INFO,
                title = "${newItems.size} gambar ditambahkan ke antrean",
                message = "Total ${items.size} gambar siap diproses dengan mode '${activePreset.title}'."
            )
        }
    }

    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty() && items.isEmpty()) {
            appendUrisToQueue(initialUris)
        }
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        appendUrisToQueue(uris)
    }

    val completedCount = items.count { it.status == BatchItemStatus.COMPLETED }
    val failedCount = items.count { it.status == BatchItemStatus.FAILED }
    val pendingCount = items.count { it.status == BatchItemStatus.PENDING || it.status == BatchItemStatus.PROCESSING }
    val overallProgress = if (items.isNotEmpty()) {
        (completedCount.toFloat() / items.size.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceBase)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigateBack() }
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Kembali", tint = TextPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Batch Processing",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (items.isNotEmpty() && !isRunningBatch) {
                            IconButton(
                                onClick = {
                                    items = emptyList()
                                    currentAlert = UiAlert(
                                        kind = AlertKind.INFO,
                                        title = "Antrean dikosongkan",
                                        message = "Semua daftar gambar pada antrean batch telah dihapus."
                                    )
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceRaised)
                                    .border(1.dp, LineDivider, RoundedCornerShape(10.dp))
                            ) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Kosongkan antrean", tint = TextSecondary, modifier = Modifier.size(20.dp))
                            }
                        }

                        IconButton(
                            onClick = { multiPhotoPicker.launch("image/*") },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceRaised)
                                .border(1.dp, LineDivider, RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Tambah Gambar", tint = AccentPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        containerColor = SurfaceBase,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            if (currentAlert != null) {
                Spacer(modifier = Modifier.height(4.dp))
                StatusAlertBanner(
                    alert = currentAlert,
                    onDismiss = { currentAlert = null }
                )
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, LineDivider, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MODE WORKFLOW BATCH",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = activePreset.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activePreset.subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BatchWorkflowPreset.values().forEach { preset ->
                            val selected = activePreset == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) AccentPrimary else SurfaceSubtle)
                                    .clickable(enabled = !isRunningBatch) { activePreset = preset }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = preset.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Antrean (${items.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Tambah Gambar +",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentPrimary,
                    modifier = Modifier.clickable(enabled = !isRunningBatch) {
                        multiPhotoPicker.launch("image/*")
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (items.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable { multiPhotoPicker.launch("image/*") }
                        .border(1.dp, LineDivider, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AccentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Antrean Batch Masih Kosong",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Pilih beberapa gambar sekaligus dari Galeri untuk dikompresi, dihapus latarnya, atau dikonversi otomatis ke Pictures/PixelStudio.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { multiPhotoPicker.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pilih Beberapa Gambar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(items, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, LineDivider, RoundedCornerShape(18.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AccentSoft),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Image,
                                            contentDescription = null,
                                            tint = AccentPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val statusText = when (item.status) {
                                            BatchItemStatus.PENDING -> "Menunggu • ${formatFileSize(item.originalSizeBytes)} ➔ ${activePreset.targetFormat.name}"
                                            BatchItemStatus.PROCESSING -> "Sedang memproses..."
                                            BatchItemStatus.COMPLETED -> {
                                                val savedPct = if (item.originalSizeBytes > 0 && item.outputSizeBytes < item.originalSizeBytes) {
                                                    val pct = ((1.0 - item.outputSizeBytes.toDouble() / item.originalSizeBytes.toDouble()) * 100.0).toInt()
                                                    " (-$pct%)"
                                                } else ""
                                                "Tersimpan di Galeri • ${formatFileSize(item.originalSizeBytes)} ➔ ${formatFileSize(item.outputSizeBytes)}$savedPct"
                                            }
                                            BatchItemStatus.FAILED -> "Gagal: ${item.errorMessage ?: "Error"}"
                                            BatchItemStatus.SKIPPED -> "Dilewati"
                                        }
                                        val statusColor = when (item.status) {
                                            BatchItemStatus.COMPLETED -> SemanticSuccess
                                            BatchItemStatus.FAILED -> SemanticError
                                            BatchItemStatus.PROCESSING -> AccentPrimary
                                            else -> TextSecondary
                                        }
                                        Text(
                                            text = statusText,
                                            fontSize = 12.sp,
                                            color = statusColor
                                        )
                                    }

                                    when (item.status) {
                                        BatchItemStatus.COMPLETED -> {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SemanticSuccess, modifier = Modifier.size(20.dp))
                                        }
                                        BatchItemStatus.PROCESSING -> {
                                            CircularProgressIndicator(color = AccentPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                        }
                                        BatchItemStatus.FAILED -> {
                                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = SemanticError, modifier = Modifier.size(20.dp))
                                        }
                                        else -> {
                                            if (!isRunningBatch) {
                                                IconButton(
                                                    onClick = { items = items.filterNot { it.id == item.id } },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Hapus", tint = TextMuted, modifier = Modifier.size(16.dp))
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(TextMuted)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (item.status == BatchItemStatus.PROCESSING) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        color = AccentPrimary,
                                        trackColor = SurfaceSubtle,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (items.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$completedCount dari ${items.size} selesai" + if (failedCount > 0) " ($failedCount gagal)" else "",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "${(overallProgress * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { overallProgress },
                    color = AccentPrimary,
                    trackColor = SurfaceSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            Button(
                onClick = {
                    if (items.isEmpty()) {
                        multiPhotoPicker.launch("image/*")
                        return@Button
                    }
                    if (isRunningBatch) {
                        isRunningBatch = false
                        currentAlert = UiAlert(
                            kind = AlertKind.WARNING,
                            title = "Pemrosesan batch dijeda",
                            message = "Ketuk 'Lanjutkan Proses Batch' untuk meneruskan antrean yang tersisa."
                        )
                        return@Button
                    }

                    if (pendingCount == 0) {
                        items = items.map { it.copy(status = BatchItemStatus.PENDING, progress = 0f, errorMessage = null) }
                    }

                    coroutineScope.launch {
                        isRunningBatch = true
                        val exportManager = ExportManager(context)
                        val compressionEngine = SmartCompressionEngine()
                        val converter = FormatConverter()
                        var successCounter = 0
                        var failCounter = 0

                        for (idx in items.indices) {
                            if (!isRunningBatch) break
                            val current = items[idx]
                            if (current.status == BatchItemStatus.COMPLETED) {
                                successCounter++
                                continue
                            }

                            items = items.toMutableList().also { list ->
                                list[idx] = current.copy(status = BatchItemStatus.PROCESSING, progress = 0.25f)
                            }

                            try {
                                val decodedAsset = withContext(Dispatchers.IO) {
                                    BitmapUtils.decodeSampledBitmapFromUri(context, current.uri, maxDimension = 2048)
                                } ?: throw IllegalStateException("Gagal membaca gambar")

                                val origSize = if (current.originalSizeBytes > 0L) current.originalSizeBytes else decodedAsset.sizeBytes

                                val outputBytes: ByteArray = withContext(Dispatchers.Default) {
                                    when (activePreset) {
                                        BatchWorkflowPreset.SMART_COMPRESS_WEBP -> {
                                            compressionEngine.compress(
                                                sourceBitmap = decodedAsset.bitmap,
                                                originalSizeBytes = origSize,
                                                mode = CompressionMode.SMART,
                                                targetFormat = ImageFormat.WEBP
                                            ).bestCandidate.encodedBytes
                                        }
                                        BatchWorkflowPreset.REMOVE_BG_PNG -> {
                                            val cutout = segmentationEngine.segmentBackgroundDetailed(
                                                sourceBitmap = decodedAsset.bitmap,
                                                edgeMode = EdgeRefinementMode.NATURAL,
                                                highPrecision = true
                                            ).bitmap
                                            converter.convert(cutout, ImageFormat.PNG, quality = 100).outputBytes
                                        }
                                        BatchWorkflowPreset.CONVERT_JPEG -> {
                                            converter.convert(decodedAsset.bitmap, ImageFormat.JPEG, quality = 90).outputBytes
                                        }
                                        BatchWorkflowPreset.CONVERT_PNG -> {
                                            converter.convert(decodedAsset.bitmap, ImageFormat.PNG, quality = 100).outputBytes
                                        }
                                    }
                                }

                                val baseName = current.displayName.substringBeforeLast('.').ifBlank { "Batch_${idx + 1}" }
                                val exportRes = exportManager.exportToPictures(
                                    ExportRequest(
                                        data = outputBytes,
                                        format = activePreset.targetFormat,
                                        suggestedName = "${baseName}_${System.currentTimeMillis()}"
                                    )
                                )

                                if (exportRes.isSuccess) {
                                    successCounter++
                                    items = items.toMutableList().also { list ->
                                        list[idx] = current.copy(
                                            originalSizeBytes = origSize,
                                            outputSizeBytes = exportRes.bytesWritten,
                                            status = BatchItemStatus.COMPLETED,
                                            progress = 1f,
                                            errorMessage = null
                                        )
                                    }
                                } else {
                                    failCounter++
                                    items = items.toMutableList().also { list ->
                                        list[idx] = current.copy(
                                            status = BatchItemStatus.FAILED,
                                            progress = 0f,
                                            errorMessage = exportRes.errorMessage ?: "Gagal menyimpan"
                                        )
                                    }
                                }
                            } catch (t: Throwable) {
                                failCounter++
                                items = items.toMutableList().also { list ->
                                    list[idx] = current.copy(
                                        status = BatchItemStatus.FAILED,
                                        progress = 0f,
                                        errorMessage = t.localizedMessage ?: "Gagal memproses"
                                    )
                                }
                            }
                        }

                        val wasCancelled = !isRunningBatch
                        isRunningBatch = false

                        if (!wasCancelled) {
                            currentAlert = if (failCounter == 0 && successCounter > 0) {
                                UiAlert(
                                    kind = AlertKind.SUCCESS,
                                    title = "Batch selesai! $successCounter gambar berhasil disimpan",
                                    message = "Semua hasil telah tersimpan otomatis di Galeri (Pictures/PixelStudio)."
                                )
                            } else if (successCounter > 0 && failCounter > 0) {
                                UiAlert(
                                    kind = AlertKind.WARNING,
                                    title = "Batch selesai sebagian ($successCounter berhasil, $failCounter gagal)",
                                    message = "File yang berhasil telah disimpan ke Pictures/PixelStudio."
                                )
                            } else {
                                UiAlert(
                                    kind = AlertKind.ERROR,
                                    title = "Pemrosesan batch gagal",
                                    message = "Tidak ada gambar yang berhasil diproses. Periksa format file."
                                )
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunningBatch) SemanticWarning else AccentPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                val buttonLabel = when {
                    items.isEmpty() -> "Pilih Gambar untuk Antrean Batch +"
                    isRunningBatch -> "Jeda Antrean Batch ⏸"
                    pendingCount == 0 -> "Proses Ulang Semua (${items.size}) ↻"
                    completedCount > 0 -> "Lanjutkan Proses Batch ($pendingCount tersisa) ▶"
                    else -> "Proses & Simpan Semua (${items.size}) ▶"
                }
                Text(
                    text = buttonLabel,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
