package com.luoh.music.lrc.ui.home.pop

internal data class PopLyricPage(val from: Int, val until: Int)

/** Font size and orientation affect packing, never how long a sung phrase stays on stage. */
internal fun paginatePopLyrics(tokens: List<PopLyricToken>, penalties: FloatArray,
    pageLength: Int): List<PopLyricPage> = paginatePopLyrics(tokens, penalties,
    FloatArray(tokens.size), Float.MAX_VALUE, pageLength)

/** Page boundaries need a real new onset. Never divide simultaneous syllables or
 * discard the end of a held note just to meet a character budget. */
internal fun paginatePopLyrics(tokens: List<PopLyricToken>, penalties: FloatArray,
    widths: FloatArray, widthBudget: Float, pageLength: Int): List<PopLyricPage> {
    if (tokens.isEmpty()) return emptyList()
    val count = tokens.size
    val length = FloatArray(count + 1)
    val width = FloatArray(count + 1)
    val latestEnd = LongArray(count + 1)
    for (index in tokens.indices) {
        length[index + 1] = length[index] + popLyricReadingLength(tokens[index].text)
        width[index + 1] = width[index] + widths[index]
        latestEnd[index + 1] = maxOf(latestEnd[index], tokens[index].endMs)
    }
    fun boundary(index: Int): Boolean = index == 0 || index == count ||
        (penalties[index] < 40f && tokens[index].startMs > tokens[index - 1].startMs &&
            latestEnd[index] <= tokens[index].startMs)
    val boundaries = (0..count).filter(::boundary)
    val costs = FloatArray(count + 1) { Float.POSITIVE_INFINITY }
    val previous = IntArray(count + 1) { -1 }
    costs[0] = 0f
    for ((endIndex, end) in boundaries.withIndex()) {
        if (end == 0) continue
        for (startIndex in endIndex - 1 downTo 0) {
            val start = boundaries[startIndex]
            val units = length[end] - length[start]
            val extent = width[end] - width[start]
            // A single real clock/lexical group must remain renderable even when oversized.
            val indivisible = startIndex == endIndex - 1
            if (!indivisible && (units > pageLength || extent > widthBudget)) break
            val fullness = units / pageLength.coerceAtLeast(1)
            val duration = (tokens[end - 1].endMs - tokens[start].startMs).coerceAtLeast(1L)
            val quick = if (duration < 600L) (600L - duration) / 600f else 0f
            val overflow = (extent / widthBudget.coerceAtLeast(1f) - 1f).coerceAtLeast(0f)
            val cost = costs[start] + 1f + (1f - fullness) * (1f - fullness) +
                quick * 2f + overflow * overflow + (if (start == 0) 0f else penalties[start] * .7f)
            if (cost < costs[end]) { costs[end] = cost; previous[end] = start }
        }
    }
    val pages = ArrayList<PopLyricPage>()
    var end = count
    while (end > 0) {
        val start = previous[end]
        if (start < 0) return listOf(PopLyricPage(0, count))
        pages += PopLyricPage(start, end)
        end = start
    }
    return pages.asReversed()
}

/** Latin words take roughly half the visual space of CJK characters. */
private fun popLyricReadingLength(text: String): Float {
    var length = 0f
    var index = 0
    while (index < text.length) {
        val code = text.codePointAt(index)
        val kind = Character.getType(code)
        length += when {
            kind == Character.NON_SPACING_MARK.toInt() || code == 0x200d -> 0f
            Character.isWhitespace(code) -> .25f
            code < 128 -> .5f
            else -> 1f
        }
        index += Character.charCount(code)
    }
    return length.coerceAtLeast(.25f)
}

internal fun activePopLyricPage(pages: List<PopLyricPage>, tokens: List<PopLyricToken>, now: Long): Int =
    pages.indexOfLast { tokens[it.from].startMs <= now }.coerceAtLeast(0)

internal data class PopPhraseHandoff(val startMs: Long, val durationMs: Long)
internal data class PopPhraseWindow(val current: Int, val retiring: Int?)

/** A completed phrase stays readable until the next real sung onset. Its retirement
 * uses playback time, so pause, speed changes and seeking cannot leave stale text. */
internal fun popPhraseHandoffs(pages: List<PopLyricPage>, timeline: PopLyricTimeline, transitionMs: Int = 360): List<PopPhraseHandoff> =
    pages.mapIndexed { index, page ->
        val start = timeline.tokens[page.from].startMs
        val next = pages.getOrNull(index + 1)?.let { timeline.tokens[it.from].startMs } ?: timeline.endMs
        val available = (next - start).coerceAtLeast(1L)
        PopPhraseHandoff(start, if (index == 0 || available <= 100L) 0L else minOf(transitionMs.toLong(), available / 2))
    }

internal fun popPhraseWindow(pages: List<PopLyricPage>, timeline: PopLyricTimeline,
    handoffs: List<PopPhraseHandoff>, now: Long, animate: Boolean): PopPhraseWindow {
    val current = activePopLyricPage(pages, timeline.tokens, now)
    val handoff = handoffs[current]
    val retiring = (current - 1).takeIf { animate && current > 0 && now - handoff.startMs < handoff.durationMs }
    return PopPhraseWindow(current, retiring)
}
