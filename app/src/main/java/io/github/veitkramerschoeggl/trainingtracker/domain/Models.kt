package io.github.veitkramerschoeggl.trainingtracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoField
import java.time.temporal.IsoFields

data class TrainingSet(
    val id: Long,
    val date: LocalDate,
    val reps: Int,
    val note: String,
    /** Epoch millis; orders the sets within a day. */
    val createdAt: Long,
)

/** All sets of one training day, in the order they were recorded. */
data class TrainingDay(val date: LocalDate, val sets: List<TrainingSet>) {
    val total: Int = sets.sumOf { it.reps }
    val notes: List<String> get() = sets.map { it.note }.filter { it.isNotBlank() }
}

/** ISO 8601 calendar week (Monday–Sunday), as used for "KW 39/2026". */
data class IsoWeek(val year: Int, val week: Int, val monday: LocalDate) : Comparable<IsoWeek> {
    val sunday: LocalDate get() = monday.plusDays(6)
    val key: String get() = "%04d-W%02d".format(year, week)

    override fun compareTo(other: IsoWeek) = monday.compareTo(other.monday)

    companion object {
        fun of(date: LocalDate) = IsoWeek(
            year = date.get(IsoFields.WEEK_BASED_YEAR),
            week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
            monday = date.with(ChronoField.DAY_OF_WEEK, 1),
        )
    }
}

data class TrainingWeek(val week: IsoWeek, val days: List<TrainingDay>) {
    val total: Int = days.sumOf { it.total }
}
