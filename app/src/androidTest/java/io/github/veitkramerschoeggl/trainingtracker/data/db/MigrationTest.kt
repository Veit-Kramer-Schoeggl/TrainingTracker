package io.github.veitkramerschoeggl.trainingtracker.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the user's data across app updates: a database created with every old schema version
 * (app/schemas/…/N.json) must open with the current app and keep its rows.
 * When adding a migration, extend [oldVersionsWithTheirData] with the new previous version.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TrainingDatabase::class.java)

    private val oldVersionsWithTheirData = listOf(1)

    @Test
    fun everySchemaVersionOpensWithTheCurrentAppAndKeepsItsData() {
        for (version in oldVersionsWithTheirData) {
            helper.createDatabase(testDb, version).apply {
                execSQL("INSERT INTO exercise (id, name) VALUES (1, 'Klimmzüge')")
                execSQL("INSERT INTO training_set (exercise_id, date, reps, note, created_at) VALUES (1, '2026-09-28', 12, 'alt', 1)")
                close()
            }
            val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), TrainingDatabase::class.java, testDb)
                .addMigrations(*TrainingDatabase.MIGRATIONS)
                .build()
            val sets = runBlocking { db.setDao().getAll(TrainingDatabase.PULLUPS_EXERCISE_ID) }
            db.close()
            assertEquals(listOf(12 to "alt"), sets.map { it.reps to it.note })
            ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(testDb)
        }
    }
}
