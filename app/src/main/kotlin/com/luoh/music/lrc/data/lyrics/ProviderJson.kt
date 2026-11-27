package com.luoh.music.lrc.data.lyrics

import org.json.JSONArray

internal fun JSONArray?.joinNames(key: String): String {
    if (this == null) return ""
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.optString(key)?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }.joinToString("/")
}

internal const val MAX_LYRIC_CANDIDATES_PER_QUERY = 4
