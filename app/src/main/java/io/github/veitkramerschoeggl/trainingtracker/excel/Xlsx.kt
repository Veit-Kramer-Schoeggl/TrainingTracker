package io.github.veitkramerschoeggl.trainingtracker.excel

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * A sheet to write: cells are [String], [Number] or null. Widths are in characters.
 * The first row is written bold (header).
 */
class XlsxSheet(val name: String, val columnWidths: List<Double>, val rows: List<List<Any?>>)

/** A sheet as read back: cells are [String], [Double], [Boolean] or null. */
class XlsxReadSheet(val name: String, val rows: List<List<Any?>>)

/**
 * Minimal Office Open XML (.xlsx) writer — just what the export needs, no heavy library.
 * Strings are written inline, so no shared-string table is needed.
 */
object XlsxWriter {

    fun write(sheets: List<XlsxSheet>, out: OutputStream) {
        val zip = ZipOutputStream(out)
        fun entry(name: String, xml: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", contentTypes(sheets.size))
        entry("_rels/.rels", ROOT_RELS)
        entry("xl/workbook.xml", workbook(sheets))
        entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
        entry("xl/styles.xml", STYLES)
        sheets.forEachIndexed { i, sheet -> entry("xl/worksheets/sheet${i + 1}.xml", worksheet(sheet)) }
        zip.finish()
    }

    private fun contentTypes(sheetCount: Int) = buildString {
        append(XML_HEADER)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook(sheets: List<XlsxSheet>) = buildString {
        append(XML_HEADER)
        append("""<workbook xmlns="$NS_MAIN" xmlns:r="$NS_REL"><sheets>""")
        sheets.forEachIndexed { i, sheet ->
            append("""<sheet name="${escape(sheet.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int) = buildString {
        append(XML_HEADER)
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="$NS_REL/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rIdStyles" Type="$NS_REL/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun worksheet(sheet: XlsxSheet) = buildString {
        append(XML_HEADER)
        append("""<worksheet xmlns="$NS_MAIN">""")
        if (sheet.columnWidths.isNotEmpty()) {
            append("<cols>")
            sheet.columnWidths.forEachIndexed { i, width ->
                append("""<col min="${i + 1}" max="${i + 1}" width="${width + 0.83}" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        sheet.rows.forEachIndexed { rowIndex, row ->
            if (row.all { it == null || it == "" }) return@forEachIndexed
            val r = rowIndex + 1
            val style = if (rowIndex == 0) """ s="1"""" else ""
            append("""<row r="$r">""")
            row.forEachIndexed { colIndex, value ->
                val ref = columnName(colIndex) + r
                when (value) {
                    null, "" -> Unit
                    is Number -> append("""<c r="$ref"$style><v>$value</v></c>""")
                    else -> append(
                        """<c r="$ref" t="inlineStr"$style><is><t xml:space="preserve">${escape(value.toString())}</t></is></c>""",
                    )
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    /** 0 -> A, 25 -> Z, 26 -> AA */
    fun columnName(index: Int): String {
        var i = index + 1
        val sb = StringBuilder()
        while (i > 0) {
            val rem = (i - 1) % 26
            sb.append('A' + rem)
            i = (i - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun escape(text: String): String = buildString {
        for (ch in text) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                // Control characters are not allowed in XML 1.0.
                ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                else -> append(ch)
            }
        }
    }

    private const val XML_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val ROOT_RELS = XML_HEADER +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="$NS_REL/officeDocument" Target="xl/workbook.xml"/>""" +
        "</Relationships>"
    private const val STYLES = XML_HEADER +
        """<styleSheet xmlns="$NS_MAIN">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        "</styleSheet>"
}

/**
 * Minimal .xlsx reader for importing: reads cell values of all sheets (shared strings, inline
 * strings, numbers, booleans). Formatting and formulas are ignored — cached values are used.
 */
object XlsxReader {
    private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024

    fun read(input: InputStream): List<XlsxReadSheet> {
        val files = unzip(input)
        val workbook = files["xl/workbook.xml"] ?: throw IOException("Keine Excel-Arbeitsmappe (.xlsx)")
        val rels = files["xl/_rels/workbook.xml.rels"]?.let(::parseRelationships).orEmpty()
        val sharedStrings = files["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()

        return parseSheetRefs(workbook).mapNotNull { (name, relId) ->
            val target = rels[relId] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            val xml = files[path] ?: return@mapNotNull null
            XlsxReadSheet(name, parseSheet(xml, sharedStrings))
        }
    }

    private fun unzip(input: InputStream): Map<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.endsWith(".xml") && !entry.name.endsWith(".rels")) continue
                val bytes = zip.readAtMost(MAX_TOTAL_BYTES - total)
                total += bytes.size
                files[entry.name.removePrefix("/")] = bytes
            }
        }
        if (files.isEmpty()) throw IOException("Keine Excel-Datei (.xlsx)")
        return files
    }

    /** Reads the current entry, but refuses to unpack more than [limit] bytes (zip bombs). */
    private fun ZipInputStream.readAtMost(limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) throw IOException("Datei ist zu groß")
        }
        return out.toByteArray()
    }

    private fun parse(xml: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        for (feature in listOf(
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
        )) {
            runCatching { factory.setFeature(feature, false) }
        }
        factory.newSAXParser().parse(ByteArrayInputStream(xml), handler)
    }

    /** Sheet name to relationship id, in workbook order. */
    private fun parseSheetRefs(xml: ByteArray): List<Pair<String, String>> {
        val sheets = mutableListOf<Pair<String, String>>()
        parse(xml, object : DefaultHandler() {
            override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
                if (localName != "sheet") return
                val relId = (0 until attrs.length)
                    .firstOrNull { attrs.getLocalName(it) == "id" && attrs.getURI(it).isNotEmpty() }
                    ?.let(attrs::getValue) ?: return
                sheets += (attrs.getValue("", "name") ?: "") to relId
            }
        })
        return sheets
    }

    private fun parseRelationships(xml: ByteArray): Map<String, String> {
        val rels = HashMap<String, String>()
        parse(xml, object : DefaultHandler() {
            override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
                if (localName != "Relationship") return
                val id = attrs.getValue("", "Id") ?: return
                val target = attrs.getValue("", "Target") ?: return
                rels[id] = target
            }
        })
        return rels
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        parse(xml, object : DefaultHandler() {
            private val current = StringBuilder()
            private var inText = false
            private var phoneticDepth = 0

            override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
                when (localName) {
                    "si" -> current.setLength(0)
                    "rPh" -> phoneticDepth++
                    "t" -> inText = phoneticDepth == 0
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) current.appendRange(ch, start, start + length)
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                when (localName) {
                    "si" -> strings += current.toString()
                    "rPh" -> phoneticDepth--
                    "t" -> inText = false
                }
            }
        })
        return strings
    }

    private fun parseSheet(xml: ByteArray, sharedStrings: List<String>): List<List<Any?>> {
        val rows = sortedMapOf<Int, MutableMap<Int, Any?>>()
        parse(xml, object : DefaultHandler() {
            private var rowIndex = 0
            private var colIndex = 0
            private var type: String? = null
            private val value = StringBuilder()
            private var inValue = false
            private var inCell = false

            override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
                when (localName) {
                    "row" -> {
                        rowIndex = attrs.getValue("", "r")?.toIntOrNull() ?: (rowIndex + 1)
                        colIndex = -1
                    }
                    "c" -> {
                        inCell = true
                        type = attrs.getValue("", "t")
                        colIndex = attrs.getValue("", "r")?.let(::columnIndex) ?: (colIndex + 1)
                        value.setLength(0)
                    }
                    "v" -> inValue = inCell
                    "t" -> inValue = inCell && type == "inlineStr"
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue) value.appendRange(ch, start, start + length)
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                when (localName) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        inCell = false
                        val raw = value.toString()
                        val cell: Any? = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let(sharedStrings::getOrNull)
                            "str", "inlineStr", "e", "d" -> raw
                            "b" -> raw.trim() == "1"
                            else -> raw.trim().toDoubleOrNull()
                        }
                        if (cell != null) rows.getOrPut(rowIndex) { HashMap() }[colIndex] = cell
                    }
                }
            }
        })
        if (rows.isEmpty()) return emptyList()
        return (1..rows.lastKey()).map { r ->
            val cells = rows[r] ?: return@map emptyList()
            (0..cells.keys.max()).map { cells[it] }
        }
    }

    /** "C12" -> 2 */
    private fun columnIndex(ref: String): Int {
        var index = 0
        for (ch in ref) {
            if (ch !in 'A'..'Z') break
            index = index * 26 + (ch - 'A' + 1)
        }
        return index - 1
    }
}
