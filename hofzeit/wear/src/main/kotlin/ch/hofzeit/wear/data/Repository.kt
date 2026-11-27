package ch.hofzeit.wear.data

import android.content.Context
import ch.hofzeit.core.DefaultTasks
import ch.hofzeit.core.History
import ch.hofzeit.core.Session
import ch.hofzeit.core.Task
import ch.hofzeit.core.WatchState
import ch.hofzeit.core.finish
import ch.hofzeit.core.isRunning
import ch.hofzeit.core.newSession
import ch.hofzeit.core.pause
import ch.hofzeit.core.resume
import ch.hofzeit.core.switchTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Zentrale Stelle für alles, was die Uhr weiss. Die Oberfläche liest [state] und [history],
 * Änderungen gehen über die Funktionen hier. Gespeichert wird im Hintergrund, der Reihe nach.
 */
class Repository(context: Context, private val scope: CoroutineScope) {
    private val storage = Storage(File(context.filesDir, "hofzeit"))
    private val sync = PhoneSync(context)
    private val ongoing = OngoingNotifier(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(storage.loadState())
    val state: StateFlow<WatchState> = _state.asStateFlow()

    private val _history = MutableStateFlow(storage.loadHistory().sessions)
    /** Abgeschlossene Einsätze, neueste zuerst. */
    val history: StateFlow<List<Session>> = _history.asStateFlow()

    private fun now() = System.currentTimeMillis()

    /** Startet die Tätigkeit; läuft schon etwas, wird dorthin gewechselt. */
    fun start(task: Task) {
        val now = now()
        val running = _state.value.running
        setRunning(running?.switchTo(task, now) ?: newSession(UUID.randomUUID().toString(), task, now))
    }

    fun pause() {
        _state.value.running?.let { setRunning(it.pause(now())) }
    }

    fun resume() {
        _state.value.running?.let { setRunning(it.resume(now())) }
    }

    /** Beendet den Einsatz und legt ihn im Verlauf ab. `null`, wenn er zu kurz war. */
    fun finish(): Session? {
        val running = _state.value.running ?: return null
        val done = running.finish(now())
        update { it.copy(running = null) }
        ongoing.cancel()
        scope.launch(io) { sync.putRunning(null) }
        if (done != null) {
            _history.value = listOf(done) + _history.value
            saveHistory()
            scope.launch(io) { sync.putSession(done) }
        }
        return done
    }

    fun deleteSession(id: String) {
        _history.value = _history.value.filterNot { it.id == id }
        saveHistory()
        scope.launch(io) { sync.deleteSession(id) }
    }

    fun addTask(name: String) {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(28)
        if (clean.isEmpty()) return
        update { st ->
            if (st.tasks.any { it.name.equals(clean, ignoreCase = true) }) return@update st
            val customCount = st.tasks.count { it.id.startsWith("c-") }
            val color = DefaultTasks.customColors[customCount % DefaultTasks.customColors.size]
            val task = Task("c-" + UUID.randomUUID().toString().take(8), clean, "", color)
            val (work, pause) = st.tasks.partition { !it.isPause }
            st.copy(tasks = work + task + pause)
        }
    }

    fun removeTask(id: String) = update { st ->
        st.copy(tasks = st.tasks.filterNot { it.id == id && !it.isPause })
    }

    fun moveTaskToTop(id: String) = update { st ->
        val task = st.tasks.find { it.id == id } ?: return@update st
        st.copy(tasks = listOf(task) + st.tasks.filterNot { it.id == id })
    }

    /** Gelöschte Standard-Tätigkeiten wieder hinzufügen. */
    fun restoreDefaults() = update { st ->
        val missing = DefaultTasks.work.filter { d -> st.tasks.none { it.id == d.id } }
        val (work, pause) = st.tasks.partition { !it.isPause }
        st.copy(tasks = work + missing + pause.ifEmpty { listOf(DefaultTasks.pause) })
    }

    /** Anzeige auf dem Zifferblatt an den gespeicherten Stand angleichen (App-Start, Neustart). */
    fun refreshOngoing() {
        val running = _state.value.running
        if (running != null && running.isRunning) ongoing.show(running) else ongoing.cancel()
    }

    /** Alles nochmals ans Handy schicken. Gibt die Anzahl Einsätze zurück. */
    suspend fun resendAll(): Int = withContext(io) {
        val sessions = _history.value
        sync.putRunning(_state.value.running)
        sessions.forEach { sync.putSession(it, force = true) }
        sessions.size
    }

    private fun setRunning(session: Session) {
        update { it.copy(running = session) }
        ongoing.show(session)
        scope.launch(io) { sync.putRunning(session) }
    }

    private inline fun update(change: (WatchState) -> WatchState) {
        val old = _state.value
        val new = change(old)
        if (new == old) return
        _state.value = new
        scope.launch(io) { storage.saveState(new) }
    }

    private fun saveHistory() {
        val snapshot = History(_history.value)
        scope.launch(io) { storage.saveHistory(snapshot) }
    }
}
