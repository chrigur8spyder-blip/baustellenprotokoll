package ch.hofzeit.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Codec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(s: Session): String = json.encodeToString(s)
    fun decodeSession(text: String): Session? = runCatching { json.decodeFromString<Session>(text) }.getOrNull()

    fun encode(s: WatchState): String = json.encodeToString(s)
    fun decodeWatchState(text: String): WatchState? = runCatching { json.decodeFromString<WatchState>(text) }.getOrNull()

    fun encode(h: History): String = json.encodeToString(h)
    fun decodeHistory(text: String): History? = runCatching { json.decodeFromString<History>(text) }.getOrNull()

    fun encode(a: PhoneArchive): String = json.encodeToString(a)
    fun decodeArchive(text: String): PhoneArchive? = runCatching { json.decodeFromString<PhoneArchive>(text) }.getOrNull()
}
