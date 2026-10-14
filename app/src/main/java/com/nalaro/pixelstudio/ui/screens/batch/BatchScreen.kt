package com.nalaro.pixelstudio.ui.screens.batch

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nalaro.pixelstudio.ui.theme.*

data class MockBatchItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val isDone: Boolean = false,
    val isProcessing: Boolean = false,
    val progress: Float = 0f
)

@Composable
fun BatchScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPaused by remember { mutableStateOf(false) }

    var items by remember {
        mutableStateOf(
            listOf(
                MockBatchItem("1", "Hero-banner.png", "Saved 68% • WebP", isDone = true),
                MockBatchItem("2", "Product-02.png", "Processing • 74%", isProcessing = true, progress = 0.74f),
                MockBatchItem("3", "Gallery-03.jpg", "Waiting • JPG"),
                MockBatchItem("4", "Brand-cover.png", "Waiting • PNG")
            )
        )
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val added = uris.mapIndexed { idx, uri ->
                var displayName = "Image-${items.size + idx + 1}.jpg"
                try {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0 && !c.isNull(nameIdx)) {
                                displayName = c.getString(nameIdx) ?: displayName
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
                MockBatchItem(
                    id = "${System.currentTimeMillis()}_$idx",
                    name = displayName,
                    subtitle = "Waiting • WebP"
                )
            }
            items = items + added
        }
    }

    val completedCount = items.count { it.isDone }
    val totalCount = items.size.coerceAtLeast(1)
    val overallProgress = (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)

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
                            text = "Batch Processing",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }

                    IconButton(
                        onClick = { multiPhotoPicker.launch("image/*") },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceRaised)
                            .border(1.dp, LineDivider, RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = TextPrimary, modifier = Modifier.size(20.dp))
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Many files.\nOne workflow.",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp
                ),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Organize your images in a single queue.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Workflow Preset Card matching mockup-06
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
                        Text(
                            text = "WORKFLOW PRESET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "Edit ↗",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Web assets • Smart",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "WebP • Preserve dimensions • Strip GPS",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Queue Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Queue (${items.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Add images +",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentPrimary,
                    modifier = Modifier.clickable { multiPhotoPicker.launch("image/*") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Queue List matching mockup-06
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(items, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LineDivider, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFE8D7C3)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF6B5844), modifier = Modifier.size(20.dp))
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.subtitle,
                                        fontSize = 12.sp,
                                        color = if (item.isDone) SemanticSuccess else TextSecondary
                                    )
                                }

                                if (item.isDone) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = SemanticSuccess, modifier = Modifier.size(20.dp))
                                } else if (item.isProcessing) {
                                    CircularProgressIndicator(color = AccentPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(TextMuted)
                                    )
                                }
                            }

                            if (item.isProcessing) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { item.progress },
                                    color = AccentPrimary,
                                    trackColor = SurfaceSubtle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("$completedCount of ${items.size} completed", fontSize = 13.sp, color = TextSecondary)
                Text("${(overallProgress * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { overallProgress },
                color = AccentPrimary,
                trackColor = SurfaceSubtle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = { isPaused = !isPaused },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = if (isPaused) "Resume queue ▶" else "Pause queue ⏸",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
