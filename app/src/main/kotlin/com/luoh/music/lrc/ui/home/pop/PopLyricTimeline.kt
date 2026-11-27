package com.luoh.music.lrc.ui.home.pop

import com.luoh.music.lrc.lyrics.LyricLine
import com.luoh.music.lrc.lyrics.LyricTiming
import java.text.Bidi
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** A bounded, immutable plan for ONE sentence. No generated singing timestamps. */
internal data class PopLyricToken(val text: String, val startMs: Long, val endMs: Long, val visualDelayMs: Long = 0L)

internal class PopLyricTimeline(val tokens: List<PopLyricToken>, val wordTimed: Boolean,
    val startMs: Long = tokens.firstOrNull()?.startMs ?: 0L,
    val endMs: Long = tokens.maxOfOrNull { it.endMs } ?: startMs) {
    /** Wake only while an entrance/exit is moving, or at the next real word/line boundary. */
    fun nextChangeDelayMs(positionMs: Long, nextLineMs: Long?, style: PopLyricsStyle = PopLyricsStyle()): Long {
        var delay = nextLineMs?.minus(positionMs)?.coerceAtLeast(1L) ?: 1000L
        if (wordTimed) for (index in tokens.indices) {
            val token = tokens[index]
            val entering = positionMs - (token.startMs - style.lookaheadMs + token.visualDelayMs)
            val passing = positionMs - token.endMs
            if (entering in 0 until style.entranceMs.toLong() || passing in 0 until PopLyricMotion.PASS_DURATION_MS) return 0L
            if (entering < 0L) delay = min(delay, -entering)
            if (passing < 0L) delay = min(delay, -passing)
        }
        return delay.coerceIn(1L, 1000L)
    }

    companion object {
        const val MAX_TOKENS = 64
        const val MAX_ANIMATED_CHARACTERS = 256
        const val MAX_PREVIEW_CHARACTERS = 512

        fun prepare(line: LyricLine, displayText: String = line.text, reveal: String = "word"): PopLyricTimeline {
            fun sentence() = PopLyricTimeline(listOf(PopLyricToken(displayText, line.startMs, LyricTiming.lineEnd(line))), false, line.startMs, LyricTiming.lineEnd(line))
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
            if (tokens.isEmpty()) return sentence()
            if (reveal != "character") return PopLyricTimeline(tokens, true, line.startMs, LyricTiming.lineEnd(line))
            val characters = ArrayList<PopLyricToken>()
            for (token in tokens) {
                // Keep emoji/ZWJ sequences intact; BreakIterator preserves ordinary combining marks.
                val pieces = if (token.text.any { Character.isSurrogate(it) || it == '\u200d' }) listOf(token.text)
                    else popCharacterPieces(token.text)
                for ((index, piece) in pieces.withIndex()) {
                    characters += token.copy(text = piece, visualDelayMs = (index * 25L).coerceAtMost(100L))
                    if (characters.size > MAX_TOKENS) return PopLyricTimeline(tokens, true, line.startMs, LyricTiming.lineEnd(line))
                }
            }
            return PopLyricTimeline(characters, true, line.startMs, LyricTiming.lineEnd(line))
        }
    }
}

private fun popCharacterPieces(text: String): List<String> {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(text)
    val pieces = ArrayList<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        pieces += text.substring(start, end)
        start = end
        end = iterator.next()
    }
    return pieces
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
    const val PASS_DURATION_MS = 280L
    const val LOOKAHEAD_MS = 90L
    // No wall-time gate: withFrameNanos follows every display frame, including 120 Hz.
    const val FRAME_INTERVAL_NS = 0L
    const val MAX_SCALE = 1.55f
    private val entrance = FloatArray(257) { index ->
        // Smooth attack, one restrained rebound, and zero velocity at the settled end.
        val p = index / 256.0
        val spring = 1.0 - exp(-8.0 * p) * (cos(8.0 * p) + sin(8.0 * p))
        val finish = PopVisualMotion.travel(((p - .72) / .28).toFloat())
        (spring + (1.0 - spring) * finish).toFloat().coerceIn(0f, 1.05f)
    }.also { it[it.lastIndex] = 1f }

    fun enter(elapsedMs: Long, durationMs: Long = DURATION_MS): Float {
        if (elapsedMs <= 0L) return 0f
        if (elapsedMs >= durationMs) return 1f
        val sample = elapsedMs.toFloat() * entrance.lastIndex / durationMs
        val index = sample.toInt().coerceAtMost(entrance.lastIndex - 1)
        return entrance[index] + (entrance[index + 1] - entrance[index]) * (sample - index)
    }

    fun pass(elapsedMs: Long, durationMs: Long = PASS_DURATION_MS): Float {
        val progress = (elapsedMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        return progress * progress * (3f - 2f * progress)
    }
}
