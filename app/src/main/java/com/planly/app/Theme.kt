package com.planly.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

data class PlanlyColors(val background: Color, val surface: Color, val mint: Color, val text: Color,
    val secondaryText: Color, val green: Color, val onGreen: Color, val outline: Color, val highlight: Color)
val LightPlanly = PlanlyColors(Color(0xFFFFFDF8), Color(0xFFFFFDF8), Color(0xFFEAF3EC),
    Color(0xFF20342B), Color(0xFF465A4E), Color(0xFF285D46), Color(0xFFFFFDF8), Color(0xFFD0DFD4), Color(0xFFFFFFFF))
val DarkPlanly = PlanlyColors(Color(0xFF17231D), Color(0xFF1F2E25), Color(0xFF2A3B30),
    Color(0xFFEDF5EC), Color(0xFFB7CABC), Color(0xFFB0D4BC), Color(0xFF1B3022), Color(0xFF455B4B), Color(0xFF374D3D))
val LocalPlanly = staticCompositionLocalOf { LightPlanly }
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable fun PlanlyTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    val p = if (dark) DarkPlanly else LightPlanly
    val context = LocalContext.current
    SideEffect {
        context.activity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    val colors = scheme.copy(primary = p.green, onPrimary = p.onGreen,
        primaryContainer = p.mint, onPrimaryContainer = p.text,
        secondary = p.green, onSecondary = p.onGreen, secondaryContainer = p.mint, onSecondaryContainer = p.text,
        tertiary = p.green, onTertiary = p.onGreen, tertiaryContainer = p.mint, onTertiaryContainer = p.text,
        background = p.background, onBackground = p.text, surface = p.surface, onSurface = p.text,
        surfaceVariant = p.mint, onSurfaceVariant = p.secondaryText,
        surfaceContainer = p.surface, surfaceContainerLow = p.surface, surfaceContainerHigh = p.mint,
        surfaceContainerHighest = p.mint, surfaceContainerLowest = p.background,
        surfaceBright = p.surface, surfaceDim = p.background, outline = p.outline, outlineVariant = p.outline)
    CompositionLocalProvider(LocalPlanly provides p) { MaterialTheme(colorScheme = colors, content = content) }
}
