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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nalaro.pixelstudio.R
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
        recentExports = ExportManager(context).listExportedImages(limit = 4)
    }

    Scaffold(
        containerColor = SurfaceBase,
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceRaised)
                    .statusBarsPadding()
                    .height(62.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pixel_studio_mark),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text("Pixel Studio", modifier = Modifier.weight(1f),
                    fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                IconButton(onClick = onNavigateToModelManager) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Pengaturan model", tint = TextPrimary)
                }
            }
        }
    ) { inset ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inset),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Column {
                    Text("Editor", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextMuted)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceRaised)
                            .border(1.dp, LineDivider, RoundedCornerShape(14.dp))
                            .clickable { onNavigateToStudio("remove_bg") }
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mulai edit", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Buka foto", fontSize = 13.sp, color = TextSecondary)
                        }
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = TextSecondary)
                    }
                }
            }
            item {
                Column {
                    Text("Alat", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditorToolItem("Hapus Latar", Icons.Outlined.ContentCut,
                            Modifier.weight(1f)) { onNavigateToStudio("remove_bg") }
                        EditorToolItem("Kompresi", Icons.Outlined.Tune,
                            Modifier.weight(1f)) { onNavigateToStudio("compress") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditorToolItem("Ganti Latar", Icons.Outlined.Layers,
                            Modifier.weight(1f)) { onNavigateToStudio("background") }
                        EditorToolItem("Konversi", Icons.Outlined.SwapHoriz,
                            Modifier.weight(1f)) { onNavigateToStudio("convert") }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Batch", modifier = Modifier.weight(1f),
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("Buka", color = AccentPrimary, fontSize = 13.sp,
                        modifier = Modifier.clickable { onNavigateToBatch() }.padding(8.dp))
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceRaised)
                        .border(1.dp, LineDivider, RoundedCornerShape(12.dp))
                        .clickable { onNavigateToModelManager() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Memory, contentDescription = null, tint = TextSecondary)
                    Spacer(Modifier.width(12.dp))
                    Text("Model AI", modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium, color = TextPrimary)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted)
                }
            }
            if (recentExports.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Terbaru", modifier = Modifier.weight(1f),
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Semua", fontSize = 13.sp, color = AccentPrimary,
                            modifier = Modifier.clickable { onNavigateToLibrary() }.padding(8.dp))
                    }
                }
                items(recentExports, key = { it.uri.toString() }) { image ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenImageInStudio(image.uri) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = image.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceSubtle)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(image.displayName, maxLines = 1,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(formatFileSize(image.sizeBytes), fontSize = 11.sp, color = TextMuted)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorToolItem(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceRaised)
            .border(1.dp, LineDivider, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(23.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}
