package ch.hofzeit.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import ch.hofzeit.core.DefaultTasks
import ch.hofzeit.core.Format
import ch.hofzeit.core.Session
import ch.hofzeit.core.Task
import ch.hofzeit.core.newSession
import ch.hofzeit.core.pause
import ch.hofzeit.core.finish
import ch.hofzeit.core.resume
import ch.hofzeit.core.switchTo
import ch.hofzeit.core.totals
import ch.hofzeit.core.workMs
import ch.hofzeit.wear.ui.AmbientScreen
import ch.hofzeit.wear.ui.ConfirmEndScreen
import ch.hofzeit.wear.ui.HistoryScreen
import ch.hofzeit.wear.ui.HofzeitTheme
import ch.hofzeit.wear.ui.HomeScreen
import ch.hofzeit.wear.ui.LocalNow
import ch.hofzeit.wear.ui.RunningScreen
import ch.hofzeit.wear.ui.SummaryScreen
import ch.hofzeit.wear.ui.SwitchScreen
import ch.hofzeit.wear.ui.TasksScreen
import ch.hofzeit.wear.ui.TodayScreen
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Bilder der Uhr-Bildschirme mit Beispieldaten (für die Anleitung und zur Kontrolle). */
class Screenshots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.WEAR_OS_SMALL_ROUND,
        theme = "android:Theme.DeviceDefault",
    )

    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 11, 26)
    private fun t(h: Int, m: Int, s: Int = 0) = day.atTime(h, m, s).atZone(zone).toInstant().toEpochMilli()
    private fun task(id: String): Task = DefaultTasks.all.first { it.id == id }

    private val morning: Session = newSession("m", task("misten"), t(6, 0))
        .switchTo(task("melken"), t(6, 42))
        .pause(t(7, 35))
        .resume(t(7, 50))
        .switchTo(task("fuettern"), t(8, 5))
        .finish(t(8, 31))!!

    private val evening: Session = newSession("r", task("melken"), t(17, 0))

    private val yesterday: Session = newSession("y", task("feld"), t(6, 0) - 86_400_000)
        .switchTo(task("maschinen"), t(9, 0) - 86_400_000)
        .finish(t(11, 15) - 86_400_000)!!

    private fun shot(now: Long, content: @Composable () -> Unit) {
        paparazzi.snapshot {
            CompositionLocalProvider(LocalNow provides now) {
                HofzeitTheme { content() }
            }
        }
    }

    @Test
    fun a_start() = shot(t(5, 58)) {
        HomeScreen(
            tasks = DefaultTasks.all, running = null, todayWorkMs = 0, todayByTask = emptyMap(),
            onTask = {}, onOpenRunning = {}, onToday = {}, onHistory = {}, onTasks = {},
        )
    }

    @Test
    fun b_laeuft() = shot(t(17, 12, 34)) {
        RunningScreen(evening, morning.workMs(0) + evening.workMs(t(17, 12, 34)), {}, {}, {}, {})
    }

    @Test
    fun c_pause() {
        val now = t(17, 24, 10)
        val paused = evening.pause(t(17, 20))
        shot(now) { RunningScreen(paused, morning.workMs(0) + paused.workMs(now), {}, {}, {}, {}) }
    }

    @Test
    fun d_wechseln() = shot(t(17, 13)) { SwitchScreen(DefaultTasks.all, "melken") {} }

    @Test
    fun e_beenden() = shot(t(8, 31)) { ConfirmEndScreen(morning.workMs(0), {}, {}) }

    @Test
    fun f_zusammenfassung() = shot(t(8, 31)) { SummaryScreen(morning, justFinished = true, onDone = {}, onDelete = {}) }

    @Test
    fun g_start_laeuft() {
        val now = t(17, 12, 34)
        val sessions = listOf(morning, evening)
        shot(now) {
            HomeScreen(
                tasks = DefaultTasks.all, running = evening, todayWorkMs = sessions.workMs(now),
                todayByTask = sessions.totals(now).associate { it.taskId to it.ms },
                onTask = {}, onOpenRunning = {}, onToday = {}, onHistory = {}, onTasks = {},
            )
        }
    }

    @Test
    fun h_heute() = shot(t(17, 12, 34)) { TodayScreen(listOf(morning, evening)) }

    @Test
    fun i_verlauf() = shot(t(9, 0)) {
        HistoryScreen(listOf(morning, yesterday), day, onOpen = {}, onResend = { 0 })
    }

    @Test
    fun j_taetigkeiten() = shot(t(9, 0)) { TasksScreen(DefaultTasks.all, {}, {}, {}) }

    @Test
    fun k_always_on() = shot(t(17, 12, 34)) { AmbientScreen(evening, t(17, 12, 34)) }

    @Test
    fun formatCheck() {
        // Kleine Kontrolle, dass die Beispieldaten stimmen.
        check(Format.hm(morning.workMs(0)) == "2:16")
    }
}
