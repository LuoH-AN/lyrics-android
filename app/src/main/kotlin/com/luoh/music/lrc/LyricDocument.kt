package com.luoh.music.lrc

/** Shared, platform-independent lyric data for the home page and compact overlay. */
data class LyricWord(val startMs: Long, val durationMs: Long, val text: String)

data class LyricLine(
    val startMs: Long,
    val text: String,
    val translation: String = "",
    val endMs: Long? = null,
    val words: List<LyricWord> = emptyList()
)

data class LyricDocument(val lines: List<LyricLine>, val timed: Boolean)

object LyricTiming {
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lines[middle].startMs <= positionMs) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }

    fun lineEnd(line: LyricLine): Long = line.endMs?.takeIf { it > line.startMs }
        ?: (line.startMs + (line.text.length * 400L + 1500L).coerceIn(3000L, 8000L))

    /** Character boundary plus the sung fraction of the current word; drawing measures actual glyph widths. */
    fun sungCharacters(line: LyricLine, positionMs: Long): Float {
        if (line.words.isEmpty()) return 0f
        val total = line.words.sumOf { it.text.length }.coerceAtLeast(1)
        var done = 0f
        for ((index, word) in line.words.withIndex()) {
            if (positionMs < word.startMs) break
            val duration = word.durationMs.takeIf { it > 0L }
                ?: line.words.getOrNull(index + 1)?.let { it.startMs - word.startMs }?.takeIf { it > 0L }
                ?: 500L
            val fraction = ((positionMs - word.startMs).toDouble() / duration).coerceIn(0.0, 1.0)
            done += word.text.length * fraction.toFloat()
            if (fraction < 1.0) break
        }
        return (done / total * line.text.length).coerceIn(0f, line.text.length.toFloat())
    }
}
