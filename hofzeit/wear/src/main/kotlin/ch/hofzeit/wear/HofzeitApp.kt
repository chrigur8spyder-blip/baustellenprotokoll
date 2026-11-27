package ch.hofzeit.wear

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.hofzeit.wear.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HofzeitApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, appScope)
        repository.refreshOngoing()
    }
}

val Context.repository: Repository get() = (applicationContext as HofzeitApp).repository

/** Nach einem Neustart der Uhr die Anzeige «Arbeit läuft» wieder einblenden. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) context.repository.refreshOngoing()
    }
}
