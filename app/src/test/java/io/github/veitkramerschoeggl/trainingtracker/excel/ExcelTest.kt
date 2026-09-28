package io.github.veitkramerschoeggl.trainingtracker.excel

import io.github.veitkramerschoeggl.trainingtracker.domain.Stats
import io.github.veitkramerschoeggl.trainingtracker.domain.TrainingSet
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ExcelTest {
    private val zone = ZoneId.of("Europe/Vienna")

    private fun set(date: String, reps: Int, note: String = "", time: String = "12:00") = TrainingSet(
        id = 0,
        date = LocalDate.parse(date),
        reps = reps,
        note = note,
        createdAt = LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli(),
    )

    private val sets = listOf(
        set("2026-09-22", 10, time = "07:10"),
        set("2026-09-22", 12, note = "Breiter Griff & <eng>", time = "07:25"),
        set("2026-09-24", 8, note = "Müde \"heute\""),
    )

    private fun export(sets: List<TrainingSet>): ByteArray {
        val days = Stats.days(sets)
        return ByteArrayOutputStream().also { XlsxWriter.write(ExcelExport.sheets(days, Stats.weeks(days), zone), it) }.toByteArray()
    }

    @Test
    fun exportHasThePrototypeSheets() {
        val sheets = XlsxReader.read(ByteArrayInputStream(export(sets)))
        assertEquals(listOf("Tage", "Wochen", "Sätze"), sheets.map { it.name })

        val tage = sheets[0].rows
        assertEquals(listOf("Datum", "Wochentag", "Klimmzüge", "Sätze", "Notiz"), tage[0])
        assertEquals(listOf("2026-09-22", "Dienstag", 22.0, "10+12", "Breiter Griff & <eng>"), tage[1])
        assertEquals(listOf("2026-09-24", "Donnerstag", 8.0, "–", "Müde \"heute\""), tage[2])
        assertEquals(emptyList<Any?>(), tage[3])
        assertEquals(listOf("Gesamt", null, 30.0), tage[4])
        assertEquals(listOf("Bestleistung", null, 22.0), tage[5])
        assertEquals(listOf("Ø pro Tag", null, 15.0), tage[6])

        val wochen = sheets[1].rows
        assertEquals(listOf("KW 39/2026", "21.09. – 27.09.", 30.0, 2.0, 15.0), wochen[1])
        assertEquals(listOf("Ø pro Woche", null, 30.0), wochen[3])
    }

    @Test
    fun exportImportRoundTripIsLossless() {
        val imported = ExcelImport.parse(ByteArrayInputStream(export(sets)), zone)
        assertEquals(sets.map { Triple(it.date, it.reps, it.note) }, imported.map { Triple(it.date, it.reps, it.note) })
        assertEquals(sets.map { it.createdAt }, imported.map { it.createdAt })
    }

    @Test
    fun importsTheDaySheetOfThePrototypeExport() {
        // Same structure as the prototype's SheetJS export: "str" cells, no shared strings.
        val sheet = """
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1" t="str"><v>Datum</v></c><c r="B1" t="str"><v>Wochentag</v></c><c r="C1" t="str"><v>Klimmzüge</v></c><c r="D1" t="str"><v>Sätze</v></c><c r="E1" t="str"><v>Notiz</v></c></row>
            <row r="2"><c r="A2" t="str"><v>2026-06-22</v></c><c r="B2" t="str"><v>Montag</v></c><c r="C2"><v>21</v></c><c r="D2" t="str"><v>–</v></c><c r="E2" t="str"><v></v></c></row>
            <row r="3"><c r="A3" t="str"><v>2026-06-28</v></c><c r="C3"><v>34</v></c><c r="D3" t="str"><v>19+15</v></c><c r="E3" t="str"><v>Gut</v></c></row>
            <row r="4"><c r="A4" t="str"><v>2026-06-29</v></c><c r="C4"><v>30</v></c><c r="D4" t="str"><v>10+10</v></c></row>
            <row r="5"><c r="A5"><v>46204</v></c><c r="C5"><v>12</v></c></row>
            <row r="6"><c r="A6" t="str"><v>03.07.2026</v></c><c r="C6"><v>7</v></c></row>
            <row r="8"><c r="A8" t="str"><v>Gesamt</v></c><c r="C8"><v>104</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()
        val imported = ExcelImport.parse(ByteArrayInputStream(workbook("Tage" to sheet)), zone)
        assertEquals(
            listOf(
                Triple("2026-06-22", 21, ""),
                Triple("2026-06-28", 19, "Gut"),
                Triple("2026-06-28", 15, ""),
                Triple("2026-06-29", 30, ""), // "10+10" does not add up to 30 -> one set with the total
                Triple("2026-07-01", 12, ""), // Excel serial date
                Triple("2026-07-03", 7, ""), // German date
            ),
            imported.map { Triple(it.date.toString(), it.reps, it.note) },
        )
        assertTrue(imported.all { it.createdAt == null })
    }

    @Test
    fun readsSharedStringsAndInlineStrings() {
        val sheet = """
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="inlineStr"><is><t>Klimmzüge</t></is></c></row>
            <row r="2"><c r="A2" t="s"><v>1</v></c><c r="B2"><v>9</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()
        val shared = """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>Datum</t></si><si><r><t>2026-</t></r><r><t>09-01</t></r></si></sst>"""
        val imported = ExcelImport.parse(ByteArrayInputStream(workbook("Tabelle1" to sheet, sharedStrings = shared)), zone)
        assertEquals(listOf(LocalDate.of(2026, 9, 1) to 9), imported.map { it.date to it.reps })
    }

    @Test(expected = ImportException::class)
    fun rejectsFilesWithoutTrainingData() {
        val sheet = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="str"><v>Hallo</v></c></row></sheetData></worksheet>"""
        ExcelImport.parse(ByteArrayInputStream(workbook("Tabelle1" to sheet)), zone)
    }

    @Test(expected = ImportException::class)
    fun rejectsNonExcelFiles() {
        ExcelImport.parse(ByteArrayInputStream("keine Excel-Datei".toByteArray()), zone)
    }

    /** The real export from the prototype, if it is in the project root (it is not committed). */
    @Test
    fun importsTheOriginalTrackerFileWhenPresent() {
        val file = File("../klimmzug-tracker-2026-09-26.xlsx")
        assumeTrue(file.exists())
        val imported = file.inputStream().use { ExcelImport.parse(it, zone) }
        assertEquals(1129, imported.sumOf { it.reps })
        assertEquals(57, imported.map { it.date }.distinct().size)
        assertEquals(listOf(10, 10, 10, 8, 8, 10), imported.filter { it.date == LocalDate.of(2026, 9, 24) }.map { it.reps })
    }

    private fun workbook(vararg sheets: Pair<String, String>, sharedStrings: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            val ns = """xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships""""
            entry(
                "xl/workbook.xml",
                "<workbook $ns><sheets>" +
                    sheets.mapIndexed { i, (name, _) -> """<sheet name="$name" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") +
                    "</sheets></workbook>",
            )
            entry(
                "xl/_rels/workbook.xml.rels",
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    sheets.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } +
                    "</Relationships>",
            )
            sheets.forEachIndexed { i, (_, xml) -> entry("xl/worksheets/sheet${i + 1}.xml", xml) }
            if (sharedStrings != null) entry("xl/sharedStrings.xml", sharedStrings)
        }
        return out.toByteArray()
    }
}
