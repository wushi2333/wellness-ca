// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.ui.chart

import iss.nus.edu.sg.ca_application.model.DailyWellness
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Week-bound utilities using the new split data model ([DailyWellness]).
 */
object WeekUtils {

    private var cachedLocale: Locale = Locale.getDefault()
    private var displayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", cachedLocale)

    fun setLocale(locale: Locale) {
        cachedLocale = locale
        displayFormat = DateTimeFormatter.ofPattern("MMM d", locale)
    }

    fun weekMonday(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    fun currentWeekMonday(): LocalDate = LocalDate.now().with(DayOfWeek.MONDAY)

    fun weekLabel(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        return "${monday.format(displayFormat)} – ${sunday.format(displayFormat)}"
    }

    /**
     * Build a 7-element list of [T] for Monday–Sunday.
     * [picker] receives the DailyWellness for that day (or null if no entry exists).
     */
    fun <T> buildWeekArray(
        monday: LocalDate,
        dailies: List<DailyWellness>,
        picker: (DailyWellness?) -> T?
    ): List<T?> {
        return (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            val daily = dailies.firstOrNull { d ->
                try { LocalDate.parse(d.recordDate) == date } catch (_: Exception) { false }
            }
            picker(daily)
        }
    }

    /** List of Mondays (newest first) for weeks that have at least one daily entry. */
    fun availableWeeks(dailies: List<DailyWellness>): List<LocalDate> {
        val mondays = mutableSetOf<LocalDate>()
        for (d in dailies) {
            try {
                val date = LocalDate.parse(d.recordDate)
                mondays.add(date.with(DayOfWeek.MONDAY))
            } catch (_: Exception) {}
        }
        return mondays.sortedDescending()
    }

    val dayLabels: List<String>
        get() = if (cachedLocale.language == "zh")
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        else
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}
