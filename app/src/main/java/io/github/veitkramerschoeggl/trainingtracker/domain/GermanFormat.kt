package io.github.veitkramerschoeggl.trainingtracker.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Formats as the prototype shows them with the de-AT locale ("Mo., 22.06.2026", "Jän. 2026",
 * "1 129"). Written out explicitly so the output does not depend on the device's ICU version.
 */
object GermanFormat {
    private val weekdaysShort = listOf("Mo.", "Di.", "Mi.", "Do.", "Fr.", "Sa.", "So.")
    private val weekdaysLong =
        listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    private val monthsShort =
        listOf("Jän.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.")

    /** "Mo., 22.06.2026" */
    fun dayLabel(date: LocalDate): String = "${weekdayShort(date)}, ${date(date)}"

    /** "22.06.2026" */
    fun date(date: LocalDate): String = "${dayMonth(date)}${date.year}"

    /** "22.06." */
    fun dayMonth(date: LocalDate): String = "%02d.%02d.".format(date.dayOfMonth, date.monthValue)

    fun weekdayShort(date: LocalDate): String = weekdaysShort[date.dayOfWeek.value - 1]

    /** "Montag" */
    fun weekdayLong(date: LocalDate): String = weekdaysLong[date.dayOfWeek.value - 1]

    /** "Jän. 2026" */
    fun month(month: YearMonth): String = "${monthsShort[month.monthValue - 1]} ${month.year}"

    /** "KW 39/2026" */
    fun weekTitle(week: IsoWeek): String = "KW ${week.week}/${week.year}"

    /** "21.09. – 27.09." */
    fun weekRange(week: IsoWeek): String = "${dayMonth(week.monday)} – ${dayMonth(week.sunday)}"

    /** "KW 39/2026 (21.09. – 27.09.)" */
    fun weekLabel(week: IsoWeek): String = "${weekTitle(week)} (${weekRange(week)})"

    /** "3 Tage", "1 Tag" */
    fun days(count: Int): String = if (count == 1) "$count Tag" else "$count Tage"

    /** "10 + 5 + 8 Sätze" or "1 Satz" */
    fun sets(day: TrainingDay): String =
        if (day.sets.size > 1) day.sets.joinToString(" + ") { it.reps.toString() } + " Sätze" else "1 Satz"

    /** Thousands separated by a no-break space like de-AT: "1 129". */
    fun number(value: Int): String {
        val digits = kotlin.math.abs(value.toLong()).toString()
        val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
        return if (value < 0) "-$grouped" else grouped
    }
}
