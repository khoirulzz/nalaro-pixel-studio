package com.nalaro.pixelstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nalaro.pixelstudio.ui.theme.*

@Composable
fun ImageMetricsCard(
    originalBytes: Long,
    resultBytes: Long,
    savingsPct: Float,
    formatName: String,
    modifier: Modifier = Modifier
) {
    val isSaved = resultBytes < originalBytes && savingsPct > 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceRaised, RoundedCornerShape(20.dp))
            .border(1.dp, LineDivider, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ORIGINAL",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatFileSize(originalBytes),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
            }

            Text(
                text = "➔",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AccentPrimary
            )

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "ESTIMATED OUTPUT",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatFileSize(resultBytes),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isSaved) AccentPrimary else TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Illustrative estimate",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Text(
                text = if (isSaved) "-${"%.1f".format(savingsPct)}%" else "No size saving",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSaved) SemanticSuccess else SemanticWarning
            )
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        "%.2f MB".format(mb)
    } else {
        "%.1f KB".format(kb)
    }
}
