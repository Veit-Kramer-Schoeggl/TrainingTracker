package io.github.veitkramerschoeggl.trainingtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.veitkramerschoeggl.trainingtracker.data.ImportMode
import io.github.veitkramerschoeggl.trainingtracker.domain.GermanFormat
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.Palette

/** Asked when an imported file contains days that already exist in the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(preview: ImportPreview, onConfirm: (ImportMode) -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Palette.Card) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Excel-Import", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                Text(
                    "Die Datei enthält ${GermanFormat.days(preview.days)} mit ${GermanFormat.number(preview.reps)} Klimmzügen. " +
                        "${GermanFormat.days(preview.existingDays)} davon ${if (preview.existingDays == 1) "ist" else "sind"} bereits in der App.",
                    fontSize = 14.sp,
                    color = Palette.Soft,
                )
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onConfirm(ImportMode.ADD_MISSING_DAYS) }) { Text("Nur neue Tage hinzufügen") }
                    TextButton(onClick = { onConfirm(ImportMode.REPLACE_ALL) }) { Text("Alles durch Datei ersetzen") }
                    TextButton(onClick = onDismiss) { Text("Abbrechen", color = Palette.Subtle) }
                }
            }
        }
    }
}

/** Explains the one-time "install unknown apps" permission before opening the settings. */
@Composable
fun InstallPermissionDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Updates erlauben") },
        text = {
            Text(
                "Damit sich die App selbst aktualisieren kann, muss sie einmalig Apps installieren dürfen. " +
                    "Aktiviere in den Einstellungen „Aus dieser Quelle zulassen“ und kehre dann zurück – " +
                    "das Update startet automatisch.",
            )
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("Einstellungen öffnen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = Palette.Subtle) } },
    )
}
