package com.luoh.music.lrc.data.lyrics

import android.content.SharedPreferences
import org.json.JSONObject
import java.security.MessageDigest

internal class LyricOffsetMemory(private val prefs: SharedPreferences) {
    data class Song(val identity: String, val source: String, val title: String, val artist: String) {
        val key: String by lazy {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$identity\u0000$source".toByteArray(Charsets.UTF_8))
                .take(12)
                .joinToString("") { "%02x".format(it) }
            ENTRY_PREFIX + digest
        }
    }

    data class Entry(
        val key: String,
        val title: String,
        val artist: String,
        val source: String,
        val offsetMs: Int,
        val updatedAt: Long,
    )

    fun activeSong(): Song? = synchronized(prefs) {
        val identity = prefs.getString(ACTIVE_IDENTITY, "").orEmpty()
        val source = prefs.getString(ACTIVE_SOURCE, "").orEmpty()
        if (identity.isBlank() || source.isBlank()) return@synchronized null
        Song(identity, source, prefs.getString(ACTIVE_TITLE, "").orEmpty(), prefs.getString(ACTIVE_ARTIST, "").orEmpty())
    }

    fun rememberActiveSong(song: Song?, displayedOffsetMs: Int? = null) = synchronized(prefs) {
        val editor = prefs.edit()
            .putString(ACTIVE_IDENTITY, song?.identity.orEmpty())
            .putString(ACTIVE_SOURCE, song?.source.orEmpty())
            .putString(ACTIVE_TITLE, song?.title.orEmpty())
            .putString(ACTIVE_ARTIST, song?.artist.orEmpty())
        displayedOffsetMs?.let { editor.putInt(DISPLAYED_OFFSET, it) }
        editor.apply()
    }

    fun displayedOffset(): Int = prefs.getInt(DISPLAYED_OFFSET, 0).coerceIn(MIN_MS, MAX_MS)

    fun read(song: Song): Int = prefs.getInt(song.key, 0).coerceIn(MIN_MS, MAX_MS)

    // Keep the slider's value available immediately while its index write runs on IO.
    fun writeImmediateOffset(song: Song, offsetMs: Int) = synchronized(prefs) {
        prefs.edit().putInt(song.key, offsetMs).putInt(DISPLAYED_OFFSET, offsetMs).apply()
    }

    fun save(song: Song?, offsetMs: Int, updateDisplayedOffset: Boolean = false) = synchronized(prefs) {
        val offset = offsetMs.coerceIn(MIN_MS, MAX_MS)
        val editor = prefs.edit()
        if (updateDisplayedOffset) editor.putInt(DISPLAYED_OFFSET, offset)
        if (song != null) {
            val index = readIndex()
            val id = song.key.removePrefix(ENTRY_PREFIX)
            if (offset == 0) {
                editor.remove(song.key)
                index.remove(id)
            } else {
                editor.putInt(song.key, offset)
                index.put(id, JSONObject()
                    .put("identity", song.identity)
                    .put("source", song.source)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("offsetMs", offset)
                    .put("updatedAt", System.currentTimeMillis()))
            }
            editor.putString(INDEX_KEY, index.toString())
        }
        editor.apply()
    }

    fun entries(): List<Entry> {
        val index = readIndex()
        return index.keys().asSequence().mapNotNull { id ->
            val value = index.optJSONObject(id) ?: return@mapNotNull null
            val offset = value.optInt("offsetMs", 0)
            if (offset == 0) return@mapNotNull null
            val identity = value.optString("identity").split('\u0000', limit = 2)
            Entry(
                ENTRY_PREFIX + id,
                value.optString("title").ifBlank { identity.first() },
                value.optString("artist").ifBlank { identity.getOrNull(1).orEmpty() },
                value.optString("source"),
                offset,
                value.optLong("updatedAt", 0L),
            )
        }.sortedByDescending { it.updatedAt }.toList()
    }

    fun delete(key: String, resetDisplayedOffset: Boolean = false): Boolean = synchronized(prefs) {
        if (!key.startsWith(ENTRY_PREFIX)) return@synchronized false
        val index = readIndex()
        index.remove(key.removePrefix(ENTRY_PREFIX))
        val editor = prefs.edit().remove(key).putString(INDEX_KEY, index.toString())
        if (resetDisplayedOffset) editor.putInt(DISPLAYED_OFFSET, 0)
        editor.apply()
        true
    }

    fun clear() = synchronized(prefs) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(ENTRY_PREFIX) }.forEach(editor::remove)
        editor.remove(INDEX_KEY).putInt(DISPLAYED_OFFSET, 0).apply()
    }

    private fun readIndex(): JSONObject = runCatching {
        JSONObject(prefs.getString(INDEX_KEY, "{}").orEmpty())
    }.getOrDefault(JSONObject())

    companion object {
        const val MIN_MS = -5_000
        const val MAX_MS = 5_000
        const val INDEX_KEY = "lyric_offset_index_v2"
        private const val ENTRY_PREFIX = "lyric_offset_entry_v2:"
        private const val DISPLAYED_OFFSET = "lyric_offset_ms_v1"
        private const val ACTIVE_IDENTITY = "active_lyric_identity_v1"
        private const val ACTIVE_SOURCE = "active_lyric_source_v1"
        private const val ACTIVE_TITLE = "active_lyric_title_v1"
        private const val ACTIVE_ARTIST = "active_lyric_artist_v1"
    }
}
