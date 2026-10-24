package com.luoh.music.lrc

import android.content.Context
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/** Persistent provider results shared by the home page and overlay. Custom lyrics stay separate. */
internal class SharedLyricsCache(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("home_lyric_cache_v1", Context.MODE_PRIVATE)

    private fun key(track: String, artist: String) = "${track.trim()}\u0000${artist.trim()}".lowercase(Locale.ROOT)

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

    private fun decode(raw: String?, expectedDuration: Long, oldFormat: Boolean = false): DirectLyricsRepository.Result? {
        if (raw == null) return null
        return runCatching {
            val data = JSONObject(raw)
            val saved = data.optLong("at", 0L)
            if (saved <= 0 || now() - saved > TTL_MS) return null
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
                .put("result", result.toJson()).toString())
            entries.entries.sortedBy {
                runCatching { JSONObject(it.value as String).optLong("at") }.getOrDefault(0L)
            }.take((entries.size + 1 - MAX_ENTRIES).coerceAtLeast(0)).forEach { editor.remove(it.key) }
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
