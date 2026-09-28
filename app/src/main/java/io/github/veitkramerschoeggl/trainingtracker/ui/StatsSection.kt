package io.github.veitkramerschoeggl.trainingtracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.veitkramerschoeggl.trainingtracker.domain.GermanFormat
import io.github.veitkramerschoeggl.trainingtracker.domain.MonthTotal
import io.github.veitkramerschoeggl.trainingtracker.domain.TrainingDay
import io.github.veitkramerschoeggl.trainingtracker.domain.TrainingStats
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.LocalAccent
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.Palette

/** Four pinned KPIs, "Mehr Statistiken" toggle and the expandable details. */
@Composable
fun StatsSection(stats: TrainingStats, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        StatRow(spacing = 8) {
            StatBox("Gesamt", GermanFormat.number(stats.total), accentBorder = true)
            StatBox("Bestleistung", stats.best.toString())
            StatBox("Ø / Woche", stats.weekAverage.toString())
            StatBox("Ø / Tag", stats.dayAverage.toString())
        }
        ToggleButton(expanded, onToggle, Modifier.padding(top = 10.dp, bottom = 10.dp))
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(350)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(350)) + fadeOut(tween(250)),
        ) {
            StatDetails(stats)
        }
        Box(Modifier.height(14.dp))
    }
}

@Composable
private fun StatDetails(stats: TrainingStats) {
    val streak = stats.longestStreak
    val streakRange = when {
        streak == null -> null
        streak.start == streak.end -> GermanFormat.dayMonth(streak.start)
        else -> "${GermanFormat.dayMonth(streak.start)}–${GermanFormat.dayMonth(streak.end)}"
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatRow {
            StatBox("Tage aktiv", stats.activeDays.toString())
            StatBox("Beste Woche", (stats.bestWeek?.total ?: 0).toString(), sub = stats.bestWeek?.let { GermanFormat.weekTitle(it.week) })
        }
        StatRow {
            StatBox("Längster Streak", GermanFormat.days(streak?.days ?: 0), sub = streakRange)
            StatBox("Reps im Streak", (streak?.reps ?: 0).toString(), sub = "im längsten Streak")
        }
        StatRow {
            StatBox("Längste Pause", GermanFormat.days(stats.longestPause), sub = "Tage inaktiv")
            StatBox("Ø letzte 10 Tage", stats.averageLast10Days.toString())
        }
        StatRow {
            StatBox("Ø letzte 4 Wochen", stats.averageLast4Weeks.toString())
            StatBox("Ø / Satz (30 Tage)", stats.averagePerSetLast30Days.toString())
        }
        MonthList(stats.months)
        TopDays(stats.top3)
    }
}

/** Grid row: equal widths and equal heights like CSS grid. */
@Composable
private fun StatRow(spacing: Int = 10, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(spacing.dp),
        content = content,
    )
}

@Composable
private fun RowScope.StatBox(label: String, value: String, sub: String? = null, accentBorder: Boolean = false) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .card(14.dp, border = if (accentBorder) accent.dim else Palette.Border)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 20.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, color = accent.main, textAlign = TextAlign.Center)
        Text(
            label,
            modifier = Modifier.padding(top = 3.dp),
            fontSize = 9.sp,
            letterSpacing = 0.4.sp,
            color = Palette.Muted,
            textAlign = TextAlign.Center,
        )
        if (!sub.isNullOrEmpty()) {
            Text(sub, modifier = Modifier.padding(top = 2.dp), fontSize = 9.sp, color = Palette.Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ToggleButton(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "arrow")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .card(12.dp)
            .plainClickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (expanded) "Weniger" else "Mehr Statistiken", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
        DropdownArrow(Palette.Muted, rotation)
    }
}

@Composable
private fun MonthList(months: List<MonthTotal>) {
    val accent = LocalAccent.current
    val max = months.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth().card(14.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
        BoxTitle("KLIMMZÜGE PRO MONAT")
        for (month in months) {
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(GermanFormat.month(month.month), fontSize = 13.sp, color = Palette.Soft)
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Palette.Border),
                ) {
                    val fraction = Math.round(month.total * 100f / max) / 100f
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(accent.dim))
                }
                Text(month.total.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent.main)
            }
        }
    }
}

@Composable
private fun TopDays(days: List<TrainingDay>) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth().card(14.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
        BoxTitle("TOP 3 TAGE")
        days.forEachIndexed { index, day ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    Text("${index + 1}.", modifier = Modifier.width(16.dp), fontSize = 13.sp, color = Palette.Muted)
                    Text(day.total.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent.main)
                }
                Text(GermanFormat.dayLabel(day.date), fontSize = 11.sp, color = Palette.Subtle)
            }
        }
    }
}

@Composable
private fun BoxTitle(text: String) {
    Text(text, modifier = Modifier.padding(bottom = 8.dp), fontSize = 10.sp, letterSpacing = 0.5.sp, color = Palette.Muted)
}
