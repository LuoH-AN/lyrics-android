package com.luoh.music.lrc

import android.content.Context
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/** Persistent provider results shared by the home page and overlay. Custom lyrics stay separate. */
internal class SharedLyricsCache(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("home_lyric_cache_v1", Context.MODE_PRIVATE)
    private val metadata = context.getSharedPreferences("lyrics_cache_metadata", Context.MODE_PRIVATE)
    val epoch: Long get() = metadata.getLong("epoch", 0L)

    private fun key(track: String, artist: String) = "${track.trim()}\u0000${artist.trim()}".lowercase(Locale.ROOT)

    data class Entry(val title: String, val artist: String, val result: DirectLyricsRepository.Result, val savedAt: Long)

    fun clear() = synchronized(prefs) {
        prefs.edit().clear().apply()
        legacy.edit().clear().apply()
        metadata.edit().putLong("epoch", epoch + 1).apply()
    }

    /** Expired downloads remain editable until they are evicted or explicitly cleared. */
    fun all(): List<Entry> {
        val current = prefs.all
        val entries = current.entries.mapNotNull { (identity, value) ->
            (value as? String)?.let { editingEntry(identity, it) }
        }
        return (entries + legacyEditingEntries(current.keys)).sortedByDescending { it.savedAt }
    }

    private fun editingEntry(identity: String, raw: String, oldFormat: Boolean = false): Entry? {
        val parts = identity.split('\u0000', limit = 2)
        if (parts.size != 2) return null
        val result = decode(raw, 0L, oldFormat = oldFormat, ignoreExpiry = true) ?: return null
        val data = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return Entry(if (oldFormat) parts[0] else data.optString("track", parts[0]),
            if (oldFormat) parts[1] else data.optString("artist", parts[1]), result, data.optLong("at"))
    }

    /** Reading older downloads for correction must not renew their playback TTL. */
    private fun legacyEditingEntries(currentKeys: Set<String>): List<Entry> = legacy.all.entries.mapNotNull { (identity, value) ->
        val song = identity.substringBeforeLast('\u0000', "")
        val parts = song.split('\u0000', limit = 2)
        if (parts.size != 2 || key(parts[0], parts[1]) in currentKeys) return@mapNotNull null
        (value as? String)?.let { editingEntry(song, it, oldFormat = true) }
    }.sortedByDescending { it.savedAt }.distinctBy { key(it.title, it.artist) }

    fun readForEditing(track: String, artist: String): DirectLyricsRepository.Result? {
        val raw = prefs.getString(key(track, artist), null)
        return if (raw != null) decode(raw, 0L, ignoreExpiry = true)
        else legacyEditingEntries(prefs.all.keys).firstOrNull { key(it.title, it.artist) == key(track, artist) }?.result
    }

    fun read(track: String, artist: String, album: String = "", durationMs: Long = 0L): DirectLyricsRepository.Result? {
        val identity = key(track, artist)
        // Once migrated, stale legacy entries must not replace a newer recording or expired result.
        val current = prefs.getString(identity, null)
        if (current != null) return decode(current, durationMs)
        // Retain previously downloaded home lyrics when upgrading; album metadata can arrive late.
        val old = legacy.getString("$track\u0000$artist\u0000$album", null)
            ?: legacy.all.entries.firstOrNull { it.key.substringBeforeLast('\u0000')
                .split('\u0000').let { parts -> parts.size == 2 && key(parts[0], parts[1]) == identity } }?.value as? String
        val migrated = decode(old, durationMs, oldFormat = true) ?: return null
        write(track, artist, migrated, savedAt = runCatching { JSONObject(old!!).optLong("at") }.getOrDefault(now()))
        return migrated
    }

    /** Serialize the same song's requests across Activity and Service instances, rechecking after waiting. */
    fun resolve(track: String, artist: String, album: String, durationMs: Long, forceNetwork: Boolean = false,
        fetch: () -> DirectLyricsRepository.Result): DirectLyricsRepository.Result {
        val identity = key(track, artist)
        return synchronized(locks[(identity.hashCode() and Int.MAX_VALUE) % locks.size]) {
            if (!forceNetwork) read(track, artist, album, durationMs)?.let { return@synchronized it }
            val result = fetch()
            write(track, artist, result.copy(durationMs = result.durationMs.takeIf { it > 0L } ?: durationMs))
            result
        }
    }

    private fun decode(raw: String?, expectedDuration: Long, oldFormat: Boolean = false, ignoreExpiry: Boolean = false): DirectLyricsRepository.Result? {
        if (raw == null) return null
        return runCatching {
            val data = JSONObject(raw)
            val saved = data.optLong("at", 0L)
            if (saved <= 0 || (!ignoreExpiry && now() - saved > TTL_MS)) return null
            val payload = if (oldFormat) data else data.optJSONObject("result") ?: return null
            val result = candidate(payload, oldFormat)
            if (result.lyrics.isBlank() || result.source == CustomLyricsStore.SOURCE) return null
            if (expectedDuration > 0 && result.durationMs > 0 &&
                abs(expectedDuration - result.durationMs) > maxOf(3_000L, expectedDuration / 30)) return null
            val alternatives = payload.optJSONArray("alternatives")
            result.copy(alternatives = (0 until (alternatives?.length() ?: 0)).mapNotNull { index ->
                alternatives?.optJSONObject(index)?.let { candidate(it, false) }
                    ?.takeIf { it.lyrics.isNotBlank() && (it.source != result.source || it.recordId != result.recordId) }
            })
        }.getOrNull()
    }

    private fun candidate(data: JSONObject, oldFormat: Boolean) = DirectLyricsRepository.Result(
        lyrics = data.optString("lyrics"),
        translatedLyrics = data.optString(if (oldFormat) "translated" else "translatedLyrics"),
        wordLyrics = data.optString(if (oldFormat) "word" else "wordLyrics"),
        durationMs = data.optLong("duration"), cover = data.optString("cover"),
        source = data.optString("source"), recordId = data.optString("recordId"),
        title = data.optString("title"), artist = data.optString("artist"), score = data.optInt("matchScore"),
    )

    private fun write(track: String, artist: String, result: DirectLyricsRepository.Result, savedAt: Long = now()) {
        if (result.lyrics.isBlank() || result.source == CustomLyricsStore.SOURCE) return
        synchronized(prefs) {
            val identity = key(track, artist)
            val entries = prefs.all.filterKeys { it != identity }
            val editor = prefs.edit().putString(identity, JSONObject().put("at", savedAt)
                .put("track", track.trim()).put("artist", artist.trim())
                .put("result", result.toJson(includePrimary = false)).toString())
            val excess = entries.size + 1 - MAX_ENTRIES
            if (excess > 0) {
                entries.entries.sortedBy {
                    runCatching { JSONObject(it.value as String).optLong("at") }.getOrDefault(0L)
                }.take(excess).forEach { editor.remove(it.key) }
            }
            editor.apply()
        }
    }

    companion object {
        const val PREFS = "shared_lyrics_cache_v1"
        private const val TTL_MS = 14 * 86_400_000L
        private const val MAX_ENTRIES = 60
        private val locks = Array(32) { Any() }
    }
}
