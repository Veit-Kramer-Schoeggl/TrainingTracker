package io.github.veitkramerschoeggl.trainingtracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SetDao {

    @Query("SELECT * FROM training_set WHERE exercise_id = :exerciseId ORDER BY date, created_at, id")
    abstract fun observeAll(exerciseId: Long): Flow<List<SetEntity>>

    @Query("SELECT * FROM training_set WHERE exercise_id = :exerciseId ORDER BY date, created_at, id")
    abstract suspend fun getAll(exerciseId: Long): List<SetEntity>

    @Query("SELECT * FROM training_set WHERE exercise_id = :exerciseId AND date = :date ORDER BY created_at, id")
    abstract suspend fun getDay(exerciseId: Long, date: LocalDate): List<SetEntity>

    @Query("SELECT DISTINCT date FROM training_set WHERE exercise_id = :exerciseId")
    abstract suspend fun getDates(exerciseId: Long): List<LocalDate>

    @Insert
    abstract suspend fun insert(set: SetEntity): Long

    @Insert
    abstract suspend fun insertAll(sets: List<SetEntity>)

    @Query("DELETE FROM training_set WHERE exercise_id = :exerciseId AND date = :date")
    abstract suspend fun deleteDay(exerciseId: Long, date: LocalDate): Int

    @Query("DELETE FROM training_set WHERE exercise_id = :exerciseId")
    abstract suspend fun deleteAll(exerciseId: Long): Int

    /**
     * Edit of a day: the sets of [originalDate] are replaced by [newSet]. If [newSet] lands on a
     * different day that already has sets, it is added to them (never silently overwritten).
     */
    @Transaction
    open suspend fun replaceDay(exerciseId: Long, originalDate: LocalDate, newSet: SetEntity) {
        deleteDay(exerciseId, originalDate)
        insert(newSet)
    }

    /** Deletes a day and returns what was removed (for undo). */
    @Transaction
    open suspend fun removeDay(exerciseId: Long, date: LocalDate): List<SetEntity> {
        val removed = getDay(exerciseId, date)
        deleteDay(exerciseId, date)
        return removed
    }

    /** Replaces everything and returns the previous content (for undo). */
    @Transaction
    open suspend fun replaceAll(exerciseId: Long, sets: List<SetEntity>): List<SetEntity> {
        val previous = getAll(exerciseId)
        deleteAll(exerciseId)
        insertAll(sets)
        return previous
    }

    /** Inserts only the sets of days that have no data yet. Returns the number of added days. */
    @Transaction
    open suspend fun insertMissingDays(exerciseId: Long, sets: List<SetEntity>): Int {
        val existing = getDates(exerciseId).toSet()
        val toInsert = sets.filter { it.date !in existing }
        insertAll(toInsert)
        return toInsert.distinctBy { it.date }.size
    }
}
