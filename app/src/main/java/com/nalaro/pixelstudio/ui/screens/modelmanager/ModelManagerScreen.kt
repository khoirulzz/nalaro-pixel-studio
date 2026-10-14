package com.nalaro.pixelstudio.ui.screens.modelmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nalaro.pixelstudio.data.models.ModelEntity
import com.nalaro.pixelstudio.data.repository.ModelRepository
import com.nalaro.pixelstudio.ui.components.AlertKind
import com.nalaro.pixelstudio.ui.components.ModelCard
import com.nalaro.pixelstudio.ui.components.StatusAlertBanner
import com.nalaro.pixelstudio.ui.components.TopAppBarCompact
import com.nalaro.pixelstudio.ui.components.UiAlert
import com.nalaro.pixelstudio.ui.components.formatFileSize
import com.nalaro.pixelstudio.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ModelManagerScreen(
    repository: ModelRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val models by repository.allModels.collectAsState(initial = emptyList())
    val isWifiOnly by repository.isWifiOnly.collectAsState(initial = true)
    val selectedModelId by repository.selectedModelId.collectAsState(initial = "birefnet-general-lite")

    var modelToDownload by remember { mutableStateOf<ModelEntity?>(null) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var currentAlert by remember { mutableStateOf<UiAlert?>(null) }

    val totalStorageUsed = remember(models) {
        repository.getTotalStorageUsedBytes()
    }

    Scaffold(
        topBar = {
            TopAppBarCompact(
                title = "AI Model Manager",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Kembali", tint = TextPrimary, modifier = Modifier.size(28.dp))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isCheckingUpdates = true
                                val hasUpdates = repository.checkForUpdates(force = true)
                                isCheckingUpdates = false
                                currentAlert = if (hasUpdates) {
                                    UiAlert(
                                        kind = AlertKind.SUCCESS,
                                        title = "Manifest model diperbarui",
                                        message = "Daftar model ONNX terbaru berhasil disinkronkan."
                                    )
                                } else {
                                    UiAlert(
                                        kind = AlertKind.INFO,
                                        title = "Daftar model sudah terbaru",
                                        message = "Semua definisi model sudah berada pada revisi terkini."
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Periksa pembaruan",
                            tint = if (isCheckingUpdates) AccentPrimary else TextPrimary
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        .border(1.5.dp, SemanticSuccess.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
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
                                        .background(SemanticSuccess.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SemanticSuccess, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Built-in Offline AI Engine",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "On-Device Subject Segmenter + Smart Matting (<5 MB)",
                                        fontSize = 11.sp,
                                        color = SemanticSuccess,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Text(
                                text = "Aktif",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SemanticSuccess,
                                modifier = Modifier
                                    .background(SemanticSuccess.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sudah terpasang langsung di dalam aplikasi dan siap menghapus background foto orang, produk, maupun objek secara offline tanpa perlu mengunduh model besar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            item {
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
                                Icon(Icons.Outlined.CloudQueue, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Model ONNX Tambahan (Opsional)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Model ONNX terkuantisasi INT8 dari Hugging Face Hub untuk kebutuhan tingkat lanjut. Berjalan 100% lokal setelah diunduh.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = LineDivider)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Penyimpanan Model ONNX",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = formatFileSize(totalStorageUsed),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentPrimary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Hanya Wi-Fi",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = isWifiOnly,
                                    onCheckedChange = { checked ->
                                        coroutineScope.launch {
                                            repository.setWifiOnly(checked)
                                            currentAlert = UiAlert(
                                                kind = AlertKind.INFO,
                                                title = if (checked) "Mode Wi-Fi Only diaktifkan" else "Unduhan jaringan seluler diizinkan",
                                                message = if (checked) "Model hanya akan diunduh saat terhubung ke Wi-Fi." else "Model dapat diunduh melalui Wi-Fi maupun data seluler."
                                            )
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AccentPrimary,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = SurfaceSubtle
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Model ONNX Tersedia (${models.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }

            items(models, key = { it.id }) { model ->
                ModelCard(
                    model = model,
                    isSelected = model.id == selectedModelId,
                    onSelect = {
                        coroutineScope.launch {
                            repository.setSelectedModel(model.id)
                            currentAlert = UiAlert(
                                kind = AlertKind.SUCCESS,
                                title = "Model aktif dipilih",
                                message = "'${model.name}' ditetapkan sebagai model ONNX utama."
                            )
                        }
                    },
                    onDownload = {
                        modelToDownload = model
                    },
                    onDelete = {
                        coroutineScope.launch {
                            repository.deleteModel(model.id)
                            currentAlert = UiAlert(
                                kind = AlertKind.INFO,
                                title = "Model dihapus",
                                message = "File '${model.name}' telah dihapus dari penyimpanan perangkat."
                            )
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (modelToDownload != null) {
        val target = modelToDownload!!
        AlertDialog(
            onDismissRequest = { modelToDownload = null },
            title = {
                Text(text = "Unduh Model ONNX?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Unduh \"${target.name}\" (${formatFileSize(target.sizeBytes)}) dari Hugging Face Hub?",
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Revisi commit: ${target.remoteRevision.take(12)}\nLisensi: ${target.license}\nJaringan: ${if (isWifiOnly) "Hanya Wi-Fi" else "Semua Jaringan"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.enqueueDownload(target.id)
                            currentAlert = UiAlert(
                                kind = AlertKind.INFO,
                                title = "Unduhan '${target.name}' dimulai",
                                message = "Proses pengunduhan berjalan di latar belakang."
                            )
                        }
                        modelToDownload = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Unduh", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDownload = null }) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = SurfaceRaised,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
