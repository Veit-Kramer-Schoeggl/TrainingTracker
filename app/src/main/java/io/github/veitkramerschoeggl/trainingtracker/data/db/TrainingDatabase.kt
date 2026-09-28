package io.github.veitkramerschoeggl.trainingtracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

/**
 * Local SQLite database (Room). It lives in the app's private storage and survives app updates.
 *
 * Changing the schema: bump [version], add a [Migration] to [MIGRATIONS] and a test in
 * `MigrationTest`. Never use `fallbackToDestructiveMigration()` — it would wipe all training data.
 */
@Database(
    entities = [ExerciseEntity::class, SetEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TrainingDatabase : RoomDatabase() {

    abstract fun setDao(): SetDao

    companion object {
        const val NAME = "training.db"
        const val PULLUPS_EXERCISE_ID = 1L

        /** All migrations, oldest first, e.g. `MIGRATION_1_2`. */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun create(context: Context): TrainingDatabase =
            Room.databaseBuilder(context, TrainingDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .addCallback(SeedExercises)
                .build()
    }

    /** Rows every fresh database starts with. */
    object SeedExercises : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO exercise (id, name) VALUES ($PULLUPS_EXERCISE_ID, 'Klimmzüge')")
        }
    }
}

class Converters {
    @TypeConverter
    fun dateToString(date: LocalDate): String = date.toString()

    @TypeConverter
    fun stringToDate(value: String): LocalDate = LocalDate.parse(value)
}
