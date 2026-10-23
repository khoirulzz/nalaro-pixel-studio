package com.nalaro.pixelstudio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nalaro.pixelstudio.ui.theme.*

enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Beranda", Icons.Outlined.Home),
    STUDIO("Studio", Icons.Outlined.AutoFixHigh),
    BATCH("Batch", Icons.Outlined.Layers),
    LIBRARY("Galeri", Icons.Outlined.GridView)
}

@Composable
fun BottomNavigationCompact(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceRaised,
        shadowElevation = 8.dp
    ) {
        Column {
            HorizontalDivider(color = LineDivider, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    val tintColor = if (isSelected) AccentPrimary else TextMuted

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tintColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tab.label,
                            fontSize = 11.sp,
                            color = tintColor,
                            style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
