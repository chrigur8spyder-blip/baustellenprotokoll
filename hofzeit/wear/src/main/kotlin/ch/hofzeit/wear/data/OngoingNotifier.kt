package ch.hofzeit.wear.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import ch.hofzeit.core.Format
import ch.hofzeit.core.Session
import ch.hofzeit.core.current
import ch.hofzeit.wear.MainActivity
import ch.hofzeit.wear.R

/**
 * Zeigt die laufende Arbeit als «Laufende Aktivität» an: kleines Symbol auf dem Zifferblatt,
 * Antippen führt zurück in die App, wie bei einem Workout in Samsung Health.
 * Die Zeit zählt das System selbst weiter, die App muss dafür nicht laufen.
 */
class OngoingNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.channel_name))
                .setVibrationEnabled(false)
                .setShowBadge(false)
                .build()
        )
    }

    private fun allowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun show(session: Session) {
        val seg = session.current ?: return cancel()
        if (!allowed()) return

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (seg.isPause) "Pause" else seg.name

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_ongoing)
            .setContentTitle(title)
            .setContentText("seit " + Format.clock(seg.start))
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(seg.start)
            .setShowWhen(true)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Die Stoppuhr im Status rechnet mit der Zeit seit dem Einschalten der Uhr.
        val zero = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - seg.start)
        val status = Status.Builder()
            .addTemplate("#name# · #time#")
            .addPart("name", Status.TextPart(title))
            .addPart("time", Status.StopwatchPart(zero))
            .build()

        OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_ongoing)
            .setTouchIntent(open)
            .setStatus(status)
            .build()
            .apply(context)

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    companion object {
        const val CHANNEL = "laufend"
        const val NOTIFICATION_ID = 1
    }
}
