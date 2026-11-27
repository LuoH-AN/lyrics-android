package com.luoh.music.lrc.data.lyrics

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal class LyricMatchMemory(private val prefs: SharedPreferences) {
    fun readRaw(): String = prefs.getString(PREF_KEY, "").orEmpty()

    fun writeFromOverlay(value: String) {
        if (runCatching { JSONArray(value) }.isSuccess) {
            synchronized(prefs) { writeRaw(value) }
        }
    }

    fun entries(): List<JSONObject> {
        val records = runCatching { readArray() }.getOrDefault(JSONArray())
        return (0 until records.length()).mapNotNull { records.optJSONObject(it) }
    }

    fun update(key: String, change: (JSONObject) -> Unit): Boolean = synchronized(prefs) {
        val records = entries()
        val entry = records.find { it.optString("key") == key } ?: return@synchronized false
        change(entry)
        writeRaw(JSONArray(records).toString())
        true
    }

    fun delete(key: String) = synchronized(prefs) {
        writeRaw(JSONArray(entries().filterNot { it.optString("key") == key }).toString())
    }

    fun clear() = synchronized(prefs) { writeRaw("[]") }

    /** Uses the same key as the overlay and preserves the first recorded choice. */
    fun recordAutomaticMatch(
        track: String,
        artist: String,
        album: String,
        durationMs: Long,
        searchRevision: Long,
        payload: LyricPayload,
    ) {
        if (track.isBlank() || payload.source.isBlank() || payload.lyrics.isBlank()) return
        runCatching {
            synchronized(prefs) {
                val normalizedKey = "$track\u0000$artist".trim().lowercase(Locale.ROOT)
                val searchKey = normalizedKey + if (searchRevision > 0L) "\u0000search:$searchRevision" else ""
                val key = "$searchKey|${Math.round(durationMs / 1000.0)}|${payload.source}"
                val records = readArray()
                if ((0 until records.length()).any { records.optJSONObject(it)?.optString("key") == key }) {
                    return@synchronized
                }

                val candidate = JSONObject()
                    .put("lyrics", payload.lyrics)
                    .put("translatedLyrics", payload.translated)
                    .put("wordLyrics", payload.word)
                    .put("duration", durationMs)
                    .put("cover", "")
                    .put("source", payload.source)
                    .put("recordId", payload.recordId)
                    .put("title", track)
                    .put("artist", artist)
                    .put("matchScore", 0)
                val entry = JSONObject()
                    .put("key", key)
                    .put("at", System.currentTimeMillis())
                    .put("title", track)
                    .put("artist", artist)
                    .put("album", album)
                    .put("duration", durationMs)
                    .put("source", payload.source)
                    .put("candidate", candidate)
                    .put("original", candidate)
                    .put("history", JSONArray().put(candidate))
                    .put("needsReview", false)
                    .put("auto", true)
                writeRaw(records.put(entry).toString())
            }
        }
    }

    private fun readArray(): JSONArray = JSONArray(readRaw().ifBlank { "[]" })

    private fun writeRaw(value: String) = prefs.edit().putString(PREF_KEY, value).apply()

    companion object {
        const val PREF_KEY = "match_memory_v2"
    }
}
