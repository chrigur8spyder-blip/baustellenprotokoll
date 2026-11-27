package ch.hofzeit.mobile

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ch.hofzeit.core.Csv
import ch.hofzeit.core.Format
import ch.hofzeit.core.Session
import ch.hofzeit.core.pauseMs
import ch.hofzeit.core.totals
import ch.hofzeit.core.workMs
import java.io.File
import java.time.YearMonth

fun List<Session>.inMonth(month: YearMonth): List<Session> =
    filter { it.end != null && YearMonth.from(Format.localDate(it.start)) == month }

/** Kurzer Text für den Mail-Text beim Teilen. */
fun monthSummaryText(sessions: List<Session>, month: YearMonth): String {
    val days = sessions.map { Format.localDate(it.start) }.distinct().size
    val sb = StringBuilder()
    sb.append("Hofzeit – ").append(Format.month(month.atDay(1))).append("\n\n")
    sb.append("Arbeit: ").append(Format.hours(sessions.workMs(0))).append(" an ").append(days)
        .append(if (days == 1) " Tag" else " Tagen").append("\n")
    sb.append("Pause: ").append(Format.hours(sessions.pauseMs(0))).append("\n\n")
    for (t in sessions.totals(0).filter { !it.isPause }) {
        sb.append(t.name).append(": ").append(Format.hours(t.ms)).append("\n")
    }
    return sb.toString().trimEnd()
}

/** Zwei CSV-Dateien (Tagesübersicht und alle Einträge) über das Teilen-Menü verschicken. */
fun shareMonth(context: Context, all: List<Session>, month: YearMonth) {
    val sessions = all.inMonth(month)
    val dir = File(context.cacheDir, "export").apply {
        mkdirs()
        listFiles()?.forEach { it.delete() }
    }
    val base = "Hofzeit_%d-%02d".format(month.year, month.monthValue)
    val days = File(dir, "${base}_Tage.csv").apply { writeText(Csv.days(sessions)) }
    val entries = File(dir, "${base}_Eintraege.csv").apply { writeText(Csv.entries(sessions)) }

    val authority = context.packageName + ".files"
    val uris = arrayListOf(
        FileProvider.getUriForFile(context, authority, days),
        FileProvider.getUriForFile(context, authority, entries),
    )
    val title = "Hofzeit – " + Format.month(month.atDay(1))
    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "text/csv"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, monthSummaryText(sessions, month))
        clipData = ClipData.newRawUri(title, uris[0]).apply { addItem(ClipData.Item(uris[1])) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Tabelle teilen"))
}
