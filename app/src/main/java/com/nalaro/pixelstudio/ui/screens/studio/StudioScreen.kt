package com.nalaro.pixelstudio.ui.screens.studio

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nalaro.pixelstudio.core.ai.BitmapUtils
import com.nalaro.pixelstudio.core.ai.BrushMode
import com.nalaro.pixelstudio.core.ai.EdgeRefinementMode
import com.nalaro.pixelstudio.core.ai.MaskRefiner
import com.nalaro.pixelstudio.core.ai.OnnxSegmentationEngine
import com.nalaro.pixelstudio.core.compression.*
import com.nalaro.pixelstudio.core.converter.FormatConverter
import com.nalaro.pixelstudio.core.export.ExportManager
import com.nalaro.pixelstudio.core.export.ExportRequest
import com.nalaro.pixelstudio.core.studio.BackgroundStudioEngine
import com.nalaro.pixelstudio.core.studio.BackgroundStyle
import com.nalaro.pixelstudio.core.studio.SubjectShadow
import com.nalaro.pixelstudio.data.models.ModelStatus
import com.nalaro.pixelstudio.data.repository.ModelRepository
import com.nalaro.pixelstudio.ui.components.*
import com.nalaro.pixelstudio.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StudioTool(val routeKey: String, val label: String, val shortLabel: String, val icon: ImageVector) {
    COMPRESS("compress", "Kompresi", "Kompresi", Icons.Outlined.Layers),
    REMOVE_BG("remove_bg", "Hapus Latar", "Hapus Latar", Icons.Outlined.ContentCut),
    BACKGROUND("background", "Latar", "Latar", Icons.Outlined.Tonality),
    CONVERT("convert", "Konversi", "Konversi", Icons.Outlined.SwapHoriz)
}

@Composable
fun StudioScreen(
    initialTool: String = "compress",
    initialImageUri: Uri? = null,
    modelRepository: ModelRepository,
    onNavigateBack: () -> Unit,
    onNavigateToModelManager: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var activeTool by remember(initialTool) {
        mutableStateOf(
            when (initialTool) {
                "remove_bg" -> StudioTool.REMOVE_BG
                "background" -> StudioTool.BACKGROUND
                "convert" -> StudioTool.CONVERT
                else -> StudioTool.COMPRESS
            }
        )
    }

    val allModels by modelRepository.allModels.collectAsState(initial = emptyList())
    val selectedModelId by modelRepository.selectedModelId.collectAsState(initial = "u2net-mobile")

    // Production Image State (starts completely clean, no dummy/sample image)
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cutoutBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var customBgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageDisplayName by remember { mutableStateOf("") }
    var imageDimensionText by remember { mutableStateOf("") }
    var originalSizeBytes by remember { mutableLongStateOf(0L) }
    var processedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var processedSizeBytes by remember { mutableLongStateOf(0L) }
    var hasProcessedResult by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var showFullResultOnly by remember { mutableStateOf(false) }

    // Unified Alert Banner State
    var currentAlert by remember { mutableStateOf<UiAlert?>(null) }

    // Compression & Format State
    var compressionMode by remember { mutableStateOf(CompressionMode.SMART) }
    var selectedOutputFormat by remember { mutableStateOf(ImageFormat.WEBP) }
    var compressionResult by remember { mutableStateOf<CompressionResult?>(null) }

    // Remove BG & Brush State
    var isPrecisionModel by remember { mutableStateOf(false) }
    var edgeRefinementMode by remember { mutableStateOf(EdgeRefinementMode.NATURAL) }
    var activeBrushMode by remember { mutableStateOf(BrushMode.ERASE) }
    var isBrushTouchEnabled by remember { mutableStateOf(false) }
    val segmentationEngine = remember { OnnxSegmentationEngine(context.applicationContext) }
    val maskRefiner = remember { MaskRefiner() }

    DisposableEffect(Unit) {
        onDispose {
            segmentationEngine.closeSession()
        }
    }

    // Background Studio State
    val bgEngine = remember { BackgroundStudioEngine() }
    var selectedBgColor by remember { mutableIntStateOf(0xFFFF6A3D.toInt()) }
    var backgroundTab by remember { mutableStateOf("Solid") }

    // Converter Options
    var preserveDimensions by remember { mutableStateOf(true) }
    var preserveTransparency by remember { mutableStateOf(true) }
    var removeGpsMetadata by remember { mutableStateOf(true) }

    // Helper to load a Uri safely off the main thread
    val loadUriIntoStudio: (Uri) -> Unit = { uri ->
        coroutineScope.launch {
            isProcessing = true
            val asset = withContext(Dispatchers.IO) {
                BitmapUtils.decodeSampledBitmapFromUri(context, uri, maxDimension = 2048)
            }
            if (asset != null) {
                originalBitmap = asset.bitmap
                processedBitmap = asset.bitmap
                cutoutBitmap = null
                compressionResult = null
                processedBytes = null
                processedSizeBytes = 0L
                hasProcessedResult = false
                isBrushTouchEnabled = false
                showFullResultOnly = false
                imageDisplayName = asset.displayName
                imageDimensionText = "${asset.originalWidth} × ${asset.originalHeight}"
                originalSizeBytes = asset.sizeBytes
                currentAlert = UiAlert(
                    kind = AlertKind.INFO,
                    title = "Gambar siap diproses",
                    message = "${asset.displayName} • ${asset.originalWidth}×${asset.originalHeight} (${formatFileSize(asset.sizeBytes)})"
                )
            } else {
                currentAlert = UiAlert(
                    kind = AlertKind.ERROR,
                    title = "Gagal memuat gambar",
                    message = "File gambar tidak dapat dibaca atau format tidak didukung."
                )
            }
            isProcessing = false
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            loadUriIntoStudio(uri)
        }
    }

    val bgImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val asset = withContext(Dispatchers.IO) {
                    BitmapUtils.decodeSampledBitmapFromUri(context, uri, maxDimension = 2048)
                }
                if (asset != null) {
                    customBgBitmap = asset.bitmap
                    backgroundTab = "Image"
                    currentAlert = UiAlert(
                        kind = AlertKind.SUCCESS,
                        title = "Gambar latar dimuat",
                        message = "Latar kustom '${asset.displayName}' berhasil diterapkan."
                    )
                }
            }
        }
    }

    LaunchedEffect(initialImageUri) {
        if (initialImageUri != null) {
            loadUriIntoStudio(initialImageUri)
        }
    }

    // Live Background Studio preview on Dispatchers.Default
    LaunchedEffect(activeTool, selectedBgColor, backgroundTab, customBgBitmap, cutoutBitmap) {
        if (activeTool == StudioTool.BACKGROUND && originalBitmap != null) {
            val subject = cutoutBitmap ?: originalBitmap ?: return@LaunchedEffect
            val composited = withContext(Dispatchers.Default) {
                val style = when (backgroundTab) {
                    "Gradient" -> BackgroundStyle.Gradient(
                        startColor = selectedBgColor,
                        endColor = 0xFF111827.toInt(),
                        angleDegrees = 45f
                    )
                    "Image" -> {
                        val bgBmp = customBgBitmap
                        if (bgBmp != null) {
                            BackgroundStyle.Image(bgBmp)
                        } else {
                            BackgroundStyle.Solid(selectedBgColor)
                        }
                    }
                    else -> BackgroundStyle.Solid(selectedBgColor)
                }
                bgEngine.composite(
                    subjectCutout = subject,
                    background = style,
                    shadow = SubjectShadow(opacity = 0.35f, radius = 16f)
                )
            }
            processedBitmap = composited
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth().background(SurfaceRaised).statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
                    }
                    Text(
                        text = activeTool.label,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    if (originalBitmap != null) {
                        IconButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Ganti foto", tint = TextSecondary)
                        }
                        IconButton(onClick = {
                            originalBitmap = null
                            processedBitmap = null
                            cutoutBitmap = null
                            processedBytes = null
                            processedSizeBytes = 0L
                            hasProcessedResult = false
                            compressionResult = null
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Tutup foto", tint = TextSecondary)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    StudioTool.values().forEach { tool ->
                        val selected = activeTool == tool
                        Column(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable {
                                activeTool = tool
                                isBrushTouchEnabled = false
                                if (tool == StudioTool.REMOVE_BG) selectedOutputFormat = ImageFormat.PNG
                            },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(tool.icon, contentDescription = null, modifier = Modifier.size(16.dp),
                                    tint = if (selected) AccentPrimary else TextSecondary)
                                Text(tool.shortLabel, fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) TextPrimary else TextSecondary)
                            }
                            Box(modifier = Modifier.height(2.dp).fillMaxWidth().background(
                                if (selected) AccentPrimary else Color.Transparent))
                        }
                    }
                }
                HorizontalDivider(color = LineDivider, thickness = 1.dp)
            }
        },
        containerColor = SurfaceBase,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            // Status Alert Banner (Success / Error / Info)
            if (currentAlert != null) {
                Spacer(modifier = Modifier.height(6.dp))
                StatusAlertBanner(
                    alert = currentAlert,
                    onDismiss = { currentAlert = null }
                )
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Main Canvas Area or Empty State
            if (originalBitmap == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(340.dp)
                        .clip(RoundedCornerShape(14.dp)).background(SurfaceRaised)
                        .border(1.dp, LineDivider, RoundedCornerShape(14.dp))
                        .clickable { photoPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null,
                            tint = TextMuted, modifier = Modifier.size(38.dp))
                        Text("Belum ada foto", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Button(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(44.dp),
                            contentPadding = PaddingValues(horizontal = 22.dp)
                        ) {
                            Text("Buka Foto", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceRaised)
                        .border(1.dp, LineDivider, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    BeforeAfterSlider(
                        originalBitmap = originalBitmap,
                        processedBitmap = processedBitmap ?: originalBitmap,
                        showFullProcessedOnly = showFullResultOnly || isBrushTouchEnabled,
                        onCanvasTouch = if (activeTool == StudioTool.REMOVE_BG && isBrushTouchEnabled && cutoutBitmap != null) { normX, normY ->
                            val orig = originalBitmap ?: return@BeforeAfterSlider
                            val curr = processedBitmap ?: cutoutBitmap ?: return@BeforeAfterSlider
                            val tx = normX * curr.width
                            val ty = normY * curr.height
                            val radius = (curr.width.coerceAtLeast(curr.height) * 0.045f).coerceAtLeast(14f)
                            val updated = maskRefiner.applyBrush(
                                currentBitmap = curr,
                                originalBitmap = orig,
                                touchX = tx,
                                touchY = ty,
                                brushRadius = radius,
                                brushHardness = 0.6f,
                                mode = activeBrushMode
                            )
                            cutoutBitmap = updated
                            processedBitmap = updated
                            hasProcessedResult = true
                        } else null,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (hasProcessedResult && !isBrushTouchEnabled) {
                        Text(
                            text = if (showFullResultOnly) "Bandingkan" else "Hasil",
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.67f))
                                .clickable { showFullResultOnly = !showFullResultOnly }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }

                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AccentPrimary)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Memproses gambar...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }

                // File Info Row
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${imageDisplayName.uppercase()} (${formatFileSize(originalSizeBytes)})",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.5.sp,
                        color = TextMuted
                    )
                    Text(
                        text = imageDimensionText,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // UNIFIED RESULT & DIRECT SAVE PANEL (Appears directly on the same screen right after processing!)
            if (hasProcessedResult && originalBitmap != null && processedBitmap != null) {
                val estimatedOutBytes = when {
                    processedSizeBytes > 0L -> processedSizeBytes
                    compressionResult != null -> compressionResult!!.bestCandidate.sizeBytes
                    else -> (originalSizeBytes * 0.45).toLong().coerceAtLeast(1024L)
                }
                val savingsPct = if (originalSizeBytes > 0 && estimatedOutBytes < originalSizeBytes) {
                    ((1.0 - estimatedOutBytes.toDouble() / originalSizeBytes.toDouble()) * 100.0).toFloat()
                } else 0f

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, SemanticSuccess.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SemanticSuccess,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Hasil",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${formatFileSize(originalSizeBytes)} ➔ ${formatFileSize(estimatedOutBytes)} • Format ${selectedOutputFormat.name}",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            if (savingsPct > 0f) {
                                Text(
                                    text = "-${"%.1f".format(savingsPct)}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SemanticSuccess,
                                    modifier = Modifier
                                        .background(SemanticSuccess.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            } else if (selectedOutputFormat != ImageFormat.JPEG) {
                                Text(
                                    text = "Transparan",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentPrimary,
                                    modifier = Modifier
                                        .background(AccentSoft, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Inline Format Selector right in the Save panel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Format",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ImageFormat.values().forEach { fmt ->
                                    val selected = selectedOutputFormat == fmt
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) AccentPrimary else SurfaceSubtle)
                                            .clickable { selectedOutputFormat = fmt }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = fmt.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Shortcut to add solid/gradient/photo background right after Remove BG
                        if (activeTool == StudioTool.REMOVE_BG && cutoutBitmap != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AccentSoft)
                                    .clickable { activeTool = StudioTool.BACKGROUND }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ganti latar",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Buka →",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Direct Save & Share Buttons on the Same Screen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val toExport = processedBitmap ?: return@launch
                                        isProcessing = true
                                        try {
                                            val exportManager = ExportManager(context)
                                            val bytes = withContext(Dispatchers.Default) {
                                                val cached = processedBytes
                                                if (cached != null && compressionResult?.bestCandidate?.format == selectedOutputFormat) {
                                                    cached
                                                } else {
                                                    val quality = if (selectedOutputFormat == ImageFormat.PNG) 100 else 90
                                                    FormatConverter().convert(toExport, selectedOutputFormat, quality = quality).outputBytes
                                                }
                                            }
                                            val baseName = imageDisplayName.substringBeforeLast('.').ifBlank { "Nalaro" }
                                            val result = exportManager.exportToPictures(
                                                ExportRequest(
                                                    data = bytes,
                                                    format = selectedOutputFormat,
                                                    suggestedName = "${baseName}_${System.currentTimeMillis()}"
                                                )
                                            )
                                            if (result.isSuccess) {
                                                processedSizeBytes = result.bytesWritten
                                                currentAlert = UiAlert(
                                                    kind = AlertKind.SUCCESS,
                                                    title = "Berhasil disimpan ke Galeri!",
                                                    message = "Lokasi: ${result.absolutePath ?: "Pictures/PixelStudio"} (${formatFileSize(result.bytesWritten)})"
                                                )
                                            } else {
                                                currentAlert = UiAlert(
                                                    kind = AlertKind.ERROR,
                                                    title = "Gagal menyimpan gambar",
                                                    message = result.errorMessage ?: "Periksa izin penyimpanan perangkat."
                                                )
                                            }
                                        } catch (t: Throwable) {
                                            currentAlert = UiAlert(
                                                kind = AlertKind.ERROR,
                                                title = "Gagal menyimpan gambar",
                                                message = t.localizedMessage ?: "Terjadi kesalahan saat menyimpan."
                                            )
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SemanticSuccess),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.35f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val toExport = processedBitmap ?: return@launch
                                        isProcessing = true
                                        try {
                                            val exportManager = ExportManager(context)
                                            val bytes = withContext(Dispatchers.Default) {
                                                FormatConverter().convert(toExport, selectedOutputFormat, quality = 90).outputBytes
                                            }
                                            val baseName = imageDisplayName.substringBeforeLast('.').ifBlank { "Nalaro" }
                                            val result = exportManager.exportToPictures(
                                                ExportRequest(
                                                    data = bytes,
                                                    format = selectedOutputFormat,
                                                    suggestedName = "${baseName}_Share_${System.currentTimeMillis()}"
                                                )
                                            )
                                            if (result.isSuccess && result.uri != null) {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = selectedOutputFormat.mimeType
                                                    putExtra(Intent.EXTRA_STREAM, result.uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Bagikan gambar"))
                                            } else {
                                                currentAlert = UiAlert(
                                                    kind = AlertKind.ERROR,
                                                    title = "Gagal menyiapkan file bagikan",
                                                    message = result.errorMessage ?: "Tidak dapat membuat file sementara."
                                                )
                                            }
                                        } catch (t: Throwable) {
                                            currentAlert = UiAlert(
                                                kind = AlertKind.ERROR,
                                                title = "Gagal membagikan gambar",
                                                message = t.localizedMessage ?: "Terjadi kesalahan."
                                            )
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, LineDivider),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bagikan", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Controls are contextual: the empty editor only needs a photo picker.
            if (originalBitmap != null) {
            when (activeTool) {
                // S02: SMART COMPRESSION
                StudioTool.COMPRESS -> {
                    if (originalBitmap != null) {
                        ImageMetricsCard(
                            originalBytes = originalSizeBytes,
                            resultBytes = compressionResult?.bestCandidate?.sizeBytes
                                ?: (originalSizeBytes * 0.32).toLong(),
                            savingsPct = compressionResult?.bestCandidate?.savingsPct ?: 68.0f,
                            formatName = selectedOutputFormat.extension.uppercase()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceSubtle)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Pair(CompressionMode.LOSSLESS, "Lossless"),
                            Pair(CompressionMode.SMART, "Smart"),
                            Pair(CompressionMode.MAXIMUM, "Maximum")
                        ).forEach { (mode, label) ->
                            val isSelected = compressionMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) SurfaceRaised else Color.Transparent)
                                    .clickable { compressionMode = mode }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Format output", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ImageFormat.values().forEach { fmt ->
                                val selected = selectedOutputFormat == fmt
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) AccentSoft else SurfaceRaised)
                                        .border(1.dp, if (selected) AccentPrimary else LineDivider, RoundedCornerShape(8.dp))
                                        .clickable { selectedOutputFormat = fmt }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = fmt.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) AccentPrimary else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val bmp = originalBitmap
                            if (bmp == null) {
                                photoPickerLauncher.launch("image/*")
                                return@Button
                            }
                            coroutineScope.launch {
                                isProcessing = true
                                try {
                                    val engine = SmartCompressionEngine()
                                    val result = engine.compress(bmp, originalSizeBytes, compressionMode, selectedOutputFormat)
                                    compressionResult = result
                                    processedBytes = result.bestCandidate.encodedBytes
                                    processedSizeBytes = result.bestCandidate.sizeBytes
                                    selectedOutputFormat = result.bestCandidate.format
                                    val decoded = withContext(Dispatchers.Default) {
                                        BitmapFactory.decodeByteArray(
                                            result.bestCandidate.encodedBytes,
                                            0,
                                            result.bestCandidate.encodedBytes.size
                                        )
                                    }
                                    if (decoded != null) {
                                        processedBitmap = decoded
                                        hasProcessedResult = true
                                        currentAlert = UiAlert(
                                            kind = AlertKind.SUCCESS,
                                            title = "Kompresi berhasil!",
                                            message = "Ukuran berkurang ${"%.1f".format(result.bestCandidate.savingsPct)}% (${formatFileSize(originalSizeBytes)} ➔ ${formatFileSize(result.bestCandidate.sizeBytes)}) dalam ${result.processingTimeMs} ms."
                                        )
                                    } else {
                                        currentAlert = UiAlert(
                                            kind = AlertKind.ERROR,
                                            title = "Gagal mendecode hasil kompresi",
                                            message = "Silakan coba format output lainnya."
                                        )
                                    }
                                } catch (t: Throwable) {
                                    currentAlert = UiAlert(
                                        kind = AlertKind.ERROR,
                                        title = "Kompresi gagal diproses",
                                        message = t.localizedMessage ?: "Terjadi kesalahan saat mengompres gambar."
                                    )
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = if (originalBitmap == null) "Buka Foto" else "Kompres",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // S03: AI REMOVE BACKGROUND
                StudioTool.REMOVE_BG -> {
                    val targetModelId = if (isPrecisionModel) "u2net-full" else "u2net-mobile"
                    val activeModel = allModels.firstOrNull { it.id == targetModelId }
                        ?: allModels.firstOrNull { it.status == ModelStatus.INSTALLED }
                    val isModelInstalled = activeModel?.status == ModelStatus.INSTALLED && !activeModel.localFilePath.isNullOrBlank()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Engine penghapus latar", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                text = if (isModelInstalled) {
                                    "ONNX Aktif (${activeModel?.name})"
                                } else {
                                    "On-Device AI + Smart Matting (Aktif tanpa kuota)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = SemanticSuccess
                            )
                        }
                        Text(
                            text = "Kelola Model ↗",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateToModelManager() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceSubtle)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isPrecisionModel) SurfaceRaised else Color.Transparent)
                                .clickable { isPrecisionModel = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cepat", fontWeight = FontWeight.Bold, color = if (!isPrecisionModel) TextPrimary else TextSecondary)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isPrecisionModel) SurfaceRaised else Color.Transparent)
                                .clickable { isPrecisionModel = true }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Detail", fontWeight = FontWeight.Bold, color = if (isPrecisionModel) TextPrimary else TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Kehalusan tepi (Edge)", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EdgeRefinementMode.values().forEach { mode ->
                                val selected = edgeRefinementMode == mode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) AccentSoft else SurfaceRaised)
                                        .border(1.dp, if (selected) AccentPrimary else LineDivider, RoundedCornerShape(8.dp))
                                        .clickable { edgeRefinementMode = mode }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = mode.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) AccentPrimary else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (cutoutBitmap != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kuas perapi manual",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isBrushTouchEnabled) AccentPrimary else SurfaceSubtle)
                                    .clickable { isBrushTouchEnabled = !isBrushTouchEnabled }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isBrushTouchEnabled) "Kuas Aktif ✓" else "Aktifkan Kuas",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBrushTouchEnabled) Color.White else TextSecondary
                                )
                            }
                        }

                        if (isBrushTouchEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(
                                    Triple(BrushMode.ERASE, "Hapus", Icons.Outlined.ContentCut),
                                    Triple(BrushMode.RESTORE, "Pulihkan", Icons.Outlined.Edit),
                                    Triple(BrushMode.REFINE, "Tajamkan", Icons.Outlined.BlurCircular)
                                ).forEach { (mode, label, icon) ->
                                    val isSelected = activeBrushMode == mode
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (isSelected) AccentSoft else SurfaceRaised),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { activeBrushMode = mode }
                                            .border(1.dp, if (isSelected) AccentPrimary else LineDivider, RoundedCornerShape(14.dp))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) AccentPrimary else TextPrimary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isSelected) AccentPrimary else TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val bmp = originalBitmap
                            if (bmp == null) {
                                photoPickerLauncher.launch("image/*")
                                return@Button
                            }
                            coroutineScope.launch {
                                isProcessing = true
                                try {
                                    val modelPath = activeModel?.localFilePath
                                    if (!modelPath.isNullOrBlank()) {
                                        segmentationEngine.loadModel(modelPath)
                                    }
                                    val outcome = segmentationEngine.segmentBackgroundDetailed(
                                        sourceBitmap = bmp,
                                        edgeMode = edgeRefinementMode,
                                        highPrecision = isPrecisionModel,
                                        inputWidth = 320,
                                        inputHeight = 320
                                    )
                                    cutoutBitmap = outcome.bitmap
                                    processedBitmap = outcome.bitmap
                                    selectedOutputFormat = ImageFormat.PNG
                                    val encodedPng = withContext(Dispatchers.Default) {
                                        FormatConverter().convert(outcome.bitmap, ImageFormat.PNG, quality = 100).outputBytes
                                    }
                                    processedBytes = encodedPng
                                    processedSizeBytes = encodedPng.size.toLong()
                                    hasProcessedResult = true
                                    showFullResultOnly = true
                                    currentAlert = UiAlert(
                                        kind = AlertKind.SUCCESS,
                                        title = "Background berhasil dihapus!",
                                        message = "Diproses dengan ${outcome.engineLabel} (${outcome.elapsedMs} ms). Hasil langsung siap disimpan di atas."
                                    )
                                } catch (t: Throwable) {
                                    currentAlert = UiAlert(
                                        kind = AlertKind.ERROR,
                                        title = "Gagal menghapus background",
                                        message = t.localizedMessage ?: "Terjadi kesalahan saat memproses gambar."
                                    )
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = if (originalBitmap == null) "Buka Foto" else "Hapus Latar",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // S04: BACKGROUND STUDIO
                StudioTool.BACKGROUND -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceSubtle)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Solid", "Gradient", "Image").forEach { tab ->
                            val isSelected = backgroundTab == tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) SurfaceRaised else Color.Transparent)
                                    .clickable {
                                        backgroundTab = tab
                                        if (tab == "Image" && customBgBitmap == null) {
                                            bgImagePickerLauncher.launch("image/*")
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (backgroundTab == "Image") {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { bgImagePickerLauncher.launch("image/*") }
                                .border(1.dp, LineDivider, RoundedCornerShape(18.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (customBgBitmap != null) "Gambar latar kustom aktif" else "Pilih gambar latar dari Galeri",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Ketuk untuk mengganti foto background",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                                Icon(Icons.Default.ImageSearch, contentDescription = null, tint = AccentPrimary)
                            }
                        }
                    } else {
                        HexColorPicker(
                            selectedColor = selectedBgColor,
                            onColorChanged = { newColor ->
                                selectedBgColor = newColor
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val bmp = originalBitmap
                            if (bmp == null) {
                                photoPickerLauncher.launch("image/*")
                                return@Button
                            }
                            coroutineScope.launch {
                                isProcessing = true
                                try {
                                    val subject = cutoutBitmap ?: run {
                                        val outcome = segmentationEngine.segmentBackgroundDetailed(
                                            sourceBitmap = bmp,
                                            edgeMode = edgeRefinementMode,
                                            highPrecision = true
                                        )
                                        cutoutBitmap = outcome.bitmap
                                        outcome.bitmap
                                    }
                                    val composited = withContext(Dispatchers.Default) {
                                        val style = when (backgroundTab) {
                                            "Gradient" -> BackgroundStyle.Gradient(
                                                startColor = selectedBgColor,
                                                endColor = 0xFF111827.toInt(),
                                                angleDegrees = 45f
                                            )
                                            "Image" -> {
                                                val bgBmp = customBgBitmap
                                                if (bgBmp != null) BackgroundStyle.Image(bgBmp)
                                                else BackgroundStyle.Solid(selectedBgColor)
                                            }
                                            else -> BackgroundStyle.Solid(selectedBgColor)
                                        }
                                        bgEngine.composite(
                                            subjectCutout = subject,
                                            background = style,
                                            shadow = SubjectShadow(opacity = 0.35f, radius = 16f)
                                        )
                                    }
                                    processedBitmap = composited
                                    val encoded = withContext(Dispatchers.Default) {
                                        FormatConverter().convert(composited, selectedOutputFormat, quality = 90).outputBytes
                                    }
                                    processedBytes = encoded
                                    processedSizeBytes = encoded.size.toLong()
                                    hasProcessedResult = true
                                    showFullResultOnly = true
                                    currentAlert = UiAlert(
                                        kind = AlertKind.SUCCESS,
                                        title = "Background baru berhasil diterapkan!",
                                        message = "Hasil komposisi langsung tampil dan siap disimpan ke Galeri."
                                    )
                                } catch (t: Throwable) {
                                    currentAlert = UiAlert(
                                        kind = AlertKind.ERROR,
                                        title = "Gagal menerapkan background",
                                        message = t.localizedMessage ?: "Terjadi kesalahan saat komposisi latar."
                                    )
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = if (originalBitmap == null) "Pilih Gambar & Terapkan Latar ↗" else "Terapkan Background ↗",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // S05: IMAGE CONVERTER
                StudioTool.CONVERT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Konversi ke format", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Pilih format target", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            Triple(ImageFormat.PNG, "Lossless", Icons.Outlined.Image),
                            Triple(ImageFormat.JPEG, "Foto", Icons.Outlined.PhotoCamera),
                            Triple(ImageFormat.WEBP, "Modern", Icons.Outlined.Layers)
                        ).forEach { (fmt, sub, icon) ->
                            val isSelected = selectedOutputFormat == fmt
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedOutputFormat = fmt }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) AccentPrimary else LineDivider,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) AccentPrimary else TextPrimary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = fmt.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(text = if (isSelected) "Dipilih" else sub, fontSize = 11.sp, color = if (isSelected) AccentPrimary else TextMuted)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LineDivider, RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OptionToggleRow(
                                title = "Pertahankan resolusi asli",
                                subtitle = if (imageDimensionText.isNotBlank()) "Tetap $imageDimensionText" else "Sesuai dimensi asli",
                                checked = preserveDimensions,
                                onCheckedChange = { preserveDimensions = it }
                            )
                            HorizontalDivider(color = LineDivider, modifier = Modifier.padding(vertical = 10.dp))
                            OptionToggleRow(
                                title = "Pertahankan transparansi",
                                subtitle = if (selectedOutputFormat == ImageFormat.JPEG) "JPG akan diratakan ke latar putih" else "Didukung penuh oleh ${selectedOutputFormat.name}",
                                checked = preserveTransparency,
                                onCheckedChange = { preserveTransparency = it }
                            )
                            HorizontalDivider(color = LineDivider, modifier = Modifier.padding(vertical = 10.dp))
                            OptionToggleRow(
                                title = "Hapus metadata lokasi (GPS/EXIF)",
                                subtitle = "Lindungi privasi foto saat dibagikan",
                                checked = removeGpsMetadata,
                                onCheckedChange = { removeGpsMetadata = it }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val bmp = cutoutBitmap ?: originalBitmap
                            if (bmp == null) {
                                photoPickerLauncher.launch("image/*")
                                return@Button
                            }
                            coroutineScope.launch {
                                isProcessing = true
                                try {
                                    val convPair = withContext(Dispatchers.Default) {
                                        val converter = FormatConverter()
                                        val res = converter.convert(bmp, selectedOutputFormat, quality = 90)
                                        val decoded = BitmapFactory.decodeByteArray(res.outputBytes, 0, res.outputBytes.size)
                                        Pair(res, decoded)
                                    }
                                    val res = convPair.first
                                    val decoded = convPair.second
                                    if (decoded != null) {
                                        processedBitmap = decoded
                                        processedBytes = res.outputBytes
                                        processedSizeBytes = res.outputBytes.size.toLong()
                                        hasProcessedResult = true
                                        showFullResultOnly = true
                                        currentAlert = UiAlert(
                                            kind = AlertKind.SUCCESS,
                                            title = "Berhasil dikonversi ke ${selectedOutputFormat.name}!",
                                            message = "Ukuran file hasil: ${formatFileSize(res.outputBytes.size.toLong())} (${res.finalWidth}×${res.finalHeight}). Langsung simpan di atas."
                                        )
                                    } else {
                                        currentAlert = UiAlert(
                                            kind = AlertKind.ERROR,
                                            title = "Konversi gagal",
                                            message = "Tidak dapat mengonversi gambar ke format ${selectedOutputFormat.name}."
                                        )
                                    }
                                } catch (t: Throwable) {
                                    currentAlert = UiAlert(
                                        kind = AlertKind.ERROR,
                                        title = "Konversi gagal diproses",
                                        message = t.localizedMessage ?: "Terjadi kesalahan saat mengonversi format."
                                    )
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = if (originalBitmap == null) "Buka Foto" else "Konversi",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun OptionToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextMuted)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) AccentPrimary else TextMuted)
        )
    }
}
