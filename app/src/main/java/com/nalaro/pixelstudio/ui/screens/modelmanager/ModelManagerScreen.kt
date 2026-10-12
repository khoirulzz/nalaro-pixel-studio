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
import com.nalaro.pixelstudio.ui.components.ModelCard
import com.nalaro.pixelstudio.ui.components.TopAppBarCompact
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
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }

    val totalStorageUsed = remember(models) {
        repository.getTotalStorageUsedBytes()
    }

    Scaffold(
        topBar = {
            TopAppBarCompact(
                title = "AI Model Manager",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(28.dp))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isCheckingUpdates = true
                                val hasUpdates = repository.checkForUpdates(force = true)
                                isCheckingUpdates = false
                                updateCheckMessage = if (hasUpdates) "Manifest checked: Updated!" else "Already up to date."
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Check for updates",
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
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Hugging Face Distribution Header Card
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
                                text = "Hugging Face Hub Distribution",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Models are quantized INT8 ONNX binaries hosted on immutable commit revisions. Downloaded models are stored in private internal storage and operate 100% offline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = LineDivider)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Storage & Wi-Fi row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Model Storage Usage",
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
                                    text = "Wi-Fi Only",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = isWifiOnly,
                                    onCheckedChange = { checked ->
                                        coroutineScope.launch {
                                            repository.setWifiOnly(checked)
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

            if (updateCheckMessage != null) {
                item {
                    Text(
                        text = updateCheckMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = SemanticSuccess,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            item {
                Text(
                    text = "Available Models (${models.size})",
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
                        }
                    },
                    onDownload = {
                        modelToDownload = model
                    },
                    onDelete = {
                        coroutineScope.launch {
                            repository.deleteModel(model.id)
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // User Confirmation Dialog
    if (modelToDownload != null) {
        val target = modelToDownload!!
        AlertDialog(
            onDismissRequest = { modelToDownload = null },
            title = {
                Text(text = "Download Model?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Do you want to download \"${target.name}\" (${formatFileSize(target.sizeBytes)}) from Hugging Face Hub?",
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Commit revision: ${target.remoteRevision.take(12)}\nLicense: ${target.license}\nNetwork: ${if (isWifiOnly) "Wi-Fi Only" else "Any Network"}",
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
                        }
                        modelToDownload = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Download", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDownload = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceRaised,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
