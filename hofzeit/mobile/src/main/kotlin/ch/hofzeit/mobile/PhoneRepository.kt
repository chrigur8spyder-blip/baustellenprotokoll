package ch.hofzeit.mobile

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import ch.hofzeit.core.Codec
import ch.hofzeit.core.PhoneArchive
import ch.hofzeit.core.Session
import ch.hofzeit.core.SyncPaths
import ch.hofzeit.core.delete
import ch.hofzeit.core.merge
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class PhoneApp : Application() {
    val repository by lazy { PhoneRepository(this) }
}

val Context.repository: PhoneRepository get() = (applicationContext as PhoneApp).repository

sealed interface WatchStatus {
    data object Unknown : WatchStatus
    data object None : WatchStatus
    data class Connected(val names: List<String>) : WatchStatus
}

/**
 * Archiv auf dem Handy. Die Uhr legt jeden abgeschlossenen Einsatz in den «Data Layer»,
 * von dort wird er hier abgeholt und dauerhaft gespeichert, auch wenn er auf der Uhr gelöscht wird.
 */
class PhoneRepository(context: Context) {
    private val app = context.applicationContext
    private val file = File(app.filesDir, "archiv.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    private val _archive = MutableStateFlow(load())
    val archive: StateFlow<PhoneArchive> = _archive.asStateFlow()

    private val _running = MutableStateFlow<Session?>(null)
    /** Arbeit, die gerade auf der Uhr läuft. */
    val running: StateFlow<Session?> = _running.asStateFlow()

    private val _watch = MutableStateFlow<WatchStatus>(WatchStatus.Unknown)
    val watch: StateFlow<WatchStatus> = _watch.asStateFlow()

    /** Liest alles, was die Uhr übertragen hat. */
    suspend fun refresh() {
        val sessions = mutableListOf<Session>()
        var running: Session? = null
        try {
            val buffer = Wearable.getDataClient(app).dataItems.await()
            try {
                for (item in buffer) {
                    val path = item.uri.path ?: continue
                    if (path != SyncPaths.RUNNING && !path.startsWith(SyncPaths.SESSION_PREFIX)) continue
                    val json = DataMapItem.fromDataItem(item).dataMap.getString(SyncPaths.KEY_JSON) ?: continue
                    val session = Codec.decodeSession(json) ?: continue
                    if (path == SyncPaths.RUNNING) running = session else sessions += session
                }
            } finally {
                buffer.release()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Hofzeit", "Daten von der Uhr nicht lesbar", e)
        }
        _running.value = running
        val merged = _archive.value.merge(sessions)
        if (merged !== _archive.value) {
            _archive.value = merged
            save(merged)
        }
        updateWatchStatus()
    }

    /** Bittet die Uhr, alles nochmals zu schicken. Gibt die Anzahl erreichter Uhren zurück. */
    suspend fun requestFromWatch(): Int {
        return try {
            val nodes = Wearable.getNodeClient(app).connectedNodes.await()
            val messages = Wearable.getMessageClient(app)
            for (node in nodes) messages.sendMessage(node.id, SyncPaths.RESEND, ByteArray(0)).await()
            _watch.value = if (nodes.isEmpty()) WatchStatus.None else WatchStatus.Connected(nodes.map { it.displayName })
            nodes.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Hofzeit", "Uhr nicht erreichbar", e)
            _watch.value = WatchStatus.None
            0
        }
    }

    private suspend fun updateWatchStatus() {
        _watch.value = try {
            val nodes = Wearable.getNodeClient(app).connectedNodes.await()
            if (nodes.isEmpty()) WatchStatus.None else WatchStatus.Connected(nodes.map { it.displayName })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WatchStatus.None
        }
    }

    /** Löscht einen Einsatz auf dem Handy (auf der Uhr bleibt er im Verlauf). */
    fun delete(id: String) {
        val updated = _archive.value.delete(id)
        _archive.value = updated
        save(updated)
        scope.launch(io) {
            runCatching {
                val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(SyncPaths.session(id)).build()
                Wearable.getDataClient(app).deleteDataItems(uri).await()
            }
        }
    }

    private fun load(): PhoneArchive {
        if (!file.exists()) return PhoneArchive()
        val text = runCatching { file.readText() }.getOrNull() ?: return PhoneArchive()
        return Codec.decodeArchive(text) ?: PhoneArchive().also {
            file.renameTo(File(app.filesDir, "archiv.defekt-" + System.currentTimeMillis() + ".json"))
        }
    }

    private fun save(archive: PhoneArchive) {
        scope.launch(io) {
            withContext(Dispatchers.IO) {
                val tmp = File(app.filesDir, "archiv.json.tmp")
                tmp.writeText(Codec.encode(archive))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }
}
