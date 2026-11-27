package ch.hofzeit.wear.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import ch.hofzeit.core.Format
import ch.hofzeit.core.Segment
import ch.hofzeit.core.Task
import ch.hofzeit.core.TaskTotal

/** Aktuelle Zeit (Millisekunden), wird jede Sekunde neu gesetzt, im Always-On-Modus jede Minute. */
val LocalNow = compositionLocalOf { System.currentTimeMillis() }

val HofGreen = Color(0xFF9CCC65)
val StopRed = Color(0xFFE5534B)
val PauseGrey = Color(0xFF90A4AE)
private val ChipBase = Color(0xFF202124)

@Composable
fun HofzeitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = HofGreen,
            primaryVariant = Color(0xFF689F38),
            secondary = Color(0xFFC5E1A5),
            secondaryVariant = Color(0xFF33691E),
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            error = StopRed,
        ),
        content = content,
    )
}

fun taskColor(color: Long): Color = Color(color)

fun chipTint(color: Color): Color = color.copy(alpha = 0.30f).compositeOver(ChipBase)

/** Grösse der runden Knöpfe: auf grossen Uhren grösser (besser mit Handschuhen). */
@Composable
fun roundButtonSize(): Dp {
    val width = LocalConfiguration.current.screenWidthDp
    return (width * 0.25f).coerceIn(46f, 60f).dp
}

/** Spürbares Vibrieren, auch bei der Arbeit. */
fun Context.buzz() {
    val vibrator = getSystemService(Vibrator::class.java) ?: return
    if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
}

@Composable
fun ScreenScaffold(
    listState: ScalingLazyListState? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        timeText = { TimeText() },
        vignette = if (listState != null) {
            { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
        } else null,
        positionIndicator = if (listState != null) {
            { PositionIndicator(scalingLazyListState = listState) }
        } else null,
    ) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
fun TaskIcon(emoji: String, name: String, color: Color, size: Dp = 30.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (emoji.isNotEmpty()) {
            Text(emoji, fontSize = (size.value * 0.52f).sp)
        } else {
            Text(
                name.take(1).uppercase(),
                fontSize = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
fun TaskChip(task: Task, secondary: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = taskColor(task.color)
    Chip(
        onClick = onClick,
        label = { Text(task.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (secondary != null) {
            { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null,
        icon = { TaskIcon(task.emoji, task.name, color) },
        colors = ChipDefaults.chipColors(backgroundColor = chipTint(color), contentColor = Color.White),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun MenuChip(
    label: String,
    secondary: String?,
    icon: ImageVector?,
    onClick: () -> Unit,
    color: Color? = null,
) {
    Chip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (secondary != null) {
            { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null,
        icon = if (icon != null) {
            { Icon(icon, contentDescription = null, modifier = Modifier.size(ChipDefaults.IconSize)) }
        } else null,
        colors = if (color != null) {
            ChipDefaults.chipColors(backgroundColor = color, contentColor = Color.Black)
        } else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Eine Zeile «🥛 Melken 1:05» mit farbigem Balken darunter. */
@Composable
fun TotalRow(total: TaskTotal, fraction: Float) {
    val color = taskColor(total.color)
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(total.emoji.ifEmpty { "•" }, fontSize = 13.sp)
            Spacer(Modifier.width(5.dp))
            Text(
                total.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.caption1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                Format.hm(total.ms),
                style = MaterialTheme.typography.caption1.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

/** «06:02  🥛 Melken  0:43» */
@Composable
fun SegmentRow(segment: Segment, now: Long) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Format.clock(segment.start),
            style = MaterialTheme.typography.caption2.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(taskColor(segment.color)))
        Spacer(Modifier.width(5.dp))
        Text(
            segment.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.caption2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            Format.hm(segment.durationMs(now)),
            style = MaterialTheme.typography.caption2.copy(fontFeatureSettings = "tnum"),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Eigene Symbole (die Standard-Symbole haben kein Pause/Stopp/Wechseln)

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

val PauseIcon: ImageVector = icon("pause") {
    path(fill = SolidColor(Color.White)) {
        moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
        moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
    }
}

val PlayIcon: ImageVector = icon("play") {
    path(fill = SolidColor(Color.White)) {
        moveTo(7f, 4.5f); lineTo(20f, 12f); lineTo(7f, 19.5f); close()
    }
}

val StopIcon: ImageVector = icon("stop") {
    path(fill = SolidColor(Color.White)) {
        moveTo(6f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(6f, 18f); close()
    }
}

val SwapIcon: ImageVector = icon("swap") {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2.4f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(4f, 8f); lineTo(19f, 8f)
        moveTo(15f, 4f); lineTo(19f, 8f); lineTo(15f, 12f)
        moveTo(20f, 16f); lineTo(5f, 16f)
        moveTo(9f, 12f); lineTo(5f, 16f); lineTo(9f, 20f)
    }
}
