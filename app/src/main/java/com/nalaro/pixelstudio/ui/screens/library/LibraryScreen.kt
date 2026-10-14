package com.nalaro.pixelstudio.ui.screens.library

import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nalaro.pixelstudio.core.export.ExportManager
import com.nalaro.pixelstudio.core.export.ExportedImageItem
import com.nalaro.pixelstudio.ui.components.AlertKind
import com.nalaro.pixelstudio.ui.components.StatusAlertBanner
import com.nalaro.pixelstudio.ui.components.TopAppBarCompact
import com.nalaro.pixelstudio.ui.components.UiAlert
import com.nalaro.pixelstudio.ui.components.formatFileSize
import com.nalaro.pixelstudio.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    onOpenInStudio: (Uri) -> Unit = {},
    onNavigateToStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exportManager = remember { ExportManager(context) }

    var exportedItems by remember { mutableStateOf<List<ExportedImageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentAlert by remember { mutableStateOf<UiAlert?>(null) }

    val refreshLibrary: () -> Unit = {
        coroutineScope.launch {
            isLoading = true
            exportedItems = exportManager.listExportedImages(limit = 100)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshLibrary()
    }

    Scaffold(
        topBar = {
            TopAppBarCompact(
                title = "Galeri Hasil (Library)",
                actions = {
                    IconButton(onClick = {
                        refreshLibrary()
                        currentAlert = UiAlert(
                            kind = AlertKind.INFO,
                            title = "Daftar diperbarui",
                            message = "Menampilkan hasil ekspor terbaru dari Pictures/PixelStudio."
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Muat ulang",
                            tint = TextPrimary
                        )
                    }
                }
            )
        },
        containerColor = SurfaceBase,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (currentAlert != null) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusAlertBanner(
                        alert = currentAlert,
                        onDismiss = { currentAlert = null }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, LineDivider, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AccentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Lokasi Penyimpanan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Pictures/PixelStudio • ${exportedItems.size} file tersimpan",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Hasil Ekspor (${exportedItems.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentPrimary)
                    }
                }
            } else if (exportedItems.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LineDivider, RoundedCornerShape(22.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AccentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = AccentPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Belum Ada Gambar Tersimpan",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Gambar yang kamu simpan dari Studio atau Batch Processing akan otomatis muncul di sini.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToStudio,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Buka Studio Sekarang", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(exportedItems, key = { it.uri.toString() }) { item ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenInStudio(item.uri) }
                            .border(1.dp, LineDivider, RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceSubtle)
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val dimInfo = if (item.width > 0 && item.height > 0) " • ${item.width}×${item.height}" else ""
                                Text(
                                    text = "${formatFileSize(item.sizeBytes)}$dimInfo",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SemanticSuccess
                                )
                            }

                            IconButton(
                                onClick = {
                                    try {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = item.mimeType
                                            putExtra(Intent.EXTRA_STREAM, item.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan gambar"))
                                    } catch (t: Throwable) {
                                        currentAlert = UiAlert(
                                            kind = AlertKind.ERROR,
                                            title = "Gagal membagikan",
                                            message = t.localizedMessage ?: "Tidak dapat membagikan file."
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = "Bagikan", tint = AccentPrimary)
                            }

                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val deleted = exportManager.deleteExportedImage(item.uri)
                                        if (deleted) {
                                            exportedItems = exportedItems.filterNot { it.uri == item.uri }
                                            currentAlert = UiAlert(
                                                kind = AlertKind.SUCCESS,
                                                title = "File berhasil dihapus",
                                                message = "${item.displayName} telah dihapus dari penyimpanan."
                                            )
                                        } else {
                                            currentAlert = UiAlert(
                                                kind = AlertKind.ERROR,
                                                title = "Gagal menghapus file",
                                                message = "File mungkin sudah dipindahkan atau dihapus."
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus", tint = TextMuted)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}
