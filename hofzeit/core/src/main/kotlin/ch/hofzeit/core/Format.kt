package ch.hofzeit.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val locale: Locale = Locale.forLanguageTag("de-CH")
    private val clock = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val shortDate = DateTimeFormatter.ofPattern("d.M.", locale)
    private val fullDate = DateTimeFormatter.ofPattern("d.M.yyyy", locale)
    private val csvDate = DateTimeFormatter.ofPattern("dd.MM.yyyy", locale)
    private val longDate = DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale)
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    /** Stoppuhr: «04:07», ab einer Stunde «1:04:07». */
    fun stopwatch(ms: Long): String {
        val totalSec = ms.coerceAtLeast(0) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** Stunden und Minuten: «2:05». */
    fun hm(ms: Long): String {
        val totalMin = ms.coerceAtLeast(0) / 60_000
        return "%d:%02d".format(totalMin / 60, totalMin % 60)
    }

    /** «2:05 h». */
    fun hours(ms: Long): String = hm(ms) + " h"

    fun clock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        clock.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun localDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    /** «Do», «Fr», … */
    fun weekdayShort(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "Mo"; 2 -> "Di"; 3 -> "Mi"; 4 -> "Do"; 5 -> "Fr"; 6 -> "Sa"; else -> "So"
    }

    /** «Do 26.11.» */
    fun dayShort(date: LocalDate): String = weekdayShort(date) + " " + shortDate.format(date)

    /** «Do 26.11.2026» */
    fun dayFull(date: LocalDate): String = weekdayShort(date) + " " + fullDate.format(date)

    /** «Donnerstag, 26. November» */
    fun dayLong(date: LocalDate): String = longDate.format(date)

    /** «November 2026» */
    fun month(date: LocalDate): String = month.format(date).replaceFirstChar { it.uppercase(locale) }

    fun csvDate(date: LocalDate): String = csvDate.format(date)

    /** «heute», «gestern» oder das Datum. */
    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> dayShort(date)
    }

    /** «06:02 – 08:45» */
    fun range(start: Long, end: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
        clock(start, zone) + " – " + (end?.let { clock(it, zone) } ?: "läuft")
}
