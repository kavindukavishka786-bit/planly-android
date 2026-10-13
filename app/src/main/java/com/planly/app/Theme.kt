package com.planly.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

data class PlanlyColors(val background: Color, val surface: Color, val mint: Color, val text: Color,
    val secondaryText: Color, val green: Color, val onGreen: Color, val outline: Color, val highlight: Color,
    val lavender: Color, val butter: Color, val peach: Color, val sky: Color)
val LightPlanly = PlanlyColors(Color(0xFFFFFBF5), Color(0xFFFFFFFF), Color(0xFFE3F2EC),
    Color(0xFF183539), Color(0xFF476368), Color(0xFF126968), Color(0xFFFFFFFF), Color(0xFFD6E2DD), Color(0xFFFFFFFF),
    Color(0xFFEFE8FB), Color(0xFFFFF0BE), Color(0xFFFFE1D5), Color(0xFFDBEFF9))
val DarkPlanly = PlanlyColors(Color(0xFF14272A), Color(0xFF1C3436), Color(0xFF294C46),
    Color(0xFFECF5F0), Color(0xFFBACBC6), Color(0xFF9AD9CC), Color(0xFF172F30), Color(0xFF3E5557), Color(0xFF274043),
    Color(0xFF3D3554), Color(0xFF4A432D), Color(0xFF4C3733), Color(0xFF284451))
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
    val shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp))
    CompositionLocalProvider(LocalPlanly provides p) { MaterialTheme(colorScheme = colors, shapes = shapes, content = content) }
}
