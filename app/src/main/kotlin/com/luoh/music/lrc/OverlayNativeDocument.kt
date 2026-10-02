package com.luoh.music.lrc

import org.json.JSONObject

/** Parsed output from the retained matching engine; all rendering stays native. */
data class OverlayNativeDocument(
    val requestId: Int,
    val key: String,
    val track: String,
    val artist: String,
    val durationMs: Long,
    val message: String,
    val document: LyricDocument
) {
    companion object {
        fun parse(payload: String): OverlayNativeDocument? = runCatching {
            val root = JSONObject(payload)
            val rows = root.optJSONArray("lines")
            var timed = true
            val lines = (0 until (rows?.length() ?: 0)).mapNotNull { index ->
                val row = rows?.optJSONObject(index) ?: return@mapNotNull null
                val text = row.optString("text")
                if (text.isBlank()) return@mapNotNull null
                val start = row.optLong("t", 0L).coerceAtLeast(0L)
                val duration = row.optLong("duration", 0L).coerceAtLeast(0L)
                if (row.optBoolean("plain")) timed = false
                val tokens = row.optJSONArray("words")
                val words = (0 until (tokens?.length() ?: 0)).mapNotNull { wordIndex ->
                    val word = tokens?.optJSONObject(wordIndex) ?: return@mapNotNull null
                    val value = word.optString("text")
                    if (value.isEmpty()) null else LyricWord(
                        startMs = word.optLong("start", start).coerceAtLeast(0L),
                        durationMs = word.optLong("duration", 1L).coerceAtLeast(1L),
                        text = value
                    )
                }
                LyricLine(
                    startMs = start,
                    text = text,
                    translation = row.optString("translation"),
                    endMs = if (duration > 0L) start + duration else null,
                    words = words
                )
            }
            OverlayNativeDocument(
                requestId = root.getInt("requestId"),
                key = root.optString("key"),
                track = root.optString("track"),
                artist = root.optString("artist"),
                durationMs = root.optLong("durationMs", 0L).coerceAtLeast(0L),
                message = root.optString("message"),
                document = LyricDocument(lines, timed)
            )
        }.getOrNull()
    }
}

/** Boundary rules shared by the compact view and its regression tests. */
object CompactLyricSelection {
    fun activeIndex(document: LyricDocument, positionMs: Long, durationMs: Long): Int {
        if (document.lines.isEmpty()) return -1
        if (!document.timed) {
            if (durationMs <= 0L) return 0
            val progress = ((positionMs.coerceAtLeast(0L).toDouble() / durationMs - .025) / .95).coerceIn(0.0, 1.0)
            return (progress * document.lines.size).toInt().coerceIn(document.lines.indices)
        }
        var low = 0
        var high = document.lines.lastIndex
        var active = 0
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (document.lines[middle].startMs <= positionMs + 90L) {
                active = middle
                low = middle + 1
            } else high = middle - 1
        }
        return active
    }

    fun contextIndices(count: Int, active: Int, before: Int, after: Int): IntRange {
        if (active !in 0 until count) return IntRange.EMPTY
        return (active - before.coerceIn(0, 2)).coerceAtLeast(0)..
            (active + after.coerceIn(0, 2)).coerceAtMost(count - 1)
    }
}
