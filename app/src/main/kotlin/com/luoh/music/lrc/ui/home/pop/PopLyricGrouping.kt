package com.luoh.music.lrc.ui.home.pop

import android.icu.text.BreakIterator
import java.util.Locale

internal data class PopLyricGroup(val from: Int, val until: Int)
internal data class PopLyricGroups(val units: List<PopLyricGroup>, val penalties: FloatArray,
    val tightBoundaries: BooleanArray)

/** ICU's installed dictionaries define lexical boundaries. No application word list or invented timing. */
internal fun groupPopLyrics(tokens: List<PopLyricToken>, semantic: Boolean = true): PopLyricGroups {
    val penalties = popLyricBreakPenalties(tokens)
    val tight = BooleanArray(tokens.size + 1)
    if (tokens.isEmpty()) return PopLyricGroups(emptyList(), penalties, tight)
    val text = tokens.joinToString("") { it.text }
    val boundaries = if (semantic) {
        val iterator = BreakIterator.getWordInstance(Locale.ROOT)
        iterator.setText(text)
        val result = HashSet<Int>()
        var offset = iterator.first()
        while (offset != BreakIterator.DONE) { result.add(offset); offset = iterator.next() }
        result
    } else null
    var offset = tokens.first().text.length
    for (index in 1 until tokens.size) {
        val previous = tokens[index - 1]
        val next = tokens[index]
        val adjacent = previous.text.lastOrNull()?.isWhitespace() != true && next.text.firstOrNull()?.isWhitespace() != true
        // A real pause wins over an inferred lexical group; punctuation remains attached.
        val lexical = boundaries != null && offset !in boundaries && adjacent && next.startMs - previous.endMs < 120L
        val sameWord = previous.startMs == next.startMs && previous.endMs == next.endMs
        tight[index] = lexical || penalties[index] >= 40f || (sameWord && boundaries == null)
        if (lexical) penalties[index] = 40f
        offset += next.text.length
    }
    val starts = listOf(0) + (1 until tokens.size).filter { penalties[it] < 40f }
    return PopLyricGroups(starts.mapIndexed { index, start -> PopLyricGroup(start, starts.getOrNull(index + 1) ?: tokens.size) },
        penalties, tight)
}

internal data class PopLayoutTokens(val tokens: List<PopLyricToken>, val sourceIndices: List<Int>)

/** Source providers sometimes call an entire CJK phrase one word. Split its layout,
 * keeping the exact source clock on every piece; ICU keeps graphemes and Latin words intact. */
internal fun popLayoutTokens(timeline: PopLyricTimeline): PopLayoutTokens {
    // Untimed and RTL sentences retain native shaping and the sentence fallback.
    if (!timeline.wordTimed) return PopLayoutTokens(timeline.tokens, timeline.tokens.indices.toList())
    val tokens = ArrayList<PopLyricToken>()
    val sources = ArrayList<Int>()
    val iterator = BreakIterator.getWordInstance(Locale.ROOT)
    for ((index, token) in timeline.tokens.withIndex()) {
        iterator.setText(token.text)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            tokens += token.copy(text = token.text.substring(start, end))
            sources += index
            if (tokens.size > PopLyricTimeline.MAX_TOKENS) {
                return PopLayoutTokens(timeline.tokens, timeline.tokens.indices.toList())
            }
            start = end
            end = iterator.next()
        }
    }
    return PopLayoutTokens(tokens, sources)
}
