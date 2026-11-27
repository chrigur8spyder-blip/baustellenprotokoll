package ch.hofzeit.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * CSV für Excel/Numbers: Semikolon getrennt, UTF-8 mit BOM (damit Umlaute stimmen).
 * Zeiten als «h:mm» (Excel erkennt das als Zeit) und zusätzlich in ganzen Minuten.
 */
object Csv {
    private const val BOM = "﻿"
    private const val NL = "\r\n"

    private fun minutes(ms: Long): Long = (ms / 60_000.0).roundToLong()
    private fun hm(ms: Long): String {
        val m = minutes(ms)
        return "%d:%02d".format(m / 60, m % 60)
    }

    private fun cell(text: String): String =
        if (text.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

    private fun row(cells: List<String>) = cells.joinToString(";") { cell(it) } + NL

    /** Jeder Abschnitt eine Zeile. */
    fun entries(sessions: List<Session>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder(BOM)
        sb.append(row(listOf("Datum", "Tag", "Von", "Bis", "Tätigkeit", "Art", "Dauer (h:mm)", "Minuten")))
        for (s in sessions.filter { it.end != null }.sortedBy { it.start }) {
            for (seg in s.segments) {
                val end = seg.end ?: continue
                val date = Format.localDate(seg.start, zone)
                val ms = seg.durationMs(end)
                sb.append(
                    row(
                        listOf(
                            Format.csvDate(date),
                            Format.weekdayShort(date),
                            Format.clock(seg.start, zone),
                            Format.clock(end, zone),
                            seg.name,
                            if (seg.isPause) "Pause" else "Arbeit",
                            hm(ms),
                            minutes(ms).toString(),
                        )
                    )
                )
            }
        }
        return sb.toString()
    }

    /** Eine Zeile pro Tag, eine Spalte pro Tätigkeit, am Schluss eine Summenzeile. */
    fun days(sessions: List<Session>, zone: ZoneId = ZoneId.systemDefault()): String {
        val finished = sessions.filter { it.end != null }
        val taskCols = finished.totals(0).filter { !it.isPause }
        val byDay: Map<LocalDate, List<Session>> =
            finished.groupBy { Format.localDate(it.start, zone) }.toSortedMap()

        val sb = StringBuilder(BOM)
        sb.append(
            row(
                listOf("Datum", "Tag", "Von", "Bis") + taskCols.map { it.name } +
                    listOf("Arbeit (h:mm)", "Pause (h:mm)", "Arbeit (Minuten)")
            )
        )
        for ((date, list) in byDay) {
            val perTask = list.totals(0).associateBy { it.taskId }
            val work = list.workMs(0)
            sb.append(
                row(
                    listOf(
                        Format.csvDate(date),
                        Format.weekdayShort(date),
                        Format.clock(list.minOf { it.start }, zone),
                        Format.clock(list.maxOf { it.end!! }, zone),
                    ) + taskCols.map { col -> perTask[col.taskId]?.let { hm(it.ms) } ?: "" } +
                        listOf(hm(work), hm(list.pauseMs(0)), minutes(work).toString())
                )
            )
        }
        if (byDay.isNotEmpty()) {
            val work = finished.workMs(0)
            sb.append(
                row(
                    listOf("Total", "", "", "") + taskCols.map { hm(it.ms) } +
                        listOf(hm(work), hm(finished.pauseMs(0)), minutes(work).toString())
                )
            )
        }
        return sb.toString()
    }
}
