package io.github.climbintelligence.ui.theme

import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

object ClimbColors {
    val background = Color(0xFF000000)
    val surface = Color(0xFF111111)
    val surfaceVariant = Color(0xFF1A1A1A)

    val text = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFAAAAAA)
    val dim = Color(0xFF666666)
    val muted = Color(0xFF333333)

    val primary = Color(0xFF4CAF50)
    val onPrimary = Color(0xFF000000)

    val optimal = Color(0xFF4CAF50)
    val attention = Color(0xFFFFC107)
    val problem = Color(0xFFF44336)

    val divider = Color(0xFF222222)
}

@Immutable
data class ClimbColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val text: Color,
    val textSecondary: Color,
    val dim: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val optimal: Color,
    val attention: Color,
    val problem: Color,
    val divider: Color
)

val LocalClimbColors = staticCompositionLocalOf {
    ClimbColorScheme(
        background = ClimbColors.background,
        surface = ClimbColors.surface,
        surfaceVariant = ClimbColors.surfaceVariant,
        text = ClimbColors.text,
        textSecondary = ClimbColors.textSecondary,
        dim = ClimbColors.dim,
        muted = ClimbColors.muted,
        primary = ClimbColors.primary,
        onPrimary = ClimbColors.onPrimary,
        optimal = ClimbColors.optimal,
        attention = ClimbColors.attention,
        problem = ClimbColors.problem,
        divider = ClimbColors.divider
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = ClimbColors.primary,
    onPrimary = ClimbColors.onPrimary,
    secondary = ClimbColors.optimal,
    background = ClimbColors.background,
    surface = ClimbColors.surface,
    onBackground = ClimbColors.text,
    onSurface = ClimbColors.text
)

@Composable
fun ClimbIntelligenceTheme(
    content: @Composable () -> Unit
) {
    val colors = ClimbColorScheme(
        background = ClimbColors.background,
        surface = ClimbColors.surface,
        surfaceVariant = ClimbColors.surfaceVariant,
        text = ClimbColors.text,
        textSecondary = ClimbColors.textSecondary,
        dim = ClimbColors.dim,
        muted = ClimbColors.muted,
        primary = ClimbColors.primary,
        onPrimary = ClimbColors.onPrimary,
        optimal = ClimbColors.optimal,
        attention = ClimbColors.attention,
        problem = ClimbColors.problem,
        divider = ClimbColors.divider
    )

    val configuration = LocalConfiguration.current
    val layoutDirection = if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalClimbColors provides colors
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            content = content
        )
    }
}

object Theme {
    val colors: ClimbColorScheme
        @Composable get() = LocalClimbColors.current
}
