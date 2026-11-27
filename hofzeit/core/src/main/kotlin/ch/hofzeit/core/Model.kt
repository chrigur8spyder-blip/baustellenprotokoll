package ch.hofzeit.core

import kotlinx.serialization.Serializable

/** Eine Tätigkeit, die auf der Uhr gestartet werden kann (Misten, Melken, …). */
@Serializable
data class Task(
    val id: String,
    val name: String,
    val emoji: String = "",
    /** Farbe als 0xAARRGGBB. */
    val color: Long,
    val isPause: Boolean = false,
) {
    /** Ein neuer, offener Abschnitt dieser Tätigkeit ab [now]. */
    fun segmentAt(now: Long) = Segment(
        taskId = id, name = name, emoji = emoji, color = color, isPause = isPause, start = now,
    )
}

/**
 * Ein Stück am Stück erledigte Arbeit (oder Pause). Name, Symbol und Farbe werden mitgespeichert,
 * damit der Verlauf auch dann stimmt, wenn die Tätigkeit später gelöscht wird.
 */
@Serializable
data class Segment(
    val taskId: String,
    val name: String,
    val emoji: String = "",
    val color: Long,
    val isPause: Boolean = false,
    val start: Long,
    val end: Long? = null,
) {
    fun durationMs(now: Long): Long = ((end ?: now) - start).coerceAtLeast(0)
}

/** Ein Arbeitseinsatz vom Start bis «Beenden», mit allen Abschnitten der Reihe nach. */
@Serializable
data class Session(
    val id: String,
    val start: Long,
    val end: Long? = null,
    val segments: List<Segment> = emptyList(),
)

/** Alles, was die Uhr dauerhaft speichert, ausser dem Verlauf. */
@Serializable
data class WatchState(
    val tasks: List<Task> = DefaultTasks.all,
    val running: Session? = null,
)

/** Verlauf auf der Uhr, neueste zuerst. */
@Serializable
data class History(
    val sessions: List<Session> = emptyList(),
)

/** Archiv auf dem Handy. [deleted] verhindert, dass gelöschte Einsätze bei der nächsten Übertragung zurückkommen. */
@Serializable
data class PhoneArchive(
    val sessions: List<Session> = emptyList(),
    val deleted: Set<String> = emptySet(),
)

object DefaultTasks {
    const val PAUSE_ID = "pause"

    val pause = Task(PAUSE_ID, "Pause", "☕", 0xFF90A4AE, isPause = true)

    val work = listOf(
        Task("misten", "Misten", "🧹", 0xFFC0905C),
        Task("melken", "Melken", "🥛", 0xFF64B5F6),
        Task("fuettern", "Füttern", "🌾", 0xFFFFD54F),
        Task("einstreuen", "Einstreuen", "🍂", 0xFFFF9E57),
        Task("tiere", "Tiere kontrollieren", "🐄", 0xFF81C784),
        Task("feld", "Feldarbeit", "🚜", 0xFF4DB6AC),
        Task("maschinen", "Maschinen", "🔧", 0xFFE57373),
        Task("sonstiges", "Sonstiges", "📋", 0xFFBA68C8),
    )

    val all = work + pause

    /** Farben für selbst angelegte Tätigkeiten, der Reihe nach vergeben. */
    val customColors = listOf(
        0xFFF06292, 0xFF7986CB, 0xFFDCE775, 0xFF4DD0E1, 0xFFA1887F, 0xFFFFB74D,
    )
}

/** Pfade für die Übertragung Uhr → Handy (Wearable Data Layer). */
object SyncPaths {
    const val SESSION_PREFIX = "/hofzeit/session/"
    const val RUNNING = "/hofzeit/running"
    const val RESEND = "/hofzeit/resend"
    const val KEY_JSON = "json"
    const val KEY_SENT_AT = "sentAt"

    fun session(id: String) = SESSION_PREFIX + id
}
