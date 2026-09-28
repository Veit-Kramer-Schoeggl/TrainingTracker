package io.github.veitkramerschoeggl.trainingtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.veitkramerschoeggl.trainingtracker.domain.ChartRange
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.LocalAccent
import io.github.veitkramerschoeggl.trainingtracker.ui.theme.Palette
import kotlin.math.max
import kotlin.math.roundToInt

enum class ChartType { BAR, LINE }

/** "Verlauf" card: bar or line chart per day / per week, like the prototype's canvas. */
@Composable
fun HistoryChart(
    values: List<Int>,
    perWeek: Boolean,
    type: ChartType,
    range: ChartRange,
    onTypeChange: (ChartType) -> Unit,
    onRangeChange: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    Column(modifier.card(18.dp).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp)) {
        LabelWithControls(
            label = { SectionLabel(if (perWeek) "Verlauf pro Woche" else "Verlauf pro Tag") },
            controls = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChartButton("Balken", type == ChartType.BAR) { onTypeChange(ChartType.BAR) }
                    ChartButton("Linie", type == ChartType.LINE) { onTypeChange(ChartType.LINE) }
                    ChartButton("Alle", range == ChartRange.ALL) { onRangeChange(ChartRange.ALL) }
                    ChartButton("3M", range == ChartRange.THREE_MONTHS) { onRangeChange(ChartRange.THREE_MONTHS) }
                    ChartButton("1M", range == ChartRange.ONE_MONTH) { onRangeChange(ChartRange.ONE_MONTH) }
                    ChartButton("2W", range == ChartRange.TWO_WEEKS) { onRangeChange(ChartRange.TWO_WEEKS) }
                }
            },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            if (type == ChartType.BAR) drawBars(values, accent.main) else drawLine(values, accent.main)
        }
    }
}

/**
 * Label left, buttons right. On narrow phones where the buttons leave no room for the label's
 * longest word, the buttons move below the label instead of overflowing the card.
 */
@Composable
private fun LabelWithControls(
    label: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(label, controls), modifier = modifier) { (labelMeasurables, controlMeasurables), constraints ->
        val gap = 8.dp.roundToPx()
        val width = constraints.maxWidth
        val controlsPlaceable = controlMeasurables.first().measure(Constraints(maxWidth = width))
        val labelMeasurable = labelMeasurables.first()
        val sideBySide = labelMeasurable.minIntrinsicWidth(Constraints.Infinity) + gap + controlsPlaceable.width <= width
        if (sideBySide) {
            val labelPlaceable = labelMeasurable.measure(Constraints(maxWidth = width - gap - controlsPlaceable.width))
            val height = max(labelPlaceable.height, controlsPlaceable.height)
            layout(width, height) {
                labelPlaceable.place(0, (height - labelPlaceable.height) / 2)
                controlsPlaceable.place(width - controlsPlaceable.width, (height - controlsPlaceable.height) / 2)
            }
        } else {
            val labelPlaceable = labelMeasurable.measure(Constraints(maxWidth = width))
            layout(width, labelPlaceable.height + gap + controlsPlaceable.height) {
                labelPlaceable.place(0, 0)
                controlsPlaceable.place(width - controlsPlaceable.width, labelPlaceable.height + gap)
            }
        }
    }
}

@Composable
private fun ChartButton(text: String, active: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Text(
        text,
        modifier = Modifier
            .card(7.dp, border = if (active) accent.main else Palette.Border, background = if (active) accent.main else Palette.Background)
            .plainClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (active) Palette.Background else Palette.Muted,
    )
}

private val chartPadding = 4.dp

private fun DrawScope.drawBars(values: List<Int>, accent: Color) {
    if (values.isEmpty()) return
    val maxValue = max(values.max(), 1)
    val pad = chartPadding.toPx()
    val height = size.height
    val columnWidth = (size.width - pad * 2) / values.size
    values.forEachIndexed { i, value ->
        val barHeight = max(3.dp.toPx(), (value.toFloat() / maxValue * (height - 8.dp.toPx())).roundToInt().toFloat())
        drawRoundRect(
            color = if (value == maxValue) accent else Palette.BarInactive,
            topLeft = Offset(pad + i * columnWidth + columnWidth * 0.15f, height - barHeight),
            size = Size(columnWidth * 0.7f, barHeight),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }
}

private fun DrawScope.drawLine(values: List<Int>, accent: Color) {
    if (values.isEmpty()) return
    val maxValue = max(values.max(), 1)
    val pad = chartPadding.toPx()
    val baseline = size.height - 4.dp.toPx()
    val usable = size.height - 12.dp.toPx()
    val columnWidth = (size.width - pad * 2) / values.size
    val points = values.mapIndexed { i, value ->
        Offset(pad + i * columnWidth + columnWidth / 2, baseline - value.toFloat() / maxValue * usable)
    }
    val line = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(line, accent.copy(alpha = 0x88 / 255f), style = Stroke(width = 2.dp.toPx()))
    val area = Path().apply {
        addPath(line)
        lineTo(points.last().x, baseline)
        lineTo(points.first().x, baseline)
        close()
    }
    drawPath(area, accent.copy(alpha = 0x22 / 255f))
    points.forEachIndexed { i, point ->
        val isMax = values[i] == maxValue
        drawCircle(
            color = if (isMax) accent else accent.copy(alpha = 0x99 / 255f),
            radius = (if (isMax) 4f else 2.5f).dp.toPx(),
            center = point,
        )
    }
}
