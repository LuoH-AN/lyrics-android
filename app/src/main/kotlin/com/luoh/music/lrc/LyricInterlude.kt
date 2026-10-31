package com.luoh.music.lrc

/** A timed instrumental gap; it never changes the source lyric timestamps. */
internal data class LyricInterlude(val afterLine: Int, val startMs: Long, val endMs: Long) {
    fun progress(positionMs: Long): Float =
        ((positionMs - startMs).toDouble() / (endMs - startMs)).coerceIn(0.0, 1.0).toFloat()

    companion object {
        fun at(document: LyricDocument, positionMs: Long, durationMs: Long): LyricInterlude? {
            if (!document.timed || document.lines.isEmpty()) return null
            val previous = LyricTiming.activeIndex(document.lines, positionMs)
            val start = document.lines.getOrNull(previous)?.let(LyricTiming::lineEnd) ?: 0L
            val end = document.lines.getOrNull(previous + 1)?.startMs ?: durationMs
            val minimum = if (previous < 0) 4000L else 4500L
            if (end - start <= minimum || positionMs < start || positionMs >= end) return null
            return LyricInterlude(previous, start, end)
        }
    }
}
