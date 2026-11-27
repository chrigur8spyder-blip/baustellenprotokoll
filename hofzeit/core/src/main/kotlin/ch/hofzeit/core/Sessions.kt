package ch.hofzeit.core

/**
 * Abschnitte, die kürzer sind, gelten als Fehltipp: Wer «Füttern» antippt und sofort zu «Melken»
 * wechselt, soll keinen 2-Sekunden-Eintrag «Füttern» im Verlauf haben.
 */
const val MIN_SEGMENT_MS = 5_000L

/** Der laufende Abschnitt, falls der Einsatz noch läuft. */
val Session.current: Segment?
    get() = if (end == null) segments.lastOrNull()?.takeIf { it.end == null } else null

val Session.isRunning: Boolean get() = current != null

val Session.isPaused: Boolean get() = current?.isPause == true

/** Die letzte richtige Arbeit (keine Pause). Dorthin führt «Weiter» nach einer Pause. */
val Session.lastWork: Segment? get() = segments.lastOrNull { !it.isPause }

fun newSession(id: String, task: Task, now: Long) =
    Session(id = id, start = now, segments = listOf(task.segmentAt(now)))

/** Wechselt zur Tätigkeit [next]. Der bisherige Abschnitt wird bei [now] abgeschlossen. */
fun Session.switchTo(next: Segment, now: Long): Session {
    val cur = current ?: return this
    if (cur.taskId == next.taskId) return this

    val closed = closeCurrent(now)
    val last = closed.segments.lastOrNull()
    // Gleiche Tätigkeit wie direkt davor (nach einem Fehltipp): einfach weiterlaufen lassen.
    if (last != null && last.taskId == next.taskId && last.end != null && now - last.end <= MIN_SEGMENT_MS) {
        return closed.copy(segments = closed.segments.dropLast(1) + last.copy(end = null))
    }
    return closed.copy(segments = closed.segments + next.copy(start = now, end = null))
}

fun Session.switchTo(task: Task, now: Long): Session = switchTo(task.segmentAt(now), now)

fun Session.pause(now: Long): Session = switchTo(DefaultTasks.pause, now)

/** Nach einer Pause mit der zuletzt gemachten Arbeit weitermachen. */
fun Session.resume(now: Long): Session {
    val work = lastWork ?: return this
    return switchTo(work.copy(start = now, end = null), now)
}

/**
 * Beendet den Einsatz. Gibt `null` zurück, wenn nichts Speicherbares übrig bleibt
 * (z. B. aus Versehen gestartet und gleich wieder beendet).
 */
fun Session.finish(now: Long): Session? {
    val closed = closeCurrent(now)
    if (closed.segments.none { !it.isPause }) return null
    // Eine Pause ganz am Anfang zählt nicht.
    val segs = closed.segments.dropWhile { it.isPause }
    return closed.copy(start = segs.first().start, end = segs.last().end, segments = segs)
}

/** Schliesst den laufenden Abschnitt; zu kurze Abschnitte werden verworfen. */
private fun Session.closeCurrent(now: Long): Session {
    val cur = current ?: return this
    val rest = segments.dropLast(1)
    return if (now - cur.start < MIN_SEGMENT_MS) copy(segments = rest) else copy(segments = rest + cur.copy(end = now))
}

// ---------------------------------------------------------------------------------------------
// Summen

data class TaskTotal(
    val taskId: String,
    val name: String,
    val emoji: String,
    val color: Long,
    val isPause: Boolean,
    val ms: Long,
)

fun Session.workMs(now: Long): Long = segments.filter { !it.isPause }.sumOf { it.durationMs(now) }

fun Session.pauseMs(now: Long): Long = segments.filter { it.isPause }.sumOf { it.durationMs(now) }

fun Iterable<Session>.workMs(now: Long): Long = sumOf { it.workMs(now) }

fun Iterable<Session>.pauseMs(now: Long): Long = sumOf { it.pauseMs(now) }

/** Zeit pro Tätigkeit: Arbeit nach Dauer absteigend, Pause zuletzt. */
fun Iterable<Session>.totals(now: Long): List<TaskTotal> =
    flatMap { it.segments }
        .groupBy { it.taskId }
        .map { (id, segs) ->
            val latest = segs.maxBy { it.start }
            TaskTotal(id, latest.name, latest.emoji, latest.color, latest.isPause, segs.sumOf { it.durationMs(now) })
        }
        .filter { it.ms > 0 }
        .sortedWith(compareBy<TaskTotal> { it.isPause }.thenByDescending { it.ms })

/** Fügt eingehende Einsätze ins Handy-Archiv ein (gleiche ID wird ersetzt, gelöschte bleiben weg). */
fun PhoneArchive.merge(incoming: Collection<Session>): PhoneArchive {
    if (incoming.isEmpty()) return this
    val byId = sessions.associateBy { it.id }.toMutableMap()
    var changed = false
    for (s in incoming) {
        if (s.id in deleted || s.end == null) continue
        if (byId[s.id] != s) {
            byId[s.id] = s
            changed = true
        }
    }
    if (!changed) return this
    return copy(sessions = byId.values.sortedByDescending { it.start })
}

fun PhoneArchive.delete(id: String): PhoneArchive =
    copy(sessions = sessions.filterNot { it.id == id }, deleted = deleted + id)
