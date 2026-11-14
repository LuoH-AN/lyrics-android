package com.luoh.music.lrc

import java.text.Bidi
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min

/** A bounded, immutable plan for ONE sentence. No generated singing timestamps. */
internal data class PopLyricToken(val text: String, val startMs: Long, val endMs: Long)

internal class PopLyricTimeline(val tokens: List<PopLyricToken>, val wordTimed: Boolean) {
    /** Wake only while an entrance/exit is moving, or at the next real word/line boundary. */
    fun nextChangeDelayMs(positionMs: Long, nextLineMs: Long?): Long {
        var delay = nextLineMs?.minus(positionMs)?.coerceAtLeast(1L) ?: 1000L
        if (wordTimed) for (index in tokens.indices) {
            val token = tokens[index]
            val entering = positionMs - token.startMs
            val passing = positionMs - token.endMs
            if (entering in 0 until PopLyricMotion.DURATION_MS || passing in 0 until PopLyricMotion.DURATION_MS) return 0L
            if (entering < 0L) delay = min(delay, -entering)
            if (passing < 0L) delay = min(delay, -passing)
        }
        return delay.coerceIn(1L, 1000L)
    }

    companion object {
        const val MAX_TOKENS = 64

        fun prepare(line: LyricLine, displayText: String = line.text): PopLyricTimeline {
            fun sentence() = PopLyricTimeline(listOf(PopLyricToken(displayText, line.startMs, LyricTiming.lineEnd(line))), false)
            // Preserve shaping for RTL scripts and show translated/untimed/very long text as a sentence.
            if (displayText != line.text || line.words.isEmpty() || line.words.size > MAX_TOKENS ||
                Bidi.requiresBidi(displayText.toCharArray(), 0, displayText.length)) return sentence()
            val source = line.words.joinToString("") { it.text }
            if (source.trim() != displayText) return sentence()
            val leading = source.length - source.trimStart().length
            val end = leading + displayText.length
            var cursor = 0
            val tokens = ArrayList<PopLyricToken>(line.words.size)
            for ((index, word) in line.words.withIndex()) {
                if (word.startMs < 0L || (index > 0 && word.startMs < line.words[index - 1].startMs)) return sentence()
                val from = cursor.coerceAtLeast(leading)
                cursor += word.text.length
                val until = cursor.coerceAtMost(end)
                if (until <= from) continue
                val duration = word.durationMs.takeIf { it > 0L }
                    ?: line.words.getOrNull(index + 1)?.let { it.startMs - word.startMs }?.takeIf { it > 0L }
                    ?: 1L
                if (duration > Long.MAX_VALUE - word.startMs) return sentence()
                tokens += PopLyricToken(source.substring(from, until), word.startMs, word.startMs + duration)
            }
            return if (tokens.isEmpty()) sentence() else PopLyricTimeline(tokens, true)
        }
    }
}

/** Lookup once at startup; no springs, trigonometry or per-word animation objects in a frame. */
internal object PopLyricMotion {
    const val DURATION_MS = 320L
    const val FRAME_INTERVAL_NS = 33_333_333L
    const val MAX_SCALE = 1.16f
    private val entrance = FloatArray(129) { index ->
        val t = index / 128f
        (1.0 - exp(-7.0 * t) * cos(9.0 * t)).toFloat().coerceIn(0f, 1.06f)
    }.also { it[it.lastIndex] = 1f }

    fun enter(elapsedMs: Long): Float {
        if (elapsedMs <= 0L) return 0f
        if (elapsedMs >= DURATION_MS) return 1f
        return entrance[(elapsedMs * entrance.lastIndex / DURATION_MS).toInt()]
    }

    fun pass(elapsedMs: Long): Float = (elapsedMs.toFloat() / DURATION_MS).coerceIn(0f, 1f)
}
