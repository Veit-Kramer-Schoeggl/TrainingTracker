package io.github.veitkramerschoeggl.trainingtracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.veitkramerschoeggl.trainingtracker.domain.GermanFormat
import io.github.veitkramerschoeggl.trainingtracker.domain.TrainingDay
import io.github.veitkramerschoeggl.trainingtracker.domain.TrainingWeek
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.LocalAccent
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.Palette

enum class HistoryTab { DAYS, WEEKS }

/** One entry of the "Tage" list. */
@Composable
fun DayItem(
    day: TrainingDay,
    isNewest: Boolean,
    isToday: Boolean,
    isBest: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val border = when {
        isToday -> Palette.TodayBorder
        isNewest -> accent.dim
        else -> Palette.Border
    }
    Row(
        modifier = modifier
            .card(14.dp, border = border)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            day.total.toString(),
            modifier = Modifier.widthIn(min = 44.dp),
            fontSize = 28.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isBest) accent.main else Palette.White,
        )
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(GermanFormat.dayLabel(day.date), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.DayText)
                if (isToday) {
                    Text("heute", modifier = Modifier.padding(start = 8.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.TodayBadge)
                }
            }
            Text(GermanFormat.sets(day), modifier = Modifier.padding(top = 2.dp), fontSize = 11.sp, color = Palette.Muted)
            val notes = day.notes
            if (notes.isNotEmpty()) {
                Text(
                    notes.joinToString(" · "),
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 13.sp,
                    color = Palette.Soft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row {
            GlyphButton("✎", pressedColor = accent.main, description = "Tag bearbeiten", onClick = onEdit)
            GlyphButton("×", pressedColor = Palette.Danger, description = "Tag löschen", onClick = onDelete)
        }
    }
}

/** One collapsible week of the "Wochen" tab. */
@Composable
fun WeekBlock(week: TrainingWeek, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "weekArrow")
    Column(modifier.card(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.WeekHeader)
                .plainClickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(GermanFormat.weekLabel(week.week), modifier = Modifier.weight(1f), fontSize = 12.sp, color = Palette.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(week.total.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = accent.main)
                DropdownArrow(Palette.Faint, rotation, width = 9.dp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Border))
        if (expanded) {
            Column(Modifier.padding(vertical = 8.dp)) {
                for (day in week.days.asReversed()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            day.total.toString(),
                            modifier = Modifier.widthIn(min = 32.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Palette.White,
                        )
                        Text(GermanFormat.dayLabel(day.date), modifier = Modifier.weight(1f), fontSize = 12.sp, color = Palette.Subtle)
                        val notes = day.notes
                        if (notes.isNotEmpty()) {
                            Text(
                                notes.joinToString(" · "),
                                modifier = Modifier.widthIn(max = 120.dp),
                                fontSize = 11.sp,
                                color = Palette.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
