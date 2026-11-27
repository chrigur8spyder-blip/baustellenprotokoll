package ch.hofzeit.wear.data

import ch.hofzeit.core.Codec
import ch.hofzeit.core.History
import ch.hofzeit.core.WatchState
import java.io.File

/**
 * Speichert als JSON-Dateien. Geschrieben wird erst in eine Hilfsdatei und dann umbenannt,
 * damit ein leerer Akku mitten im Schreiben nichts kaputt macht.
 */
class Storage(private val dir: File) {
    private val stateFile = File(dir, "state.json")
    private val historyFile = File(dir, "history.json")

    fun loadState(): WatchState = load(stateFile, Codec::decodeWatchState) ?: WatchState()

    fun loadHistory(): History = load(historyFile, Codec::decodeHistory) ?: History()

    fun saveState(state: WatchState) = write(stateFile, Codec.encode(state))

    fun saveHistory(history: History) = write(historyFile, Codec.encode(history))

    private fun <T> load(file: File, decode: (String) -> T?): T? {
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val value = decode(text)
        if (value == null) {
            // Nicht lesbar: zur Sicherheit aufbewahren statt beim nächsten Speichern überschreiben.
            file.renameTo(File(dir, file.nameWithoutExtension + ".defekt-" + System.currentTimeMillis() + ".json"))
        }
        return value
    }

    private fun write(file: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
