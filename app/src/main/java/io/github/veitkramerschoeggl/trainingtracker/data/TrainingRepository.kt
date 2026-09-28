package io.github.veitkramerschoeggl.trainingtracker.data

import io.github.veitkramerschoeggl.trainingtracker.data.db.SetDao
import io.github.veitkramerschoeggl.trainingtracker.data.db.SetEntity
import io.github.veitkramerschoeggl.trainingtracker.data.db.TrainingDatabase.Companion.PULLUPS_EXERCISE_ID
import io.github.veitkramerschoeggl.trainingtracker.domain.TrainingSet
import io.github.veitkramerschoeggl.trainingtracker.excel.ImportedSet
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ImportMode {
    /** Keep existing days, only add days that have no data yet. */
    ADD_MISSING_DAYS,

    /** Delete everything and take the file's content. */
    REPLACE_ALL,
}

/** All reads and writes of training data. Currently always for the pull-up exercise. */
class TrainingRepository(
    private val dao: SetDao,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val exerciseId = PULLUPS_EXERCISE_ID

    val sets: Flow<List<TrainingSet>> = dao.observeAll(exerciseId).map { list -> list.map { it.toModel() } }

    suspend fun allSets(): List<TrainingSet> = dao.getAll(exerciseId).map { it.toModel() }

    suspend fun addSet(date: LocalDate, reps: Int, note: String) {
        dao.insert(SetEntity(exerciseId = exerciseId, date = date, reps = reps, note = note.trim(), createdAt = now()))
    }

    /** Edit of a day (as in the prototype): its sets are replaced by one set with the new total. */
    suspend fun replaceDay(originalDate: LocalDate, date: LocalDate, reps: Int, note: String) {
        val set = SetEntity(exerciseId = exerciseId, date = date, reps = reps, note = note.trim(), createdAt = now())
        dao.replaceDay(exerciseId, originalDate, set)
    }

    /** Deletes a day; returns its sets so the deletion can be undone with [restore]. */
    suspend fun deleteDay(date: LocalDate): List<TrainingSet> =
        dao.removeDay(exerciseId, date).map { it.toModel() }

    suspend fun restore(sets: List<TrainingSet>) {
        dao.insertAll(sets.map { it.toEntity() })
    }

    /** Replaces all data with [sets] (undo of a "replace all" import). */
    suspend fun replaceAll(sets: List<TrainingSet>) {
        dao.replaceAll(exerciseId, sets.map { it.toEntity() })
    }

    /**
     * Imports sets from a file. Returns the previous data for [ImportMode.REPLACE_ALL] (for undo),
     * and the number of imported days.
     */
    suspend fun import(imported: List<ImportedSet>, mode: ImportMode): ImportOutcome {
        val entities = toEntities(imported)
        return when (mode) {
            ImportMode.ADD_MISSING_DAYS -> ImportOutcome(dao.insertMissingDays(exerciseId, entities), previous = null)
            ImportMode.REPLACE_ALL -> {
                val previous = dao.replaceAll(exerciseId, entities)
                ImportOutcome(entities.distinctBy { it.date }.size, previous.map { it.toModel() })
            }
        }
    }

    /** Files without recording times get 12:00, 12:01, … per day so the set order is kept. */
    private fun toEntities(imported: List<ImportedSet>): List<SetEntity> {
        val zone = zone()
        return imported.groupBy { it.date }.flatMap { (date, sets) ->
            sets.mapIndexed { index, set ->
                val createdAt = set.createdAt
                    ?: date.atTime(12, 0).plusMinutes(index.toLong()).atZone(zone).toInstant().toEpochMilli()
                SetEntity(exerciseId = exerciseId, date = date, reps = set.reps, note = set.note.trim(), createdAt = createdAt)
            }
        }
    }

    private fun SetEntity.toModel() = TrainingSet(id = id, date = date, reps = reps, note = note, createdAt = createdAt)

    private fun TrainingSet.toEntity() =
        SetEntity(exerciseId = exerciseId, date = date, reps = reps, note = note, createdAt = createdAt)
}

data class ImportOutcome(val importedDays: Int, val previous: List<TrainingSet>?)
