package io.github.veitkramerschoeggl.trainingtracker.excel

import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** A set read from an Excel file. [createdAt] is null when the file does not contain it. */
data class ImportedSet(val date: LocalDate, val reps: Int, val note: String, val createdAt: Long?)

class ImportException(message: String) : Exception(message)

/**
 * Reads training data from .xlsx files exported by this app or by the HTML prototype:
 * - sheet "Sätze" (every single set, written by this app) — used when present, lossless;
 * - otherwise sheet "Tage" with the columns Datum / Klimmzüge / Sätze ("10+5+8") / Notiz.
 * Summary rows ("Gesamt", …) and rows without a valid date are skipped.
 */
object ExcelImport {
    private val excelEpoch: LocalDate = LocalDate.of(1899, 12, 30)
    private val isoDate = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val germanDate = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""")

    fun parse(input: InputStream, zone: ZoneId): List<ImportedSet> {
        val sheets = try {
            XlsxReader.read(input)
        } catch (e: Exception) {
            throw ImportException("Die Datei konnte nicht gelesen werden. Ist es eine Excel-Datei (.xlsx)?")
        }
        sheets.firstOrNull { it.name.equals("Sätze", ignoreCase = true) }
            ?.let { parseSetsSheet(it, zone) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        for (sheet in sheets.sortedByDescending { it.name.equals("Tage", ignoreCase = true) }) {
            val sets = parseDaysSheet(sheet)
            if (sets.isNotEmpty()) return sets
        }
        throw ImportException("Keine Trainingsdaten gefunden. Erwartet wird eine Tabelle mit den Spalten „Datum“ und „Klimmzüge“.")
    }

    private class Header(val rowIndex: Int, private val columns: Map<String, Int>) {
        fun column(vararg names: String): Int? = names.firstNotNullOfOrNull { columns[it] }
    }

    private fun findHeader(sheet: XlsxReadSheet): Header? {
        sheet.rows.take(10).forEachIndexed { index, row ->
            val columns = row.withIndex()
                .mapNotNull { (i, cell) -> (cell as? String)?.trim()?.lowercase()?.let { it to i } }
                .toMap()
            if ("datum" in columns) return Header(index, columns)
        }
        return null
    }

    private fun parseDaysSheet(sheet: XlsxReadSheet): List<ImportedSet> {
        val header = findHeader(sheet) ?: return emptyList()
        val dateCol = header.column("datum") ?: return emptyList()
        val totalCol = header.column("klimmzüge", "wiederholungen", "reps") ?: return emptyList()
        val setsCol = header.column("sätze")
        val noteCol = header.column("notiz")
        return sheet.rows.drop(header.rowIndex + 1).flatMap { row ->
            val date = parseDate(row.getOrNull(dateCol)) ?: return@flatMap emptyList()
            val total = parseCount(row.getOrNull(totalCol))?.takeIf { it > 0 } ?: return@flatMap emptyList()
            val note = text(row, noteCol)
            parseSetList(setsCol?.let(row::getOrNull), total).mapIndexed { i, reps ->
                ImportedSet(date, reps, if (i == 0) note else "", createdAt = null)
            }
        }
    }

    private fun parseSetsSheet(sheet: XlsxReadSheet, zone: ZoneId): List<ImportedSet> {
        val header = findHeader(sheet) ?: return emptyList()
        val dateCol = header.column("datum") ?: return emptyList()
        val repsCol = header.column("klimmzüge", "wiederholungen", "reps") ?: return emptyList()
        val noteCol = header.column("notiz")
        val recordedCol = header.column("erfasst")
        return sheet.rows.drop(header.rowIndex + 1).mapNotNull { row ->
            val date = parseDate(row.getOrNull(dateCol)) ?: return@mapNotNull null
            val reps = parseCount(row.getOrNull(repsCol))?.takeIf { it > 0 } ?: return@mapNotNull null
            val recorded = recordedCol?.let { parseTimestamp(row.getOrNull(it), zone) }
            ImportedSet(date, reps, text(row, noteCol), recorded)
        }
    }

    internal fun parseDate(cell: Any?): LocalDate? {
        val date = when (cell) {
            is String -> isoDate.find(cell)?.destructured?.let { (y, m, d) -> dateOrNull(y, m, d) }
                ?: germanDate.find(cell)?.destructured?.let { (d, m, y) -> dateOrNull(y, m, d) }
            // Dates typed in Excel are stored as serial numbers (days since 1899-12-30).
            is Double -> if (cell in 1.0..2_958_465.0) excelEpoch.plusDays(cell.toLong()) else null
            else -> null
        }
        return date?.takeIf { it.year in 2000..2100 }
    }

    private fun dateOrNull(year: String, month: String, day: String): LocalDate? =
        runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()

    internal fun parseCount(cell: Any?): Int? = when (cell) {
        is Double -> cell.takeIf { it in 0.0..100_000.0 }?.roundToInt()
        is String -> cell.trim().toIntOrNull()
        else -> null
    }

    /** "10+5+8" -> [10, 5, 8]. Falls back to a single set when it does not add up to [total]. */
    internal fun parseSetList(cell: Any?, total: Int): List<Int> {
        val parts = (cell as? String)?.split('+')?.map { it.trim().toIntOrNull() }
        if (parts != null && parts.size > 1 && parts.all { it != null && it > 0 }) {
            val reps = parts.map { it!! }
            if (reps.sum() == total) return reps
        }
        return listOf(total)
    }

    private fun parseTimestamp(cell: Any?, zone: ZoneId): Long? {
        val text = (cell as? String)?.trim()?.replace(' ', 'T') ?: return null
        return runCatching { LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun text(row: List<Any?>, col: Int?): String = when (val cell = col?.let(row::getOrNull)) {
        is String -> cell.trim()
        is Double -> if (cell % 1.0 == 0.0) cell.toLong().toString() else cell.toString()
        is Boolean -> cell.toString()
        else -> ""
    }
}
