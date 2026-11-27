package ch.hofzeit.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonColors
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import ch.hofzeit.core.DefaultTasks
import ch.hofzeit.core.Format
import ch.hofzeit.core.Session
import ch.hofzeit.core.Task
import ch.hofzeit.core.current
import ch.hofzeit.core.lastWork
import ch.hofzeit.core.pauseMs
import ch.hofzeit.core.totals
import ch.hofzeit.core.workMs
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------------------------------------------------------------------------------------------
// Start

@Composable
fun HomeScreen(
    tasks: List<Task>,
    running: Session?,
    todayWorkMs: Long,
    todayByTask: Map<String, Long>,
    onTask: (Task) -> Unit,
    onOpenRunning: () -> Unit,
    onToday: () -> Unit,
    onHistory: () -> Unit,
    onTasks: () -> Unit,
) {
    val now = LocalNow.current
    val cur = running?.current
    // Läuft etwas, steht der Knopf «läuft» in der Mitte, sonst die erste Tätigkeit.
    val listState = rememberScalingLazyListState(initialCenterItemIndex = if (cur != null) 0 else 1)
    ScreenScaffold(listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (cur != null) {
                item {
                    val color = if (cur.isPause) PauseGrey else taskColor(cur.color)
                    MenuChip(
                        label = if (cur.isPause) "Pause" else cur.name,
                        secondary = Format.stopwatch(cur.durationMs(now)) + "  ·  läuft",
                        icon = if (cur.isPause) PauseIcon else PlayIcon,
                        onClick = onOpenRunning,
                        color = color,
                    )
                }
                item { ListHeader { Text("Wechseln zu") } }
            } else {
                item { ListHeader { Text("Arbeit starten") } }
            }
            items(tasks.filter { !it.isPause && it.id != cur?.taskId }, key = { it.id }) { task ->
                val today = todayByTask[task.id]?.takeIf { it >= 60_000 }
                TaskChip(task, today?.let { "heute " + Format.hours(it) }, onClick = { onTask(task) })
            }
            item { Spacer(Modifier.height(6.dp)) }
            item { MenuChip("Heute", "Arbeit " + Format.hours(todayWorkMs), Icons.Filled.DateRange, onToday) }
            item { MenuChip("Verlauf", null, Icons.AutoMirrored.Filled.List, onHistory) }
            item { MenuChip("Tätigkeiten", "anpassen", Icons.Filled.Settings, onTasks) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Arbeit läuft

@Composable
fun RunningScreen(
    session: Session,
    todayWorkMs: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSwitch: () -> Unit,
    onEnd: () -> Unit,
) {
    val now = LocalNow.current
    val cur = session.current ?: return
    val color = if (cur.isPause) PauseGrey else taskColor(cur.color)
    val size = roundButtonSize()

    ScreenScaffold {
        CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier.fillMaxSize().padding(3.dp),
            startAngle = 300f,
            endAngle = 240f,
            indicatorColor = color,
            trackColor = Color.Transparent,
            strokeWidth = 5.dp,
        )
        // Feste Anteile der Bildschirmhöhe, damit die Knöpfe auch auf kleinen runden Uhren
        // ganz innerhalb des Rings liegen.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val h = maxHeight
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = h * 0.15f)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (cur.emoji.isNotEmpty()) {
                    Text(cur.emoji, fontSize = 15.sp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    if (cur.isPause) "Pause" else cur.name,
                    style = MaterialTheme.typography.title3,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = h * 0.25f)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    Format.stopwatch(cur.durationMs(now)),
                    style = MaterialTheme.typography.display1.copy(fontFeatureSettings = "tnum", lineHeight = 44.sp),
                    maxLines = 1,
                )
                Text(
                    if (cur.isPause) "danach: " + (session.lastWork?.name ?: "")
                    else "Arbeit heute " + Format.hours(todayWorkMs),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = h * 0.72f - size / 2),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (cur.isPause) {
                    RoundButton(PlayIcon, "Weiter", ButtonDefaults.buttonColors(backgroundColor = HofGreen), size, onResume)
                } else {
                    RoundButton(PauseIcon, "Pause", ButtonDefaults.secondaryButtonColors(), size, onPause)
                }
                RoundButton(
                    SwapIcon, "Wechseln",
                    ButtonDefaults.buttonColors(backgroundColor = chipTint(color), contentColor = Color.White),
                    size, onSwitch,
                )
                RoundButton(
                    StopIcon, "Beenden",
                    ButtonDefaults.buttonColors(backgroundColor = StopRed, contentColor = Color.White),
                    size, onEnd,
                )
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, colors: ButtonColors, size: Dp, onClick: () -> Unit) {
    Button(onClick = onClick, colors = colors, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(size * 0.42f))
    }
}

// ---------------------------------------------------------------------------------------------
// Wechseln

@Composable
fun SwitchScreen(tasks: List<Task>, currentTaskId: String?, onPick: (Task) -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Wechseln zu") } }
            items(tasks.filter { !it.isPause && it.id != currentTaskId }, key = { it.id }) { task ->
                TaskChip(task, null, onClick = { onPick(task) })
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Beenden?

@Composable
fun ConfirmEndScreen(workMs: Long, onConfirm: () -> Unit, onCancel: () -> Unit) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Arbeit beenden?", style = MaterialTheme.typography.title2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                "Arbeit " + Format.hours(workMs),
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize),
                ) { Icon(Icons.Filled.Close, contentDescription = "Abbrechen") }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(backgroundColor = StopRed, contentColor = Color.White),
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize),
                ) { Icon(Icons.Filled.Check, contentDescription = "Beenden") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Zusammenfassung eines Einsatzes / eines Tages

private fun ScalingLazyListScope.totalsBlock(sessions: List<Session>, now: Long, emptyText: String) {
    val work = sessions.workMs(now)
    val pause = sessions.pauseMs(now)
    val totals = sessions.totals(now)
    val all = (work + pause).coerceAtLeast(1)
    item {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Format.hours(work),
                style = MaterialTheme.typography.display3.copy(fontFeatureSettings = "tnum"),
                color = HofGreen,
            )
            Text(
                if (pause > 0) "Arbeit  ·  Pause " + Format.hm(pause) else "Arbeit",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
    }
    if (totals.isEmpty()) {
        item { Text(emptyText, style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center) }
    }
    items(totals, key = { "t-" + it.taskId }) { TotalRow(it, it.ms.toFloat() / all) }
}

@Composable
fun SummaryScreen(session: Session, justFinished: Boolean, onDone: () -> Unit, onDelete: () -> Unit) {
    val listState = rememberScalingLazyListState()
    val now = LocalNow.current
    var confirmDelete by remember { mutableStateOf(false) }
    val date = Format.localDate(session.start)
    ScreenScaffold(listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(if (justFinished) "Gut gemacht!" else Format.dayShort(date)) } }
            item {
                Text(
                    Format.range(session.start, session.end),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
            totalsBlock(listOf(session), now, "")
            item { ListHeader { Text("Ablauf") } }
            items(session.segments.size) { i -> SegmentRow(session.segments[i], now) }
            item { Spacer(Modifier.height(4.dp)) }
            if (justFinished) {
                item { MenuChip("Fertig", null, Icons.Filled.Check, onDone, color = HofGreen) }
            } else {
                item {
                    MenuChip(
                        label = if (confirmDelete) "Wirklich löschen?" else "Löschen",
                        secondary = if (confirmDelete) "nochmals tippen" else null,
                        icon = Icons.Filled.Delete,
                        onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                        color = if (confirmDelete) StopRed else null,
                    )
                }
            }
        }
    }
}

@Composable
fun TodayScreen(sessions: List<Session>) {
    val listState = rememberScalingLazyListState()
    val now = LocalNow.current
    ScreenScaffold(listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Heute") } }
            totalsBlock(sessions, now, "Heute noch nichts erfasst.")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Verlauf

@Composable
fun HistoryScreen(
    history: List<Session>,
    today: LocalDate,
    onOpen: (Session) -> Unit,
    onResend: suspend () -> Int,
) {
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    var sendInfo by remember { mutableStateOf<String?>(null) }
    ScreenScaffold(listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Verlauf") } }
            if (history.isEmpty()) {
                item {
                    Text(
                        "Noch keine Einträge.",
                        style = MaterialTheme.typography.caption1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(history.take(300), key = { it.id }) { s ->
                val main = listOf(s).totals(0).firstOrNull { !it.isPause }
                MenuChip(
                    label = Format.relativeDay(Format.localDate(s.start), today) + "  ·  " + Format.hours(s.workMs(0)),
                    secondary = Format.range(s.start, s.end),
                    icon = null,
                    onClick = { onOpen(s) },
                    color = main?.let { chipTint(taskColor(it.color)) },
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
            item {
                MenuChip(
                    label = "An Handy senden",
                    secondary = sendInfo ?: "alles nochmals",
                    icon = Icons.Filled.Refresh,
                    onClick = {
                        sendInfo = "wird gesendet …"
                        scope.launch { sendInfo = "${onResend()} Einsätze gesendet" }
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tätigkeiten anpassen

@Composable
fun TasksScreen(
    tasks: List<Task>,
    onOpen: (Task) -> Unit,
    onAdd: () -> Unit,
    onRestore: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val missingDefaults = DefaultTasks.work.any { d -> tasks.none { it.id == d.id } }
    ScreenScaffold(listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Tätigkeiten") } }
            item { MenuChip("Neue Tätigkeit", "sprechen/tippen", Icons.Filled.Add, onAdd, color = HofGreen) }
            items(tasks.filter { !it.isPause }, key = { it.id }) { task ->
                TaskChip(task, null, onClick = { onOpen(task) })
            }
            if (missingDefaults) {
                item { MenuChip("Standard zurückholen", "gelöschte wieder anzeigen", Icons.Filled.Refresh, onRestore) }
            }
        }
    }
}

@Composable
fun TaskDetailScreen(task: Task, isFirst: Boolean, onMoveTop: () -> Unit, onRemove: () -> Unit) {
    val listState = rememberScalingLazyListState()
    var confirm by remember { mutableStateOf(false) }
    ScreenScaffold(listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { TaskIcon(task.emoji, task.name, taskColor(task.color), size = 40.dp) }
            item {
                Text(
                    task.name,
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!isFirst) {
                item { MenuChip("Ganz nach oben", null, Icons.Filled.KeyboardArrowUp, onMoveTop) }
            }
            item {
                MenuChip(
                    label = if (confirm) "Wirklich entfernen?" else "Entfernen",
                    secondary = if (confirm) "nochmals tippen" else "Verlauf bleibt",
                    icon = Icons.Filled.Delete,
                    onClick = { if (confirm) onRemove() else confirm = true },
                    color = if (confirm) StopRed else null,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Always-On (Handgelenk unten): schwarz, wenig Pixel, leicht wandernd gegen Einbrennen

@Composable
fun AmbientScreen(running: Session?, now: Long) {
    val cur = running?.current
    val step = ((now / 60_000) % 4).toInt()
    val dx = listOf(0, 3, 0, -3)[step].dp
    val dy = listOf(-3, 0, 3, 0)[step].dp
    val grey = Color(0xFFB0B0B0)
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(Modifier.offset(dx, dy), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Format.clock(now), style = MaterialTheme.typography.display2, color = Color.White)
            Spacer(Modifier.height(4.dp))
            if (cur != null) {
                Text(if (cur.isPause) "Pause" else cur.name, style = MaterialTheme.typography.title3, color = grey)
                Text(
                    Format.hours(cur.durationMs(now)),
                    style = MaterialTheme.typography.title2.copy(fontFeatureSettings = "tnum"),
                    color = grey,
                )
            } else {
                Text("Hofzeit", style = MaterialTheme.typography.title3, color = grey)
            }
        }
    }
}
