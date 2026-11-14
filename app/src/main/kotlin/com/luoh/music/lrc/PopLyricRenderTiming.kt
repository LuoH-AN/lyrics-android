package com.luoh.music.lrc

import kotlin.math.min

/** Only visual windows change. Parser start/end times remain untouched. */
internal data class PopLyricRenderTiming(val transitionMs: Int, val entranceMs: LongArray,
    val lookaheadMs: LongArray, val passStartMs: LongArray, val passMs: LongArray) {
    fun nextChangeDelayMs(tokens: List<PopLyricToken>, now: Long, nextBoundaryMs: Long?): Long {
        var delay = nextBoundaryMs?.minus(now)?.coerceAtLeast(1L) ?: 1000L
        for (index in tokens.indices) {
            val entering = now - (tokens[index].startMs - lookaheadMs[index] + tokens[index].visualDelayMs)
            val passing = now - passStartMs[index]
            if (entering in 0 until entranceMs[index] || passing in 0 until passMs[index]) return 0L
            if (entering < 0) delay = min(delay, -entering)
            if (passing < 0) delay = min(delay, -passing)
        }
        return delay.coerceIn(1L, 1000L)
    }
}

internal fun popLyricRenderTiming(timeline: PopLyricTimeline, style: PopLyricsStyle,
    nextLineMs: Long? = null): PopLyricRenderTiming {
    val available = ((nextLineMs ?: timeline.endMs).coerceAtMost(timeline.endMs) - timeline.startMs).coerceAtLeast(1L)
    val fast = style.adaptiveTiming && available < 800L
    val instant = style.adaptiveTiming && available <= 100L
    val transition = if (instant) 0 else if (fast) (available / 3).toInt().coerceIn(16, 160) else 320
    val entrances = LongArray(timeline.tokens.size) { index ->
        val tokenDuration = (timeline.tokens[index].endMs - timeline.tokens[index].startMs).coerceAtLeast(1)
        when {
            instant -> 0L
            fast -> min(style.entranceMs.toLong(), min(available / 2, tokenDuration.coerceAtMost(1000L) * 2)).coerceAtLeast(16L)
            style.adaptiveTiming && tokenDuration < 250L -> min(style.entranceMs.toLong(), tokenDuration.coerceAtMost(1000L) * 2).coerceAtLeast(16L)
            else -> style.entranceMs.toLong()
        }
    }
    val lookahead = LongArray(timeline.tokens.size) { if (instant) 0L else
        min(style.lookaheadMs.toLong(), if (fast) available / 4 else style.lookaheadMs.toLong()) }
    val passes = LongArray(timeline.tokens.size) { if (instant) 0L else if (fast) min(150L, available / 3).coerceAtLeast(16L) else 500L }
    val passStarts = LongArray(timeline.tokens.size) { index ->
        val end = timeline.tokens[index].endMs
        end + min(style.textHoldMs.toLong(), ((nextLineMs ?: timeline.endMs) - end).coerceAtLeast(0L))
    }
    return PopLyricRenderTiming(transition, entrances, lookahead, passStarts, passes)
}

/** Access-order cache, bounded to the current, upcoming and outgoing sentence. */
internal class PopLyricCache<K, V>(private val capacity: Int = 3) {
    private val entries = LinkedHashMap<K, V>(capacity, .75f, true)
    var builds: Int = 0
        private set
    val size get() = entries.size
    fun getOrPut(key: K, build: () -> V): V = entries[key] ?: build().also {
        builds++
        entries[key] = it
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
}
