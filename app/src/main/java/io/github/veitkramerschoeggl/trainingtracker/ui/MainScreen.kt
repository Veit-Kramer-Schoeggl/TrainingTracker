package io.github.veitkramerschoeggl.trainingtracker.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.veitkramerschoeggl.trainingtracker.BuildConfig
import io.github.veitkramerschoeggl.trainingtracker.data.AccentColor
import io.github.veitkramerschoeggl.trainingtracker.domain.ChartRange
import io.github.veitkramerschoeggl.trainingtracker.domain.Stats
import io.github.veitkramerschoeggl.trainingtracker.excel.ExcelExport
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.LocalAccent
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.Palette
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.TrainingTrackerTheme
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.palette
import io.github.veitkramerschoeggl.trainingtracker.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun MainRoute(viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val accent by viewModel.accent.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    // Render only once settings and data are loaded (the window background is already dark).
    val accentColor = accent ?: return
    val trainingData = data ?: return
    TrainingTrackerTheme(accentColor) {
        MainScreen(viewModel, trainingData, accentColor)
    }
}

@Composable
private fun MainScreen(viewModel: MainViewModel, data: TrainingData, accentColor: AccentColor) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(HistoryTab.DAYS) }
    var statsExpanded by rememberSaveable { mutableStateOf(false) }
    var chartType by rememberSaveable { mutableStateOf(ChartType.BAR) }
    var chartRange by rememberSaveable { mutableStateOf(ChartRange.ALL) }
    var closedWeeks by rememberSaveable { mutableStateOf(listOf<String>()) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelExport.MIME_TYPE)) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImportFile(uri)
    }
    val startImport = { importLauncher.launch(arrayOf(ExcelExport.MIME_TYPE, "application/octet-stream")) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = if (message.undo != null) "Rückgängig" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(message)
        }
    }

    // The footer's update check found a version: jump to the top, where the notice appears.
    LaunchedEffect(viewModel) {
        viewModel.updateFound.collect { listState.animateScrollToItem(0) }
    }

    // Android asks the user to confirm the installation; launch its dialog while we are visible.
    LaunchedEffect(updateState) {
        val state = updateState
        if (state is UpdateState.ConfirmationRequired) {
            try {
                context.startActivity(state.intent)
            } catch (_: ActivityNotFoundException) {
            }
            viewModel.onUpdateConfirmationShown()
        }
    }

    val hasData = data.days.isNotEmpty()
    val showUpdate = updateState != UpdateState.Idle
    val form = viewModel.form
    val existingTotal = data.days.firstOrNull { it.date == form.date }?.total
        ?.takeIf { form.editing == null || form.editing != form.date }
    val chartValues = remember(data, tab, chartRange) {
        Stats.chartValues(data.days, data.weeks, perWeek = tab == HistoryTab.WEEKS, range = chartRange, today = data.today)
    }
    // header, update notice, statistics
    val entryIndex = 1 + (if (showUpdate) 1 else 0) + (if (hasData) 1 else 0)

    Scaffold(
        containerColor = Palette.Background,
        // Opaque status bar area, so scrolled content does not run under the clock and icons.
        topBar = {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Palette.Background))
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 24.dp,
                bottom = padding.calculateBottomPadding() + 60.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") {
                Header(accentColor, onSelect = viewModel::setAccent, modifier = Modifier.contentWidth())
            }
            if (showUpdate) {
                item(key = "update") {
                    UpdateBanner(
                        state = updateState,
                        onUpdate = viewModel::startUpdate,
                        onDismiss = viewModel::dismissUpdate,
                        modifier = Modifier.contentWidth().padding(bottom = 20.dp),
                    )
                }
            }
            if (hasData) {
                item(key = "stats") {
                    StatsSection(data.stats, statsExpanded, onToggle = { statsExpanded = !statsExpanded }, modifier = Modifier.contentWidth())
                }
            }
            item(key = "entry") {
                EntryCard(
                    form = form,
                    existingTotal = existingTotal,
                    saveFeedback = viewModel.saveFeedback,
                    onDateChange = viewModel::onDateChange,
                    onRepsChange = viewModel::onRepsChange,
                    onNoteChange = viewModel::onNoteChange,
                    onStep = viewModel::stepReps,
                    onSubmit = viewModel::submit,
                    onCancelEdit = viewModel::cancelEdit,
                    modifier = Modifier.contentWidth().padding(bottom = 24.dp),
                )
            }
            if (hasData) {
                item(key = "export") {
                    OutlineButton(
                        icon = "📊",
                        text = "Als Excel exportieren (.xlsx)",
                        onClick = { exportLauncher.launch(viewModel.exportFileName()) },
                        modifier = Modifier.contentWidth().padding(bottom = 24.dp),
                    )
                }
                item(key = "tabs") {
                    Tabs(tab, onSelect = { tab = it }, modifier = Modifier.contentWidth().padding(bottom = 20.dp))
                }
            }
            if (data.days.size >= 2) {
                item(key = "chart") {
                    HistoryChart(
                        values = chartValues,
                        perWeek = tab == HistoryTab.WEEKS,
                        type = chartType,
                        range = chartRange,
                        onTypeChange = { chartType = it },
                        onRangeChange = { chartRange = it },
                        modifier = Modifier.contentWidth().padding(bottom = 24.dp),
                    )
                }
            }
            when {
                !hasData -> item(key = "empty") { EmptyState(onImport = startImport, modifier = Modifier.contentWidth()) }
                tab == HistoryTab.DAYS -> {
                    item(key = "days-label") { SectionLabel("Tage", Modifier.contentWidth().padding(bottom = 12.dp)) }
                    val newest = data.days.last().date
                    items(data.days.asReversed(), key = { "day-${it.date}" }) { day ->
                        DayItem(
                            day = day,
                            isNewest = day.date == newest,
                            isToday = day.date == data.today,
                            isBest = day.total == data.stats.best,
                            onEdit = {
                                viewModel.startEdit(day)
                                scope.launch { listState.animateScrollToItem(entryIndex) }
                            },
                            onDelete = { viewModel.deleteDay(day.date) },
                            modifier = Modifier.animateItem().contentWidth().padding(bottom = 8.dp),
                        )
                    }
                }
                else -> items(data.weeks.asReversed(), key = { "week-${it.week.key}" }) { week ->
                    val key = week.week.key
                    WeekBlock(
                        week = week,
                        isBest = week.week == data.stats.bestWeek?.week,
                        expanded = key !in closedWeeks,
                        onToggle = { closedWeeks = if (key in closedWeeks) closedWeeks - key else closedWeeks + key },
                        modifier = Modifier.animateItem().contentWidth().padding(bottom = 12.dp),
                    )
                }
            }
            item(key = "footer") {
                Footer(
                    showImport = hasData,
                    updatesEnabled = viewModel.updatesEnabled,
                    checking = viewModel.checkingForUpdates,
                    onImport = startImport,
                    onCheckUpdates = viewModel::checkForUpdates,
                    modifier = Modifier.contentWidth(),
                )
            }
        }
    }

    viewModel.importPreview?.let { preview ->
        ImportDialog(preview, onConfirm = viewModel::confirmImport, onDismiss = viewModel::dismissImport)
    }
    if (viewModel.showInstallPermissionDialog) {
        InstallPermissionDialog(
            onOpenSettings = { context.startActivity(viewModel.installPermissionSettingsIntent()) },
            onDismiss = viewModel::dismissInstallPermissionDialog,
        )
    }
}

@Composable
private fun Header(selected: AccentColor, onSelect: (AccentColor) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pull-ups", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Palette.White)
            Row {
                for (color in AccentColor.entries) {
                    ColorDot(color, selected = color == selected, onClick = { onSelect(color) })
                }
            }
        }
        Text("KLIMMZUG TRACKER", modifier = Modifier.padding(bottom = 6.dp), fontSize = 11.sp, letterSpacing = 3.sp, color = Palette.Muted)
        Text("Pull-Hard by Bernhard", fontSize = 12.sp, letterSpacing = 2.sp, color = Palette.Muted)
    }
}

@Composable
private fun ColorDot(color: AccentColor, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) Palette.White else Color.Transparent, label = "ring")
    val name = when (color) {
        AccentColor.GREEN -> "Grün"
        AccentColor.VIOLET -> "Violett"
        AccentColor.BLUE -> "Blau"
    }
    // 24dp touch box around the 18dp dot keeps the prototype's 6dp spacing.
    Box(
        modifier = Modifier
            .size(24.dp)
            .plainClickable(onClick = onClick)
            .semantics {
                contentDescription = "Farbe $name"
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color.palette().main)
                .border(2.dp, ring, CircleShape),
        )
    }
}

@Composable
private fun Tabs(selected: HistoryTab, onSelect: (HistoryTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TabButton("Tage", selected == HistoryTab.DAYS, { onSelect(HistoryTab.DAYS) }, Modifier.weight(1f))
        TabButton("Wochen", selected == HistoryTab.WEEKS, { onSelect(HistoryTab.WEEKS) }, Modifier.weight(1f))
    }
}

@Composable
private fun TabButton(text: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val background by animateColorAsState(if (active) accent.main else Palette.Card, label = "tabBg")
    val border by animateColorAsState(if (active) accent.main else Palette.Border, label = "tabBorder")
    val content by animateColorAsState(if (active) Palette.Background else Palette.Muted, label = "tabFg")
    Box(
        modifier = modifier
            .card(10.dp, border = border, background = background)
            .plainClickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = content)
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🏋️", fontSize = 40.sp)
        Text(
            "Noch kein Training eingetragen.",
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
            fontSize = 14.sp,
            color = Palette.Faint,
            textAlign = TextAlign.Center,
        )
        OutlineButton(icon = "📥", text = "Daten aus Excel importieren", onClick = onImport)
    }
}

@Composable
private fun Footer(
    showImport: Boolean,
    updatesEnabled: Boolean,
    checking: Boolean,
    onImport: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (showImport) {
            FooterLink("Daten aus Excel importieren", onImport, Modifier.padding(bottom = 10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 11.sp, color = Palette.Faint)
            if (updatesEnabled) {
                Text("  ·  ", fontSize = 11.sp, color = Palette.Faint)
                FooterLink(if (checking) "Suche nach Updates …" else "Nach Updates suchen", onCheckUpdates)
            }
        }
    }
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .plainClickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        fontSize = 12.sp,
        color = Palette.Muted,
        textDecoration = TextDecoration.Underline,
    )
}
