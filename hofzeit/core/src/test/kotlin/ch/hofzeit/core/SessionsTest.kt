package ch.hofzeit.core

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionsTest {
    private val zone = ZoneId.of("Europe/Zurich")
    private val misten = DefaultTasks.work.first { it.id == "misten" }
    private val melken = DefaultTasks.work.first { it.id == "melken" }
    private val fuettern = DefaultTasks.work.first { it.id == "fuettern" }

    private fun t(h: Int, m: Int, s: Int = 0) =
        LocalDateTime.of(2026, 11, 26, h, m, s).atZone(zone).toInstant().toEpochMilli()

    private val min = 60_000L

    @Test
    fun normalerMorgen() {
        var s = newSession("a", misten, t(6, 0))
        s = s.switchTo(melken, t(6, 40))
        s = s.pause(t(7, 30))
        assertTrue(s.isPaused)
        s = s.resume(t(7, 45))
        assertEquals("melken", s.current?.taskId)
        s = s.switchTo(fuettern, t(8, 0))
        val done = assertNotNull(s.finish(t(8, 20)))

        assertEquals(5, done.segments.size)
        assertEquals(t(6, 0), done.start)
        assertEquals(t(8, 20), done.end)
        assertEquals((40 + 50 + 15 + 20) * min, done.workMs(0))
        assertEquals(15 * min, done.pauseMs(0))

        val totals = listOf(done).totals(0)
        assertEquals(listOf("melken", "misten", "fuettern", "pause"), totals.map { it.taskId })
        assertEquals(65 * min, totals.first().ms)
    }

    @Test
    fun fehltippWirdVerworfen() {
        var s = newSession("a", misten, t(6, 0))
        s = s.switchTo(fuettern, t(6, 30))
        s = s.switchTo(melken, t(6, 30, 3)) // Füttern nur 3 s
        assertEquals(listOf("misten", "melken"), s.segments.map { it.taskId })
    }

    @Test
    fun fehltippZurueckZurGleichenArbeitLaeuftWeiter() {
        var s = newSession("a", misten, t(6, 0))
        s = s.switchTo(fuettern, t(6, 30))
        s = s.switchTo(misten, t(6, 30, 2))
        assertEquals(1, s.segments.size)
        assertNull(s.segments.single().end)
        assertEquals(t(6, 0), s.segments.single().start)
    }

    @Test
    fun pauseUndSofortWeiter() {
        var s = newSession("a", melken, t(6, 0))
        s = s.pause(t(6, 10))
        s = s.resume(t(6, 10, 1))
        assertEquals(1, s.segments.size)
        assertEquals("melken", s.current?.taskId)
    }

    @Test
    fun gleicheTaetigkeitIstKeinWechsel() {
        val s = newSession("a", melken, t(6, 0))
        assertEquals(s, s.switchTo(melken, t(6, 10)))
    }

    @Test
    fun versehentlichGestartetWirdNichtGespeichert() {
        val s = newSession("a", melken, t(6, 0))
        assertNull(s.finish(t(6, 0, 3)))
        // Nur Pause übrig: ebenfalls nichts speichern.
        assertNull(s.pause(t(6, 0, 2)).finish(t(6, 10)))
    }

    @Test
    fun pauseAmEndeBleibtPauseAmAnfangNicht() {
        var s = newSession("a", melken, t(6, 0))
        s = s.pause(t(7, 0))
        val done = assertNotNull(s.finish(t(7, 30)))
        assertEquals(t(7, 30), done.end)
        assertEquals(30 * min, done.pauseMs(0))
    }

    @Test
    fun laufendeZeitWirdMitgezaehlt() {
        val s = newSession("a", melken, t(6, 0))
        assertEquals(15 * min, s.workMs(t(6, 15)))
        assertEquals("15:00", Format.stopwatch(s.current!!.durationMs(t(6, 15))))
    }

    @Test
    fun archivMerge() {
        val done = newSession("a", melken, t(6, 0)).finish(t(7, 0))!!
        val running = newSession("b", melken, t(8, 0))
        var archive = PhoneArchive().merge(listOf(done, running))
        assertEquals(listOf("a"), archive.sessions.map { it.id })
        assertTrue(archive === archive.merge(listOf(done)))

        archive = archive.delete("a")
        assertTrue(archive.merge(listOf(done)).sessions.isEmpty())
    }

    @Test
    fun codecRoundtrip() {
        val s = newSession("a", melken, t(6, 0)).switchTo(misten, t(6, 30)).finish(t(7, 0))!!
        assertEquals(s, Codec.decodeSession(Codec.encode(s)))
        val state = WatchState(running = s)
        assertEquals(state, Codec.decodeWatchState(Codec.encode(state)))
        assertNull(Codec.decodeSession("kaputt"))
        // Unbekannte Felder aus neueren Versionen stören nicht.
        assertNotNull(Codec.decodeSession(Codec.encode(s).replaceFirst("{", "{\"neu\":1,")))
    }
}
