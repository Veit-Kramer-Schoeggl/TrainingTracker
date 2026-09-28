package io.github.veitkramerschoeggl.trainingtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.LocalAccent
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.Palette
import io.github.veitkramerschoeggl.trainingtracker.update.UpdateState

/** Notice at the top when a new version is available, with download progress. */
@Composable
fun UpdateBanner(state: UpdateState, onUpdate: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(
        modifier = modifier.card(14.dp, border = accent.dim).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            UpdateState.Idle -> Unit
            is UpdateState.Available -> {
                Title("Update verfügbar · Version ${state.manifest.versionName}")
                if (state.manifest.releaseNotes.isNotEmpty()) {
                    Text(state.manifest.releaseNotes, fontSize = 12.sp, color = Palette.Soft, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                Actions(primary = "Aktualisieren", onPrimary = onUpdate, onDismiss = onDismiss)
            }
            is UpdateState.Downloading -> {
                val percent = state.progress?.let { " ${(it * 100).toInt()} %" }.orEmpty()
                Title("Update wird geladen …$percent")
                ProgressBar(state.progress ?: 0f)
            }
            is UpdateState.Installing, is UpdateState.ConfirmationRequired -> {
                Title("Update wird installiert …")
                Text("Die App wird dabei kurz beendet – danach einfach wieder öffnen. Deine Daten bleiben erhalten.", fontSize = 12.sp, color = Palette.Soft)
            }
            is UpdateState.Failed -> {
                Title("Update auf Version ${state.manifest.versionName} fehlgeschlagen")
                Text(state.message, fontSize = 12.sp, color = Palette.Soft)
                Actions(primary = "Erneut versuchen", onPrimary = onUpdate, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
}

@Composable
private fun Actions(primary: String, onPrimary: () -> Unit, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            primary,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accent.main)
                .plainClickable(onClick = onPrimary)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Background,
        )
        Text(
            "Später",
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .plainClickable(onClick = onDismiss)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            fontSize = 13.sp,
            color = Palette.Subtle,
        )
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Palette.Border),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(accent.main),
        )
    }
}
