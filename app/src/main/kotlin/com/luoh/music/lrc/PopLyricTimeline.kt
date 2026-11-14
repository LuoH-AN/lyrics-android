package com.luoh.music.lrc

import java.text.Bidi
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.min

/** A bounded, immutable plan for ONE sentence. No generated singing timestamps. */
internal data class PopLyricToken(val text: String, val startMs: Long, val endMs: Long)

internal class PopLyricTimeline(val tokens: List<PopLyricToken>, val wordTimed: Boolean) {
    /** Wake only while an entrance/exit is moving, or at the next real word/line boundary. */
    fun nextChangeDelayMs(positionMs: Long, nextLineMs: Long?): Long {
        var delay = nextLineMs?.minus(positionMs)?.coerceAtLeast(1L) ?: 1000L
        if (wordTimed) for (index in tokens.indices) {
            val token = tokens[index]
            val entering = positionMs - (token.startMs - PopLyricMotion.LOOKAHEAD_MS)
            val passing = positionMs - token.endMs
            if (entering in 0 until PopLyricMotion.DURATION_MS || passing in 0 until PopLyricMotion.PASS_DURATION_MS) return 0L
            if (entering < 0L) delay = min(delay, -entering)
            if (passing < 0L) delay = min(delay, -passing)
        }
        return delay.coerceIn(1L, 1000L)
    }

    companion object {
        const val MAX_TOKENS = 64
        const val MAX_ANIMATED_CHARACTERS = 256
        const val MAX_PREVIEW_CHARACTERS = 512

        fun prepare(line: LyricLine, displayText: String = line.text): PopLyricTimeline {
            fun sentence() = PopLyricTimeline(listOf(PopLyricToken(displayText, line.startMs, LyricTiming.lineEnd(line))), false)
            // Preserve shaping for RTL scripts and show translated/untimed/very long text as a sentence.
            if (displayText != line.text || line.words.isEmpty() || line.words.size > MAX_TOKENS ||
                displayText.length > MAX_ANIMATED_CHARACTERS ||
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

/** Bound text shaping work even for an imported sentence with thousands of characters. */
internal fun popLyricPreview(text: String): String {
    if (text.length <= PopLyricTimeline.MAX_PREVIEW_CHARACTERS) return text
    val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT)
    boundaries.setText(text)
    val end = boundaries.preceding(PopLyricTimeline.MAX_PREVIEW_CHARACTERS).coerceAtLeast(0)
    return text.substring(0, end) + "…"
}

/** Lookup once at startup; no springs, trigonometry or per-word animation objects in a frame. */
internal object PopLyricMotion {
    const val DURATION_MS = 600L
    const val PASS_DURATION_MS = 500L
    const val LOOKAHEAD_MS = 150L
    const val FRAME_INTERVAL_NS = 33_333_333L
    const val MAX_SCALE = 1.45f
    private val entrance = FloatArray(129) { index ->
        // Unit-mass spring response: stiffness 200, damping 20, sampled once.
        val seconds = index / 128.0 * DURATION_MS / 1000.0
        (1.0 - exp(-10.0 * seconds) * (cos(10.0 * seconds) + sin(10.0 * seconds)))
            .toFloat().coerceIn(0f, 1.05f)
    }.also { it[it.lastIndex] = 1f }

    fun enter(elapsedMs: Long): Float {
        if (elapsedMs <= 0L) return 0f
        if (elapsedMs >= DURATION_MS) return 1f
        return entrance[(elapsedMs * entrance.lastIndex / DURATION_MS).toInt()]
    }

    fun pass(elapsedMs: Long): Float = (elapsedMs.toFloat() / PASS_DURATION_MS).coerceIn(0f, 1f)
}
