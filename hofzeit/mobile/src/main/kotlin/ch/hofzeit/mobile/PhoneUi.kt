package ch.hofzeit.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.hofzeit.core.Format
import ch.hofzeit.core.Session
import ch.hofzeit.core.TaskTotal
import ch.hofzeit.core.current
import ch.hofzeit.core.pauseMs
import ch.hofzeit.core.totals
import ch.hofzeit.core.workMs
import java.time.LocalDate
import java.time.YearMonth

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B6D1E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBCF094),
    onPrimaryContainer = Color(0xFF0A2100),
    secondaryContainer = Color(0xFFD8E7CA),
    onSecondaryContainer = Color(0xFF131F0D),
    background = Color(0xFFF7F9F1),
    surface = Color(0xFFF7F9F1),
    surfaceVariant = Color(0xFFE0E4D6),
    surfaceContainerLow = Color(0xFFF1F5EA),
    surfaceContainer = Color(0xFFEBEFE4),
    onSurfaceVariant = Color(0xFF43483E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA1D47B),
    onPrimary = Color(0xFF133800),
    primaryContainer = Color(0xFF245105),
    onPrimaryContainer = Color(0xFFBCF094),
    secondaryContainer = Color(0xFF3C4B33),
    onSecondaryContainer = Color(0xFFD8E7CA),
    background = Color(0xFF11140E),
    surface = Color(0xFF11140E),
    surfaceVariant = Color(0xFF43483E),
    surfaceContainerLow = Color(0xFF191D16),
    surfaceContainer = Color(0xFF1D211A),
    onSurfaceVariant = Color(0xFFC3C8BA),
)

@Composable
fun PhoneTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

data class PhoneUiState(
    val sessions: List<Session>,
    val running: Session?,
    val watch: WatchStatus,
    val now: Long,
    val busy: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(
    state: PhoneUiState,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onFetch: () -> Unit,
    onShare: () -> Unit,
    onDelete: (Session) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val today = Format.localDate(state.now)
    val monthSessions = state.sessions.inMonth(month)
    val days = monthSessions.groupBy { Format.localDate(it.start) }.toSortedMap(compareByDescending { it })
    var toDelete by remember { mutableStateOf<Session?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hofzeit", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onFetch) { Icon(Icons.Filled.Refresh, "Von der Uhr holen") }
                    IconButton(onClick = onShare, enabled = monthSessions.isNotEmpty()) {
                        Icon(Icons.Filled.Share, "Monat teilen")
                    }
                },
                colors = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            item { WatchCard(state.watch, state.running, state.now) }
            item {
                MonthSwitcher(
                    month = month,
                    canGoForward = month < YearMonth.from(today),
                    onPrev = { onMonth(month.minusMonths(1)) },
                    onNext = { onMonth(month.plusMonths(1)) },
                )
            }
            item { MonthCard(monthSessions) }
            if (monthSessions.isEmpty()) {
                item {
                    Text(
                        "In diesem Monat sind noch keine Zeiten erfasst. Auf der Uhr eine Arbeit starten " +
                            "und beenden: danach erscheint sie hier.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(days.entries.toList(), key = { it.key.toString() }) { (date, list) ->
                    DayCard(date, today, list.sortedBy { it.start }, onDelete = { toDelete = it })
                }
                item {
                    FilledTonalButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Monat als Tabelle teilen")
                    }
                }
            }
        }
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Einsatz löschen?") },
            text = {
                Text(
                    Format.dayFull(Format.localDate(s.start)) + ", " + Format.range(s.start, s.end) +
                        " (" + Format.hours(s.workMs(0)) + ")\n\nWird nur auf dem Handy gelöscht. " +
                        "Auf der Uhr bleibt er im Verlauf."
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(s); toDelete = null }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun WatchCard(watch: WatchStatus, running: Session?, now: Long) {
    val cur = running?.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (cur != null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (cur != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(Color(cur.color), 12)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Läuft auf der Uhr: " + (if (cur.isPause) "Pause" else cur.emoji + " " + cur.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "seit " + Format.clock(cur.start) + " · " + Format.hours(cur.durationMs(now)) +
                        "  ·  Einsatz " + Format.hours(running?.workMs(now) ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                when (watch) {
                    WatchStatus.Unknown -> "Suche Uhr …"
                    WatchStatus.None -> "Keine Uhr verbunden. Die Zeiten kommen, sobald die Uhr wieder in der Nähe ist."
                    is WatchStatus.Connected -> "Verbunden mit " + watch.names.joinToString()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, canGoForward: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorheriger Monat") }
        Text(
            Format.month(month.atDay(1)),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächster Monat")
        }
    }
}

@Composable
private fun MonthCard(sessions: List<Session>) {
    val work = sessions.workMs(0)
    val pause = sessions.pauseMs(0)
    val days = sessions.map { Format.localDate(it.start) }.distinct().size
    val totals = sessions.totals(0).filter { !it.isPause }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Format.hours(work),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Arbeit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(
                "Pause " + Format.hours(pause) + "  ·  " + days + (if (days == 1) " Tag" else " Tage"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totals.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val max = totals.maxOf { it.ms }.coerceAtLeast(1)
                totals.forEach { TaskBar(it, it.ms.toFloat() / max) }
            }
        }
    }
}

@Composable
private fun TaskBar(total: TaskTotal, fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(total.emoji.ifEmpty { "•" })
            Spacer(Modifier.width(6.dp))
            Text(total.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(Format.hours(total.ms), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp)).background(Color(total.color))
            )
        }
    }
}

@Composable
private fun DayCard(date: LocalDate, today: LocalDate, sessions: List<Session>, onDelete: (Session) -> Unit) {
    var expanded by rememberSaveable(date.toString()) { mutableStateOf(false) }
    val totals = sessions.totals(0)
    val work = sessions.workMs(0)
    val title = when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> Format.dayLong(date)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable { expanded = !expanded }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(Format.hours(work), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Weniger" else "Details",
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            StackedBar(totals)
            Spacer(Modifier.height(6.dp))
            Text(
                // Geschützte Leerzeichen: Symbol, Name und Dauer bleiben beim Umbruch zusammen.
                totals.joinToString("   ") { (it.emoji + " " + it.name + " " + Format.hm(it.ms)).replace(' ', '\u00A0') },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                sessions.forEach { s ->
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Format.range(s.start, s.end) + "  ·  " + Format.hours(s.workMs(0)),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onDelete(s) }) { Icon(Icons.Filled.Delete, "Einsatz löschen") }
                    }
                    s.segments.forEach { seg ->
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Format.clock(seg.start),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(48.dp),
                            )
                            Dot(Color(seg.color), 8)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                seg.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(Format.hm(seg.durationMs(0)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** Ein Balken, aufgeteilt nach Tätigkeiten. */
@Composable
private fun StackedBar(totals: List<TaskTotal>) {
    val sum = totals.sumOf { it.ms }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        totals.forEach { t ->
            val w = t.ms.toFloat() / sum
            if (w > 0f) Box(Modifier.weight(w).fillMaxHeight().background(Color(t.color)))
        }
    }
}

@Composable
private fun Dot(color: Color, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}
