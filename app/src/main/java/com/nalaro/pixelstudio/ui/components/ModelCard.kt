package com.nalaro.pixelstudio.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    val installed = model.status == ModelStatus.INSTALLED
    val updating = model.status == ModelStatus.UPDATE_AVAILABLE
    val downloading = model.status == ModelStatus.DOWNLOADING ||
        model.status == ModelStatus.VERIFYING
    Card(
        modifier = modifier.fillMaxWidth()
            .border(1.dp, LineDivider, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(3.dp))
                    Text("${formatFileSize(model.sizeBytes)}  ·  ${model.license}",
                        fontSize = 12.sp, color = TextMuted)
                }
                if (installed && isSelected) {
                    Text("Aktif", color = SemanticSuccess, fontSize = 12.sp)
                }
            }
            if (model.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(model.description, fontSize = 12.sp, color = TextSecondary)
            }
            if (downloading) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { model.downloadProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = AccentPrimary,
                    trackColor = SurfaceSubtle
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (model.status == ModelStatus.VERIFYING) "Memverifikasi" else "${(model.downloadProgress * 100).toInt()}%",
                    color = TextSecondary, fontSize = 11.sp
                )
            }
            if (!model.errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(model.errorMessage, fontSize = 12.sp, color = SemanticError)
            }
            if (!downloading) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (installed || updating) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Outlined.DeleteOutline,
                                contentDescription = "Hapus model", tint = TextMuted)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (installed) {
                        OutlinedButton(
                            onClick = onSelect,
                            enabled = !isSelected,
                            border = androidx.compose.foundation.BorderStroke(1.dp, LineDivider),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text(if (isSelected) "Digunakan" else "Gunakan", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                        ) {
                            Text(if (updating) "Perbarui" else "Unduh", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
