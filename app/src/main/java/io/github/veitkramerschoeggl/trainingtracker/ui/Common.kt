package io.github.veitkramerschoeggl.trainingtracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.LocalAccent
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.Palette

/** Max width of the content column (`.wrap { max-width: 480px }`). */
fun Modifier.contentWidth(): Modifier = widthIn(max = 480.dp).fillMaxWidth()

/** Rounded dark card with a 1dp border — the prototype's basic building block. */
fun Modifier.card(radius: Dp, border: Color = Palette.Border, background: Color = Palette.Card): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(background).border(1.dp, border, shape)
}

/** Click without ripple — the prototype disables the tap highlight everywhere. */
fun Modifier.plainClickable(
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = role, onClick = onClick)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, fontSize = 11.sp, color = Palette.Muted, letterSpacing = 1.sp)
}

/** Full-width secondary button (`.export-btn`): border and text turn accent while pressed. */
@Composable
fun OutlineButton(icon: String, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val border by animateColorAsState(if (pressed) accent.main else Palette.Border, label = "border")
    val content = if (pressed) accent.main else Palette.Text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .card(12.dp, border = border)
            .plainClickable(interaction, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 14.sp)
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = content)
    }
}

/** The prototype's "▾": a small down-pointing triangle, rotated by [rotation] degrees. */
@Composable
fun DropdownArrow(color: Color, rotation: Float, modifier: Modifier = Modifier, width: Dp = 8.dp) {
    Canvas(modifier.size(width = width, height = width * 0.6f).rotate(rotation)) {
        drawPath(
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            },
            color,
        )
    }
}

/** Small text-glyph button (✎ ×), colored while pressed. */
@Composable
fun GlyphButton(glyph: String, pressedColor: Color, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .plainClickable(interaction, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 16.sp, color = if (pressed) pressedColor else Palette.Faint)
    }
}
