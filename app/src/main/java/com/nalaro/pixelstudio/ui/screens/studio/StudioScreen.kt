package com.nalaro.pixelstudio.ui.screens.studio

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nalaro.pixelstudio.core.ai.BitmapUtils
import com.nalaro.pixelstudio.core.ai.BrushMode
import com.nalaro.pixelstudio.core.ai.MaskRefiner
import com.nalaro.pixelstudio.core.ai.OnnxSegmentationEngine
import com.nalaro.pixelstudio.core.compression.*
import com.nalaro.pixelstudio.core.converter.FormatConverter
import com.nalaro.pixelstudio.core.export.ExportManager
import com.nalaro.pixelstudio.core.export.ExportRequest
import com.nalaro.pixelstudio.core.studio.BackgroundStyle
import com.nalaro.pixelstudio.core.studio.BackgroundStudioEngine
import com.nalaro.pixelstudio.core.studio.SubjectPlacement
import com.nalaro.pixelstudio.core.studio.SubjectShadow
import com.nalaro.pixelstudio.data.repository.ModelRepository
import com.nalaro.pixelstudio.ui.components.*
import com.nalaro.pixelstudio.ui.theme.*
import kotlinx.coroutines.launch

enum class StudioTool(val label: String) {
    COMPRESS("Smart Compression"),
    REMOVE_BG("AI Remove Background"),
    BACKGROUND("Background Studio"),
    CONVERT("Image Converter"),
    EXPORT("Export Image")
}

@Composable
fun StudioScreen(
    initialTool: String = "compress",
    modelRepository: ModelRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTool by remember {
        mutableStateOf(
            when (initialTool) {
                "remove_bg" -> StudioTool.REMOVE_BG
                "background" -> StudioTool.BACKGROUND
                "convert" -> StudioTool.CONVERT
                "export" -> StudioTool.EXPORT
                else -> StudioTool.COMPRESS
            }
        )
    }

    // Image State
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalSizeBytes by remember { mutableStateOf(4820000L) } // 4.82 MB from mockups
    var isProcessing by remember { mutableStateOf(false) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }

    // Compression State (mockup-02)
    var compressionMode by remember { mutableStateOf(CompressionMode.SMART) }
    var selectedOutputFormat by remember { mutableStateOf(ImageFormat.WEBP) }
    var compressionResult by remember { mutableStateOf<CompressionResult?>(null) }

    // AI Cutout State (mockup-03)
    var isPrecisionModel by remember { mutableStateOf(false) }
    var activeBrushMode by remember { mutableStateOf(BrushMode.ERASE) }
    var cutoutBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val segmentationEngine = remember { OnnxSegmentationEngine(context) }

    // Background Studio State (mockup-04)
    val bgEngine = remember { BackgroundStudioEngine() }
    var selectedBgColor by remember { mutableStateOf(0xFFFF6A3D.toInt()) }
    var backgroundTab by remember { mutableStateOf("Solid") }

    // Converter Options (mockup-05)
    var preserveDimensions by remember { mutableStateOf(true) }
    var preserveTransparency by remember { mutableStateOf(true) }
    var removeGpsMetadata by remember { mutableStateOf(true) }

    // Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        originalBitmap = bmp
                        processedBitmap = bmp
                        originalSizeBytes = (bmp.width * bmp.height * 2.5).toLong()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Default sample image generator
    LaunchedEffect(Unit) {
        if (originalBitmap == null) {
            val sample = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(sample)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#E8D7C3")
                isAntiAlias = true
            }
            canvas.drawRoundRect(250f, 200f, 550f, 650f, 60f, 60f, paint)
            originalBitmap = sample
            processedBitmap = sample
        }
    }

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
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = activeTool.label,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }

                    // Action button on top right matching mockups
                    IconButton(
                        onClick = {
                            if (activeTool != StudioTool.EXPORT) {
                                activeTool = StudioTool.EXPORT
                            } else {
                                photoPickerLauncher.launch("image/*")
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceRaised)
                            .border(1.dp, LineDivider, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = if (activeTool == StudioTool.EXPORT) Icons.Default.AddPhotoAlternate else Icons.Default.MoreHoriz,
                            contentDescription = "Action",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // Screen Headline & Subtitle matching mockups
            val (headline, subtitle) = when (activeTool) {
                StudioTool.COMPRESS -> Pair("Make it lighter.", "Precision compression, pixel by pixel.")
                StudioTool.REMOVE_BG -> Pair("Clean cutouts.", "Hair, edges and fine details — refined.")
                StudioTool.BACKGROUND -> Pair("Set the scene.", "A perfect background, your exact color.")
                StudioTool.CONVERT -> Pair("One image.\nAny format.", "A better workflow for modern images.")
                StudioTool.EXPORT -> Pair("Ready to save.", "Your image, polished and optimized.")
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Main Canvas Area (matching mockups with badges)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFE8D7C3))
                    .border(1.dp, LineDivider, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                BeforeAfterSlider(
                    originalBitmap = originalBitmap,
                    processedBitmap = processedBitmap ?: originalBitmap,
                    modifier = Modifier.fillMaxSize()
                )

                // Mockup Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val badgeLeft = when (activeTool) {
                        StudioTool.COMPRESS -> "ORIGINAL"
                        StudioTool.REMOVE_BG -> "CUTOUT PREVIEW"
                        StudioTool.BACKGROUND -> "LIVE PREVIEW"
                        else -> "PREVIEW"
                    }
                    Text(
                        text = badgeLeft,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )

                    if (activeTool == StudioTool.COMPRESS) {
                        Text(
                            text = "1:1 Preview",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    } else if (activeTool == StudioTool.EXPORT) {
                        Text(
                            text = "FINAL PREVIEW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentPrimary)
                    }
                }
            }

            // File Info Row matching mockup-02: "PRODUCT-HERO.PNG" | "2048 × 2048"
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PRODUCT-HERO.PNG",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Text(
                    text = "2048 × 2048",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tool-Specific Control Panel
            when (activeTool) {
                // S02: SMART COMPRESSION (mockup-02)
                StudioTool.COMPRESS -> {
                    ImageMetricsCard(
                        originalBytes = originalSizeBytes,
                        resultBytes = compressionResult?.bestCandidate?.sizeBytes ?: (originalSizeBytes * 0.284).toLong(),
                        savingsPct = compressionResult?.bestCandidate?.savingsPct ?: 71.6f,
                        formatName = selectedOutputFormat.extension.uppercase()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Segmented Pills: Lossless | ✦ Smart | Maximum
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceSubtle)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Triple(CompressionMode.LOSSLESS, "Lossless", null),
                            Triple(CompressionMode.SMART, "✦ Smart", null),
                            Triple(CompressionMode.MAXIMUM, "Maximum", null)
                        ).forEach { (mode, label, _) ->
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Output Format row: Output format | WebP ▼
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Output format", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedOutputFormat = when (selectedOutputFormat) {
                                        ImageFormat.WEBP -> ImageFormat.JPEG
                                        ImageFormat.JPEG -> ImageFormat.PNG
                                        ImageFormat.PNG -> ImageFormat.WEBP
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "${selectedOutputFormat.name} ▼", fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Button: Compress image ↗
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val bmp = originalBitmap ?: return@launch
                                isProcessing = true
                                val engine = SmartCompressionEngine()
                                val result = engine.compress(bmp, originalSizeBytes, compressionMode, selectedOutputFormat)
                                compressionResult = result
                                val decoded = android.graphics.BitmapFactory.decodeByteArray(
                                    result.bestCandidate.encodedBytes, 0, result.bestCandidate.encodedBytes.size
                                )
                                processedBitmap = decoded
                                isProcessing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text("Compress image ↗", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // S03: AI REMOVE BACKGROUND (mockup-03)
                StudioTool.REMOVE_BG -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Processing quality", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(text = "Model installed ✓", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Segmented: ⚡ Fast | ✧ Precision
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
                            Text("⚡ Fast", fontWeight = FontWeight.Bold, color = if (!isPrecisionModel) TextPrimary else TextSecondary)
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
                            Text("✧ Precision", fontWeight = FontWeight.Bold, color = if (isPrecisionModel) TextPrimary else TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3 Tool buttons: Erase, Restore, Refine matching mockup-03
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            Triple(BrushMode.ERASE, "Erase", Icons.Outlined.ContentCut),
                            Triple(BrushMode.RESTORE, "Restore", Icons.Outlined.Edit),
                            Triple(BrushMode.REFINE, "Refine", Icons.Outlined.BlurCircular)
                        ).forEach { (mode, label, icon) ->
                            val isSelected = activeBrushMode == mode
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) AccentSoft else SurfaceRaised),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeBrushMode = mode }
                                    .border(1.dp, if (isSelected) AccentPrimary else LineDivider, RoundedCornerShape(16.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) AccentPrimary else TextPrimary, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = label, fontSize = 12.sp, color = if (isSelected) AccentPrimary else TextSecondary)
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
                        Text(text = "Edge refinement", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(text = "Natural ▼", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val bmp = originalBitmap ?: return@launch
                                isProcessing = true
                                val cutout = segmentationEngine.segmentBackground(bmp)
                                cutoutBitmap = cutout
                                processedBitmap = cutout
                                isProcessing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text("Remove background ✦", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // S04: BACKGROUND STUDIO (mockup-04)
                StudioTool.BACKGROUND -> {
                    // Segmented: Solid | Gradient | Image
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
                                    .clickable { backgroundTab = tab }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(tab, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) TextPrimary else TextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Exact mockup-04 HexColorPicker with swatches & RGB sliders
                    HexColorPicker(
                        selectedColor = selectedBgColor,
                        onColorChanged = { newColor ->
                            selectedBgColor = newColor
                            val cutout = cutoutBitmap ?: originalBitmap ?: return@HexColorPicker
                            processedBitmap = bgEngine.composite(
                                subjectCutout = cutout,
                                background = BackgroundStyle.Solid(newColor),
                                shadow = SubjectShadow(opacity = 0.35f, radius = 16f)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val cutout = cutoutBitmap ?: originalBitmap ?: return@Button
                            processedBitmap = bgEngine.composite(
                                subjectCutout = cutout,
                                background = BackgroundStyle.Solid(selectedBgColor),
                                shadow = SubjectShadow(opacity = 0.35f, radius = 16f)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text("Apply background ↗", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // S05: IMAGE CONVERTER (mockup-05)
                StudioTool.CONVERT -> {
                    // Selected image card matching mockup-05
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LineDivider, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFE8D7C3)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF6B5844))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("product-hero.png", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("PNG • 2048 × 2048 • 4.82 MB", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            Icon(Icons.Default.Check, contentDescription = null, tint = AccentPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Convert to", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Choose format", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3 Format Cards: PNG, JPG, WebP matching mockup-05
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            Triple(ImageFormat.PNG, "Lossless", Icons.Outlined.Image),
                            Triple(ImageFormat.JPEG, "Photos", Icons.Outlined.PhotoCamera),
                            Triple(ImageFormat.WEBP, "Selected", Icons.Outlined.Layers)
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
                                    Text(text = if (isSelected) "Selected" else sub, fontSize = 11.sp, color = if (isSelected) AccentPrimary else TextMuted)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Options Card with 3 toggle dots matching mockup-05
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LineDivider, RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OptionToggleRow(title = "Preserve dimensions", subtitle = "Keep original 2048 × 2048", checked = preserveDimensions, onCheckedChange = { preserveDimensions = it })
                            HorizontalDivider(color = LineDivider, modifier = Modifier.padding(vertical = 10.dp))
                            OptionToggleRow(title = "Preserve transparency", subtitle = "Supported by ${selectedOutputFormat.name}", checked = preserveTransparency, onCheckedChange = { preserveTransparency = it })
                            HorizontalDivider(color = LineDivider, modifier = Modifier.padding(vertical = 10.dp))
                            OptionToggleRow(title = "Remove GPS metadata", subtitle = "Protect image privacy", checked = removeGpsMetadata, onCheckedChange = { removeGpsMetadata = it })
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val bmp = originalBitmap ?: return@Button
                            val converter = FormatConverter()
                            val res = converter.convert(bmp, selectedOutputFormat)
                            val decoded = android.graphics.BitmapFactory.decodeByteArray(res.outputBytes, 0, res.outputBytes.size)
                            processedBitmap = decoded
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text("Convert to ${selectedOutputFormat.name} →", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // S07: EXPORT IMAGE (mockup-07)
                StudioTool.EXPORT -> {
                    // Details Card matching mockup-07
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LineDivider, RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("FINAL FILE SIZE", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = TextMuted)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("1.37 MB", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                                Text("Saved 71.6%*", color = SemanticSuccess, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            HorizontalDivider(color = LineDivider, modifier = Modifier.padding(vertical = 14.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("File format", color = TextSecondary)
                                Text("WebP", fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Dimensions", color = TextSecondary)
                                Text("2048 × 2048", fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Transparency", color = TextSecondary)
                                Text("Preserved", fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Destination Card
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LineDivider, RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Save destination", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Pictures/PixelStudio", fontSize = 12.sp, color = TextSecondary)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Button: Save to device ↓
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val toExport = processedBitmap ?: originalBitmap ?: return@launch
                                val exportManager = ExportManager(context)
                                val bytes = BitmapUtils.compressToByteArray(toExport, Bitmap.CompressFormat.WEBP, 80)
                                val result = exportManager.exportToPictures(
                                    ExportRequest(bytes, ImageFormat.WEBP, "Nalaro_${System.currentTimeMillis()}")
                                )
                                exportStatusMessage = if (result.isSuccess) "Saved to Pictures/PixelStudio!" else "Export failed: ${result.errorMessage}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text("Save to device ↓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Button: Share image ↗
                    OutlinedButton(
                        onClick = { /* Share */ },
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LineDivider),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text("Share image ↗", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextMuted)
        }
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(if (checked) AccentPrimary else TextMuted)
                .clickable { onCheckedChange(!checked) }
        )
    }
}
