package com.luoh.music.lrc

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/** Search aliases belong to the player's original title/artist, never to its clock or display. */
internal object SongSearchCorrections {
    private const val PREFS = "song_search_corrections_v1"
    const val MAX_LENGTH = 300

    data class Search(val title: String, val artist: String, val revision: Long = 0L)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(track: String, artist: String): String = MessageDigest.getInstance("SHA-256")
        .digest("${track.trim()}\u0000${artist.trim()}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun resolve(context: Context, track: String, artist: String): Search {
        val data = runCatching { JSONObject(prefs(context).getString(key(track, artist), "{}").orEmpty()) }
            .getOrNull()
        return Search(data?.optString("title")?.takeIf { it.isNotBlank() } ?: track,
            if (data?.has("title") == true) data.optString("artist") else artist,
            data?.optLong("revision") ?: 0L)
    }

    /** A null title restores the player metadata, while revision invalidates its browser cache. */
    fun save(context: Context, track: String, artist: String, title: String?, searchArtist: String) {
        require(track.isNotBlank())
        require(title == null || (title.isNotBlank() && title.length <= MAX_LENGTH))
        require(title == null || searchArtist.length <= MAX_LENGTH)
        val previous = resolve(context, track, artist)
        val data = JSONObject().put("revision", previous.revision + 1L)
        if (title != null && (title.trim() != track || searchArtist.trim() != artist)) {
            data.put("title", title.trim()).put("artist", searchArtist.trim())
        }
        prefs(context).edit().putString(key(track, artist), data.toString()).apply()
    }
}
