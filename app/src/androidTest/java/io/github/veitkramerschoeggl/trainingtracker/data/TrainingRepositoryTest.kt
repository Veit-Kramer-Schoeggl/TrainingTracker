package io.github.veitkramerschoeggl.trainingtracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.veitkramerschoeggl.trainingtracker.data.db.TrainingDatabase
import io.github.veitkramerschoeggl.trainingtracker.excel.ImportedSet
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingRepositoryTest {
    private lateinit var db: TrainingDatabase
    private lateinit var repository: TrainingRepository
    private var clock = 1_000L

    private val sep27 = LocalDate.of(2026, 9, 27)
    private val sep28 = LocalDate.of(2026, 9, 28)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .addCallback(TrainingDatabase.SeedExercises)
            .build()
        repository = TrainingRepository(db.setDao(), zone = { ZoneId.of("Europe/Vienna") }, now = { clock++ })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun repsByDay() = repository.allSets().groupBy({ it.date }, { it.reps })

    @Test
    fun addEditDeleteAndUndo() = runTest {
        repository.addSet(sep28, 10, "")
        repository.addSet(sep28, 8, "  Breiter Griff ")
        val sets = repository.sets.first()
        assertEquals(listOf(10, 8), sets.map { it.reps })
        assertEquals("Breiter Griff", sets[1].note)

        // Editing a day replaces its sets with one set holding the new total (as in the prototype).
        repository.replaceDay(sep28, sep28, 20, "neu")
        assertEquals(mapOf(sep28 to listOf(20)), repsByDay())

        // Moving the edited day onto a day with data adds to it instead of overwriting it.
        repository.addSet(sep27, 5, "")
        repository.replaceDay(sep28, sep27, 7, "")
        assertEquals(mapOf(sep27 to listOf(5, 7)), repsByDay())

        val removed = repository.deleteDay(sep27)
        assertTrue(repository.allSets().isEmpty())
        repository.restore(removed)
        assertEquals(mapOf(sep27 to listOf(5, 7)), repsByDay())
    }

    @Test
    fun importModes() = runTest {
        repository.addSet(sep27, 10, "")
        val file = listOf(
            ImportedSet(sep27, 3, "", createdAt = null),
            ImportedSet(sep28, 4, "erster", createdAt = null),
            ImportedSet(sep28, 5, "", createdAt = null),
        )

        val added = repository.import(file, ImportMode.ADD_MISSING_DAYS)
        assertEquals(1, added.importedDays)
        assertEquals(mapOf(sep27 to listOf(10), sep28 to listOf(4, 5)), repsByDay())

        val replaced = repository.import(file, ImportMode.REPLACE_ALL)
        assertEquals(2, replaced.importedDays)
        assertEquals(mapOf(sep27 to listOf(3), sep28 to listOf(4, 5)), repsByDay())

        // Undo of "replace all"
        repository.replaceAll(replaced.previous!!)
        assertEquals(mapOf(sep27 to listOf(10), sep28 to listOf(4, 5)), repsByDay())
    }

    @Test
    fun pullUpExerciseIsSeeded() {
        db.openHelper.readableDatabase.query("SELECT name FROM exercise WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Klimmzüge", cursor.getString(0))
        }
    }
}
