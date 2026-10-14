package com.nalaro.pixelstudio.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AccentPrimary,
    onPrimary = SurfaceRaised,
    primaryContainer = AccentSoft,
    onPrimaryContainer = AccentPrimary,
    secondary = TextSecondary,
    onSecondary = SurfaceRaised,
    background = SurfaceBase,
    onBackground = TextPrimary,
    surface = SurfaceRaised,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextSecondary,
    outline = LineDivider,
    error = SemanticError,
    onError = SurfaceRaised
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun NalaroPixelStudioTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = SurfaceBase.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = SurfaceRaised.toArgb()
            // Set status bar & nav bar icons to dark for light mode
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
