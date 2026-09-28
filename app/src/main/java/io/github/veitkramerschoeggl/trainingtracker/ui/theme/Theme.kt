package io.github.veitkramerschoeggl.trainingtracker.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import io.github.veitkramerschoeggl.trainingtracker.data.AccentColor

/** Colors of the prototype's stylesheet. */
object Palette {
    val Background = Color(0xFF0F0F13)
    val Text = Color(0xFFE8E6E1)
    val Card = Color(0xFF1A1A22)
    val Border = Color(0xFF2A2A36)
    val Muted = Color(0xFF5A5A6A)
    val Faint = Color(0xFF3A3A4A)
    val Subtle = Color(0xFF7A7A8A)
    val Soft = Color(0xFF9090A0)
    val DayText = Color(0xFFC8C8D0)
    val Control = Color(0xFF23232F)
    val ControlPressed = Color(0xFF2E2E3D)
    val BarInactive = Color(0xFF2E2E3D)
    val WeekHeader = Color(0xFF1E1E28)
    val TodayBorder = Color(0xFF4A5A8A)
    val TodayBadge = Color(0xFF8AA0E8)
    val Danger = Color(0xFFE05050)
    val White = Color.White
}

/** `--acc`, `--acc-dim`, `--acc-dark` of the selected color theme. */
@Immutable
data class AccentPalette(val main: Color, val dim: Color, val dark: Color)

fun AccentColor.palette(): AccentPalette = when (this) {
    AccentColor.GREEN -> AccentPalette(Color(0xFFC8F542), Color(0xFF3A4A1A), Color(0xFF4A6A18))
    AccentColor.VIOLET -> AccentPalette(Color(0xFFC084FC), Color(0xFF3B1F5A), Color(0xFF6B33A0))
    AccentColor.BLUE -> AccentPalette(Color(0xFF60A5FA), Color(0xFF1A2F4A), Color(0xFF2563AB))
}

val LocalAccent = compositionLocalOf { AccentColor.GREEN.palette() }

/** Plain text style like the browser's: no extra line height or letter spacing. */
private val BaseTextStyle = TextStyle(
    color = Palette.Text,
    fontSize = 14.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
fun TrainingTrackerTheme(accent: AccentColor, content: @Composable () -> Unit) {
    val target = accent.palette()
    val main by animateColorAsState(target.main, tween(250), label = "accent")
    val dim by animateColorAsState(target.dim, tween(250), label = "accentDim")
    val dark by animateColorAsState(target.dark, tween(250), label = "accentDark")

    val colors = darkColorScheme(
        primary = main,
        onPrimary = Palette.Background,
        primaryContainer = dim,
        onPrimaryContainer = main,
        secondary = main,
        onSecondary = Palette.Background,
        background = Palette.Background,
        onBackground = Palette.Text,
        surface = Palette.Card,
        onSurface = Palette.Text,
        surfaceVariant = Palette.Control,
        onSurfaceVariant = Palette.Soft,
        surfaceContainerLowest = Palette.Background,
        surfaceContainerLow = Palette.Card,
        surfaceContainer = Palette.Card,
        surfaceContainerHigh = Palette.Card,
        surfaceContainerHighest = Palette.Control,
        inverseSurface = Palette.Control,
        inverseOnSurface = Palette.Text,
        inversePrimary = main,
        outline = Palette.Border,
        outlineVariant = Palette.Border,
        error = Palette.Danger,
    )
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalAccent provides AccentPalette(main, dim, dark),
            LocalTextStyle provides BaseTextStyle,
            content = content,
        )
    }
}
