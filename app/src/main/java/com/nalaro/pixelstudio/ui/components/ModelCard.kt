package com.nalaro.pixelstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nalaro.pixelstudio.data.models.ModelEntity
import com.nalaro.pixelstudio.data.models.ModelStatus
import com.nalaro.pixelstudio.ui.theme.*

@Composable
fun ModelCard(
    model: ModelEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInstalled = model.status == ModelStatus.INSTALLED
    val isUpdate = model.status == ModelStatus.UPDATE_AVAILABLE
    val isDownloading = model.status == ModelStatus.DOWNLOADING

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) AccentPrimary else LineDivider,
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        if (false) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FAST",
                                fontSize = 10.sp,
                                color = AccentPrimary,
                                modifier = Modifier
                                    .background(AccentSoft, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${formatFileSize(model.sizeBytes)} · ${model.license}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }

                // Status Chip
                val (badgeText, badgeBg, badgeColor) = when (model.status) {
                    ModelStatus.INSTALLED -> Triple("Installed", SemanticSuccess.copy(alpha = 0.15f), SemanticSuccess)
                    ModelStatus.UPDATE_AVAILABLE -> Triple("Update", SemanticWarning.copy(alpha = 0.15f), SemanticWarning)
                    ModelStatus.DOWNLOADING -> Triple("Downloading", AccentSoft, AccentPrimary)
                    ModelStatus.VERIFYING -> Triple("Verifying", AccentSoft, AccentPrimary)
                    ModelStatus.ERROR -> Triple("Error", SemanticError.copy(alpha = 0.15f), SemanticError)
                    ModelStatus.NOT_INSTALLED -> Triple("Remote", SurfaceSubtle, TextMuted)
                }

                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    color = badgeColor,
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Specs Row (Size, RAM, License)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatFileSize(model.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "~${model.ramRequirementMb} MB RAM",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = model.license,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            // Downloading Progress
            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { model.downloadProgress },
                    color = AccentPrimary,
                    trackColor = SurfaceSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(model.downloadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            if (model.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = model.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = SemanticError
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isInstalled || isUpdate) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete model",
                            tint = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (isUpdate) {
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = SemanticWarning)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Update (${formatFileSize(model.sizeBytes)})", color = Color.White)
                    }
                } else if (!isInstalled && !isDownloading) {
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download (${formatFileSize(model.sizeBytes)})", color = Color.White)
                    }
                } else if (isInstalled) {
                    OutlinedButton(
                        onClick = onSelect,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isSelected) AccentPrimary else TextPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AccentPrimary else LineDivider
                        )
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Aktif")
                        } else {
                            Text("Gunakan")
                        }
                    }
                }
            }
        }
    }
}
