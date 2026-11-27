package ch.hofzeit.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatCsvTest {
    private val zone = ZoneId.of("Europe/Zurich")
    private val misten = DefaultTasks.work.first { it.id == "misten" }
    private val melken = DefaultTasks.work.first { it.id == "melken" }

    private fun t(d: Int, h: Int, m: Int) =
        LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun formate() {
        assertEquals("00:07", Format.stopwatch(7_400))
        assertEquals("1:02:03", Format.stopwatch((3600 + 120 + 3) * 1000L))
        assertEquals("2:05", Format.hm((125 * 60 + 59) * 1000L))
        assertEquals("0:00 h", Format.hours(-5))
        val d = LocalDate.of(2026, 11, 26)
        assertEquals("Do 26.11.", Format.dayShort(d))
        assertEquals("Donnerstag, 26. November", Format.dayLong(d))
        assertEquals("November 2026", Format.month(d))
        assertEquals("Heute", Format.relativeDay(d, d))
        assertEquals("Gestern", Format.relativeDay(d.minusDays(1), d))
        assertEquals("06:00 – 07:30", Format.range(t(26, 6, 0), t(26, 7, 30), zone))
    }

    @Test
    fun csvTage() {
        val a = newSession("a", misten, t(26, 6, 0)).switchTo(melken, t(26, 6, 40)).finish(t(26, 7, 40))!!
        val b = newSession("b", melken, t(26, 17, 0)).pause(t(26, 18, 0)).finish(t(26, 18, 15))!!
        val c = newSession("c", misten, t(27, 6, 0)).finish(t(27, 6, 30))!!
        val csv = Csv.days(listOf(c, b, a), zone)
        assertTrue(csv.startsWith("﻿"))
        val lines = csv.removePrefix("﻿").trimEnd().lines()
        assertEquals("Datum;Tag;Von;Bis;Melken;Misten;Arbeit (h:mm);Pause (h:mm);Arbeit (Minuten)", lines[0])
        assertEquals("26.11.2026;Do;06:00;18:15;2:00;0:40;2:40;0:15;160", lines[1])
        assertEquals("27.11.2026;Fr;06:00;06:30;;0:30;0:30;0:00;30", lines[2])
        assertEquals("Total;;;;2:00;1:10;3:10;0:15;190", lines[3])
    }

    @Test
    fun csvEintraege() {
        val a = newSession("a", misten, t(26, 6, 0)).pause(t(26, 6, 40)).finish(t(26, 7, 0))!!
        val custom = Task("x", "Zaun; flicken", "", 0xFF000000)
        val b = newSession("b", custom, t(26, 8, 0)).finish(t(26, 8, 30))!!
        val lines = Csv.entries(listOf(b, a), zone).removePrefix("﻿").trimEnd().lines()
        assertEquals("Datum;Tag;Von;Bis;Tätigkeit;Art;Dauer (h:mm);Minuten", lines[0])
        assertEquals("26.11.2026;Do;06:00;06:40;Misten;Arbeit;0:40;40", lines[1])
        assertEquals("26.11.2026;Do;06:40;07:00;Pause;Pause;0:20;20", lines[2])
        assertEquals("26.11.2026;Do;08:00;08:30;\"Zaun; flicken\";Arbeit;0:30;30", lines[3])
    }
}
