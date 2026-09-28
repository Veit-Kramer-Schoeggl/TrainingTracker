package io.github.veitkramerschoeggl.trainingtracker.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.veitkramerschoeggl.trainingtracker.BuildConfig
import io.github.veitkramerschoeggl.trainingtracker.TrainingTrackerApp
import io.github.veitkramerschoeggl.trainingtracker.data.AccentColor
import io.github.veitkramerschoeggl.trainingtracker.data.ImportMode
import io.github.veitkramerschoeggl.trainingtracker.data.SettingsRepository
import io.github.veitkramerschoeggl.trainingtracker.data.TrainingRepository
import io.github.veitkramerschoeggl.trainingtracker.domain.GermanFormat
import io.github.veitkramerschoeggl.trainingtracker.domain.Stats
import io.github.veitkramerschoeggl.trainingtracker.domain.TrainingDay
import io.github.veitkramerschoeggl.trainingtracker.domain.TrainingStats
import io.github.veitkramerschoeggl.trainingtracker.domain.TrainingWeek
import io.github.veitkramerschoeggl.trainingtracker.excel.ExcelExport
import io.github.veitkramerschoeggl.trainingtracker.excel.ExcelFiles
import io.github.veitkramerschoeggl.trainingtracker.excel.ImportException
import io.github.veitkramerschoeggl.trainingtracker.excel.ImportedSet
import io.github.veitkramerschoeggl.trainingtracker.update.UpdateCheckResult
import io.github.veitkramerschoeggl.trainingtracker.update.UpdateManager
import io.github.veitkramerschoeggl.trainingtracker.update.UpdateState
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything derived from the stored sets. [days] and [weeks] are sorted oldest first. */
data class TrainingData(
    val days: List<TrainingDay>,
    val weeks: List<TrainingWeek>,
    val stats: TrainingStats,
    val today: LocalDate,
)

/** The input card. [editing] is the day being edited, null when adding. */
data class EntryForm(
    val date: LocalDate,
    val reps: String = "",
    val note: String = "",
    val editing: LocalDate? = null,
)

/** Snackbar message, optionally with "Rückgängig". */
class UiMessage(val text: String, val undo: (suspend () -> Unit)? = null)

data class ImportPreview(val sets: List<ImportedSet>, val days: Int, val reps: Int, val existingDays: Int)

class MainViewModel(
    private val repository: TrainingRepository,
    private val settings: SettingsRepository,
    private val updates: UpdateManager,
    private val excelFiles: ExcelFiles,
    private val clock: () -> LocalDate = LocalDate::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private val today = MutableStateFlow(clock())

    /** Null until loaded, so the first frame never shows a wrong state. */
    val data: StateFlow<TrainingData?> = combine(repository.sets, today) { sets, today ->
        val days = Stats.days(sets)
        val weeks = Stats.weeks(days)
        TrainingData(days, weeks, Stats.compute(days, weeks, today), today)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val accent: StateFlow<AccentColor?> = settings.accentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val updateState: StateFlow<UpdateState> = updates.state
    val updatesEnabled: Boolean get() = updates.isEnabled

    var form by mutableStateOf(EntryForm(date = clock()))
        private set

    /** Temporary button text after saving ("✓ Hinzugefügt"). */
    var saveFeedback by mutableStateOf<String?>(null)
        private set
    private var saveFeedbackJob: Job? = null

    var importPreview by mutableStateOf<ImportPreview?>(null)
        private set

    var checkingForUpdates by mutableStateOf(false)
        private set

    var showInstallPermissionDialog by mutableStateOf(false)
        private set
    private var installAfterPermission = false

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val _updateFound = Channel<Unit>(Channel.CONFLATED)

    /** "Nach Updates suchen" found a new version — the UI scrolls up to the update notice. */
    val updateFound: Flow<Unit> = _updateFound.receiveAsFlow()

    init {
        viewModelScope.launch {
            val previous = settings.swapLastRunVersionCode(BuildConfig.VERSION_CODE)
            if (previous != null && previous < BuildConfig.VERSION_CODE) {
                show("Aktualisiert auf Version ${BuildConfig.VERSION_NAME} ✓")
            }
            updates.cleanUpDownloads()
            updates.check() // silent; the banner appears when there is an update
        }
    }

    fun onResume() {
        today.value = clock()
        if (installAfterPermission) {
            installAfterPermission = false
            if (updates.canInstallPackages()) updates.downloadAndInstall()
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    fun setAccent(color: AccentColor) {
        viewModelScope.launch { settings.setAccentColor(color) }
    }

    // ── Entry form ───────────────────────────────────────────────────────────

    fun onDateChange(date: LocalDate) {
        form = form.copy(date = date)
    }

    fun onRepsChange(text: String) {
        form = form.copy(reps = text.filter(Char::isDigit).take(3))
    }

    fun onNoteChange(text: String) {
        form = form.copy(note = text.replace('\n', ' ').take(60))
    }

    /** The −/+ buttons: 1..999 like the prototype. */
    fun stepReps(delta: Int) {
        val current = form.reps.toIntOrNull() ?: 0
        form = form.copy(reps = (current + delta).coerceIn(1, 999).toString())
    }

    fun startEdit(day: TrainingDay) {
        form = EntryForm(date = day.date, reps = day.total.toString(), note = day.notes.lastOrNull().orEmpty(), editing = day.date)
    }

    fun cancelEdit() {
        form = EntryForm(date = clock())
    }

    fun submit() {
        val submitted = form
        val reps = submitted.reps.toIntOrNull()?.takeIf { it >= 1 } ?: return
        form = EntryForm(date = clock())
        viewModelScope.launch {
            if (submitted.editing != null) {
                repository.replaceDay(submitted.editing, submitted.date, reps, submitted.note)
            } else {
                repository.addSet(submitted.date, reps, submitted.note)
            }
            showSaveFeedback(if (submitted.editing != null) "✓ Gespeichert" else "✓ Hinzugefügt")
        }
    }

    private fun showSaveFeedback(text: String) {
        saveFeedbackJob?.cancel()
        saveFeedback = text
        saveFeedbackJob = viewModelScope.launch {
            delay(1_500)
            saveFeedback = null
        }
    }

    fun deleteDay(date: LocalDate) {
        if (form.editing == date) cancelEdit()
        viewModelScope.launch {
            val removed = repository.deleteDay(date)
            if (removed.isNotEmpty()) {
                show("${GermanFormat.dayLabel(date)} gelöscht") { repository.restore(removed) }
            }
        }
    }

    fun undo(message: UiMessage) {
        val undo = message.undo ?: return
        viewModelScope.launch { undo() }
    }

    // ── Excel ────────────────────────────────────────────────────────────────

    fun exportFileName(): String = ExcelExport.fileName(clock())

    fun export(uri: Uri) {
        viewModelScope.launch {
            try {
                val days = Stats.days(repository.allSets())
                excelFiles.write(uri, ExcelExport.sheets(days, Stats.weeks(days), zone()))
                show("Excel-Datei gespeichert.")
            } catch (e: Exception) {
                show("Export fehlgeschlagen.")
            }
        }
    }

    fun onImportFile(uri: Uri) {
        viewModelScope.launch {
            val sets = try {
                excelFiles.read(uri, zone())
            } catch (e: ImportException) {
                show(e.message.orEmpty())
                return@launch
            } catch (e: Exception) {
                show("Die Datei konnte nicht gelesen werden.")
                return@launch
            }
            val existing = repository.allSets().map { it.date }.toSet()
            val dates = sets.map { it.date }.toSet()
            val preview = ImportPreview(sets, dates.size, sets.sumOf { it.reps }, dates.count { it in existing })
            // Only ask when the file overlaps with existing days; otherwise nothing can be lost.
            if (preview.existingDays == 0) runImport(preview, ImportMode.ADD_MISSING_DAYS) else importPreview = preview
        }
    }

    fun confirmImport(mode: ImportMode) {
        val preview = importPreview ?: return
        importPreview = null
        viewModelScope.launch { runImport(preview, mode) }
    }

    fun dismissImport() {
        importPreview = null
    }

    private suspend fun runImport(preview: ImportPreview, mode: ImportMode) {
        val outcome = repository.import(preview.sets, mode)
        val text = if (outcome.importedDays == 0) {
            "Keine neuen Tage in der Datei."
        } else {
            "${GermanFormat.days(outcome.importedDays)} importiert."
        }
        val previous = outcome.previous?.takeIf { it.isNotEmpty() }
        show(text, undo = previous?.let { { repository.replaceAll(it) } })
    }

    // ── Updates ──────────────────────────────────────────────────────────────

    fun checkForUpdates() {
        if (checkingForUpdates) return
        checkingForUpdates = true
        viewModelScope.launch {
            when (val result = updates.check()) {
                UpdateCheckResult.UpToDate -> show("Du hast die neueste Version (${BuildConfig.VERSION_NAME}).")
                is UpdateCheckResult.Error -> show(result.message)
                is UpdateCheckResult.Available -> _updateFound.trySend(Unit)
            }
            checkingForUpdates = false
        }
    }

    fun startUpdate() {
        if (updates.canInstallPackages()) {
            updates.downloadAndInstall()
        } else {
            showInstallPermissionDialog = true
        }
    }

    /** The user goes to the system settings; the update continues when they come back. */
    fun installPermissionSettingsIntent(): Intent {
        showInstallPermissionDialog = false
        installAfterPermission = true
        return updates.installPermissionSettingsIntent()
    }

    fun dismissInstallPermissionDialog() {
        showInstallPermissionDialog = false
    }

    fun dismissUpdate() = updates.dismiss()

    fun onUpdateConfirmationShown() = updates.onConfirmationShown()

    private fun show(text: String, undo: (suspend () -> Unit)? = null) {
        _messages.trySend(UiMessage(text, undo))
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TrainingTrackerApp).container
                MainViewModel(
                    repository = container.trainingRepository,
                    settings = container.settingsRepository,
                    updates = container.updateManager,
                    excelFiles = container.excelFiles,
                )
            }
        }
    }
}
