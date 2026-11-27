package ch.hofzeit.wear.data

import android.content.Context
import android.net.Uri
import android.util.Log
import ch.hofzeit.core.Codec
import ch.hofzeit.core.Session
import ch.hofzeit.core.SyncPaths
import ch.hofzeit.wear.repository
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * Überträgt abgeschlossene Einsätze und die laufende Arbeit ans Handy.
 * Ist das Handy gerade nicht verbunden, holt Android das später automatisch nach.
 */
class PhoneSync(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    suspend fun putSession(session: Session, force: Boolean = false) = attempt {
        val request = PutDataMapRequest.create(SyncPaths.session(session.id)).apply {
            dataMap.putString(SyncPaths.KEY_JSON, Codec.encode(session))
            // Sonst gilt ein unveränderter Eintrag nicht als neu und wird nicht nochmals geschickt.
            if (force) dataMap.putLong(SyncPaths.KEY_SENT_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    suspend fun putRunning(session: Session?) = attempt {
        if (session == null) {
            dataClient.deleteDataItems(uri(SyncPaths.RUNNING)).await()
        } else {
            val request = PutDataMapRequest.create(SyncPaths.RUNNING).apply {
                dataMap.putString(SyncPaths.KEY_JSON, Codec.encode(session))
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
        }
    }

    suspend fun deleteSession(id: String) = attempt {
        dataClient.deleteDataItems(uri(SyncPaths.session(id))).await()
    }

    private fun uri(path: String): Uri =
        Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(path).build()

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Hofzeit", "Übertragung ans Handy fehlgeschlagen", e)
        }
    }
}

/** Das Handy bittet um alle Einsätze (Knopf «Von Uhr holen» in der Handy-App). */
class PhoneRequestService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == SyncPaths.RESEND) {
            runBlocking { applicationContext.repository.resendAll() }
        }
    }
}
