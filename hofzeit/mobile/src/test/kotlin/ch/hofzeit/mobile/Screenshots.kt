package ch.hofzeit.mobile

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import ch.hofzeit.core.DefaultTasks
import ch.hofzeit.core.Session
import ch.hofzeit.core.Task
import ch.hofzeit.core.finish
import ch.hofzeit.core.newSession
import ch.hofzeit.core.pause
import ch.hofzeit.core.resume
import ch.hofzeit.core.switchTo
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class Screenshots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val zone = ZoneId.systemDefault()
    private fun task(id: String): Task = DefaultTasks.all.first { it.id == id }
    private fun t(day: Int, h: Int, m: Int) =
        LocalDate.of(2026, 11, day).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun morning(day: Int, id: String): Session = newSession(id, task("misten"), t(day, 6, 0))
        .switchTo(task("melken"), t(day, 6, 40 + day % 5))
        .pause(t(day, 7, 35))
        .resume(t(day, 7, 50))
        .switchTo(task("fuettern"), t(day, 8, 5))
        .finish(t(day, 8, 30 + day % 7))!!

    private fun evening(day: Int, id: String): Session = newSession(id, task("melken"), t(day, 17, 0))
        .switchTo(task("einstreuen"), t(day, 18, 0))
        .finish(t(day, 18, 25))!!

    private val sessions = (20..26).flatMap { listOf(morning(it, "m$it"), evening(it, "e$it")) } +
        newSession("f", task("feld"), t(24, 9, 0)).switchTo(task("maschinen"), t(24, 12, 0)).finish(t(24, 13, 30))!!

    private val now = t(27, 6, 25)
    private val running = newSession("r", task("misten"), t(27, 6, 0))

    @Test
    fun uebersicht() {
        paparazzi.snapshot {
            PhoneTheme(dark = false) {
                PhoneScreen(
                    state = PhoneUiState(sessions, running, WatchStatus.Connected(listOf("Galaxy Watch")), now),
                    month = YearMonth.of(2026, 11),
                    onMonth = {}, onFetch = {}, onShare = {}, onDelete = {},
                )
            }
        }
    }

    @Test
    fun uebersicht_dunkel() {
        paparazzi.snapshot {
            PhoneTheme(dark = true) {
                PhoneScreen(
                    state = PhoneUiState(sessions, null, WatchStatus.None, now),
                    month = YearMonth.of(2026, 11),
                    onMonth = {}, onFetch = {}, onShare = {}, onDelete = {},
                )
            }
        }
    }
}
