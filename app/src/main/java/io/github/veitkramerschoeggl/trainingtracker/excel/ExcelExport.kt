package io.github.veitkramerschoeggl.trainingtracker.excel

import io.github.veitkramerschoeggl.trainingtracker.domain.GermanFormat
import io.github.veitkramerschoeggl.trainingtracker.domain.Stats
import io.github.veitkramerschoeggl.trainingtracker.domain.TrainingDay
import io.github.veitkramerschoeggl.trainingtracker.domain.TrainingWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Excel export with the prototype's sheets "Tage" and "Wochen", plus a sheet "Sätze" with every
 * single set. The "Sätze" sheet makes the file a complete backup that [ExcelImport] can restore.
 */
object ExcelExport {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun fileName(today: LocalDate) = "klimmzug-tracker-$today.xlsx"

    fun sheets(days: List<TrainingDay>, weeks: List<TrainingWeek>, zone: ZoneId): List<XlsxSheet> {
        val total = days.sumOf { it.total }

        val dayRows = buildList<List<Any?>> {
            add(listOf("Datum", "Wochentag", "Klimmzüge", "Sätze", "Notiz"))
            for (day in days) {
                add(
                    listOf(
                        day.date.toString(),
                        GermanFormat.weekdayLong(day.date),
                        day.total,
                        if (day.sets.size > 1) day.sets.joinToString("+") { it.reps.toString() } else "–",
                        day.notes.joinToString(" / "),
                    ),
                )
            }
            add(emptyList())
            add(listOf("Gesamt", null, total))
            add(listOf("Bestleistung", null, days.maxOfOrNull { it.total } ?: 0))
            add(listOf("Ø pro Tag", null, Stats.average(total, days.size)))
        }

        val weekRows = buildList<List<Any?>> {
            add(listOf("Woche", "Zeitraum", "Klimmzüge", "Trainingstage", "Ø / Tag"))
            for (week in weeks) {
                add(
                    listOf(
                        GermanFormat.weekTitle(week.week),
                        GermanFormat.weekRange(week.week),
                        week.total,
                        week.days.size,
                        Stats.average(week.total, week.days.size),
                    ),
                )
            }
            add(emptyList())
            add(listOf("Ø pro Woche", null, Stats.average(weeks.sumOf { it.total }, weeks.size)))
        }

        val setRows = buildList<List<Any?>> {
            add(listOf("Datum", "Satz", "Klimmzüge", "Notiz", "Erfasst"))
            for (day in days) {
                day.sets.forEachIndexed { index, set ->
                    val recorded = timestampFormat.format(Instant.ofEpochMilli(set.createdAt).atZone(zone))
                    add(listOf(day.date.toString(), index + 1, set.reps, set.note, recorded))
                }
            }
        }

        return listOf(
            XlsxSheet("Tage", listOf(14.0, 14.0, 14.0, 18.0, 30.0), dayRows),
            XlsxSheet("Wochen", listOf(12.0, 22.0, 14.0, 16.0, 10.0), weekRows),
            XlsxSheet("Sätze", listOf(14.0, 8.0, 12.0, 30.0, 20.0), setRows),
        )
    }
}
