package io.github.veitkramerschoeggl.trainingtracker.excel

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and writes Excel files the user picked through the system file dialog. */
class ExcelFiles(private val resolver: ContentResolver) {

    suspend fun write(uri: Uri, sheets: List<XlsxSheet>) = withContext(Dispatchers.IO) {
        val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("Datei kann nicht geschrieben werden")
        out.use { XlsxWriter.write(sheets, it) }
    }

    suspend fun read(uri: Uri, zone: ZoneId): List<ImportedSet> = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri) ?: throw ImportException("Die Datei konnte nicht geöffnet werden.")
        input.use { ExcelImport.parse(it, zone) }
    }
}
