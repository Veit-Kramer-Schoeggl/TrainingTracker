package io.github.veitkramerschoeggl.trainingtracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Kind of exercise. For now there is only one row (pull-ups), see [TrainingDatabase]. */
@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val id: Long,
    val name: String,
)

/** One set: [reps] repetitions on training day [date]. A day's total is the sum of its sets. */
@Entity(
    tableName = "training_set",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
        ),
    ],
    indices = [Index(value = ["exercise_id", "date"])],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    /** Training day in local time, stored as ISO text (yyyy-MM-dd). */
    val date: LocalDate,
    val reps: Int,
    val note: String = "",
    /** When the set was recorded (epoch millis); orders the sets within a day. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
