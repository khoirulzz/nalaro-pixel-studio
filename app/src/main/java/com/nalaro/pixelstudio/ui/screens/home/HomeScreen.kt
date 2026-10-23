package com.nalaro.pixelstudio.ui.screens.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nalaro.pixelstudio.core.export.ExportManager
import com.nalaro.pixelstudio.core.export.ExportedImageItem
import com.nalaro.pixelstudio.ui.components.formatFileSize
import com.nalaro.pixelstudio.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToStudio: (tool: String) -> Unit,
    onNavigateToBatch: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToModelManager: () -> Unit,
    onOpenImageInStudio: (Uri) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var recentExports by remember { mutableStateOf<List<ExportedImageItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        recentExports = ExportManager(context).listExportedImages(limit = 3)
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceBase)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CropFree,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "NALARO",
                                fontSize = 9.sp,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Text(
                                text = "PIXEL STUDIO",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = TextPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = onNavigateToModelManager,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceRaised)
                            .border(1.dp, LineDivider, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Pengaturan & Model AI",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pixel Studio",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 38.sp
                    ),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Edit foto · Offline",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = TextSecondary
                )
            }

            // Featured Tool Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToStudio("remove_bg") }
                        .border(1.dp, LineDivider, RoundedCornerShape(24.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFE8D7C3), Color(0xFFCBB196))
                                )
                            )
                            .padding(22.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "REMOVE BG",
                                    fontSize = 10.sp,
                                    letterSpacing = 1.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6B5844)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Hapus Latar",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 28.sp,
                                    color = Color(0xFF2C2218)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Hasil transparan, siap diekspor.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF5A4938)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { onNavigateToStudio("remove_bg") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(14.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                ) {
                                    Text(
                                        "Buka Editor",
                                        color = Color(0xFF2C2218),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFFF4EDE4))
                                    .border(1.dp, Color(0xFFDAC7B2), RoundedCornerShape(22.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AccentSoft),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoFixHigh,
                                            contentDescription = null,
                                            tint = AccentPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "AI LOKAL",
                                        fontSize = 9.sp,
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF554433)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section "Editor"
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Alat Studio",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Batch",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentPrimary,
                        modifier = Modifier.clickable { onNavigateToBatch() }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ToolGridItemLight(
                            title = "Remove BG",
                            description = "Hapus latar otomatis",
                            icon = Icons.Outlined.ContentCut,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToStudio("remove_bg") }
                        )
                        ToolGridItemLight(
                            title = "Compress",
                            description = "Perkecil ukuran file",
                            icon = Icons.Outlined.Layers,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToStudio("compress") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ToolGridItemLight(
                            title = "Background",
                            description = "Warna & latar baru",
                            icon = Icons.Outlined.Tonality,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToStudio("background") }
                        )
                        ToolGridItemLight(
                            title = "Convert",
                            description = "PNG, JPG, WebP",
                            icon = Icons.Outlined.SwapHoriz,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToStudio("convert") }
                        )
                    }
                }
            }

            // Batch Processing Quick Entry Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToBatch() }
                        .border(1.dp, LineDivider, RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Batch Processing",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Proses & simpan banyak gambar sekaligus",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }
                }
            }

            // AI Model Manager Entry Bar
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToModelManager() }
                        .border(1.dp, LineDivider, RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Model AI",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Kelola model offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }
                }
            }

            if (recentExports.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terbaru",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Lihat semua",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentPrimary,
                            modifier = Modifier.clickable { onNavigateToLibrary() }
                        )
                    }
                }

                items(recentExports, key = { it.uri.toString() }) { exported ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenImageInStudio(exported.uri) }
                            .border(1.dp, LineDivider, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = exported.uri,
                                contentDescription = exported.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceSubtle)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exported.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${formatFileSize(exported.sizeBytes)} • Ketuk untuk buka di Studio",
                                    fontSize = 11.sp,
                                    color = SemanticSuccess
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextMuted
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ToolGridItemLight(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .border(1.dp, LineDivider, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}
