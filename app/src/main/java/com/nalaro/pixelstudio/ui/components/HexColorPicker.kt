package com.nalaro.pixelstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nalaro.pixelstudio.ui.theme.*

// Exact palette from mockup-04-background.png
private val SwatchColors = listOf(
    Color(0xFFFF6A3D), // Signature Studio Orange
    Color(0xFFF2EFEB), // Off-white cream
    Color(0xFF2C3038), // Dark slate
    Color(0xFF7FC1AD), // Mint green
    Color(0xFF8EABE2), // Soft slate blue
    Color(0xFFD8B3D3)  // Lavender pink
)

@Composable
fun HexColorPicker(
    selectedColor: Int,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentColor = Color(selectedColor)
    var redValue by remember(selectedColor) { mutableFloatStateOf(currentColor.red * 255f) }
    var greenValue by remember(selectedColor) { mutableFloatStateOf(currentColor.green * 255f) }
    var blueValue by remember(selectedColor) { mutableFloatStateOf(currentColor.blue * 255f) }

    val hexString = remember(selectedColor) {
        String.format("#%02X%02X%02X", (currentColor.red * 255).toInt(), (currentColor.green * 255).toInt(), (currentColor.blue * 255).toInt())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceRaised, RoundedCornerShape(20.dp))
            .border(1.dp, LineDivider, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        // Section Header: Color palette with "Save swatch +"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Color palette",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = "Save swatch +",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.clickable { /* Save */ }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Color Swatches Row (Rounded Rectangles matching mockup)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(SwatchColors) { color ->
                val isSelected = color.toArgb() == selectedColor
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) AccentPrimary else LineDivider,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            onColorChanged(color.toArgb())
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Custom HEX Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Custom HEX",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceSubtle)
                    .border(1.dp, LineDivider, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = hexString,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // RGB Sliders matching mockup
        RgbSliderRow(label = "Red", value = redValue, onValueChange = {
            redValue = it
            onColorChanged(Color(redValue / 255f, greenValue / 255f, blueValue / 255f, 1f).toArgb())
        })
        Spacer(modifier = Modifier.height(10.dp))
        RgbSliderRow(label = "Green", value = greenValue, onValueChange = {
            greenValue = it
            onColorChanged(Color(redValue / 255f, greenValue / 255f, blueValue / 255f, 1f).toArgb())
        })
        Spacer(modifier = Modifier.height(10.dp))
        RgbSliderRow(label = "Blue", value = blueValue, onValueChange = {
            blueValue = it
            onColorChanged(Color(redValue / 255f, greenValue / 255f, blueValue / 255f, 1f).toArgb())
        })
    }
}

@Composable
private fun RgbSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(text = value.toInt().toString(), style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = AccentPrimary,
                activeTrackColor = AccentPrimary,
                inactiveTrackColor = SurfaceSubtle
            ),
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
    }
}
