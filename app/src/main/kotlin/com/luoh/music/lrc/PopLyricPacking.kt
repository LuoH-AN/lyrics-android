package com.luoh.music.lrc

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

internal data class PopLyricSlot(val x: Float, val y: Float)
internal data class PopLyricPacking(val slots: List<PopLyricSlot>, val scale: Float, val rowStarts: List<Int> = emptyList())

/** Prefer source word boundaries and real pauses; this never changes singing timestamps. */
internal fun popLyricBreakPenalties(tokens: List<PopLyricToken>): FloatArray {
    val penalties = FloatArray(tokens.size + 1)
    val closing = "，。！？；：、,.!?;:)]}）】》〉」』”’"
    val opening = "([{（【《〈「『“‘"
    val particles = setOf("的", "了", "着", "吗", "呢", "吧", "啊", "地", "得")
    val prefixes = setOf("把", "被", "在", "从", "向", "给", "让", "和", "与", "到")
    for (index in 1 until tokens.size) {
        val previous = tokens[index - 1]
        val next = tokens[index]
        val left = previous.text.trim()
        val right = next.text.trim()
        penalties[index] = when {
            right.firstOrNull()?.let { it in closing } == true || left.lastOrNull()?.let { it in opening } == true -> 40f
            previous.startMs == next.startMs && previous.endMs == next.endMs -> 8f
            previous.text.lastOrNull()?.isLetter() == true && previous.text.last() < '\u0080' &&
                next.text.firstOrNull()?.isLetter() == true && next.text.first() < '\u0080' -> 8f
            left.lastOrNull()?.let { it in "。！？!?；;" } == true -> -1f
            left.lastOrNull()?.let { it in "，、,:：" } == true -> -.6f
            next.startMs - previous.endMs >= 120L -> -.5f
            right in particles || left in prefixes -> 2f
            else -> .15f
        }
    }
    return penalties
}

/** Balanced, bounded line breaking is cached per sentence/viewport, never run in a draw frame. */
internal fun packPopLyrics(widths: FloatArray, heights: FloatArray, width: Float, height: Float, gap: Float,
    layoutStyle: String = "staggered", maxScale: Float = PopLyricMotion.MAX_SCALE, rotationDegrees: Int = 20,
    breakPenalties: FloatArray? = null): PopLyricPacking {
    require(widths.size == heights.size && widths.size <= PopLyricTimeline.MAX_TOKENS)
    require(breakPenalties == null || breakPenalties.size == widths.size + 1)
    if (widths.isEmpty() || width <= 0f || height <= 0f) return PopLyricPacking(emptyList(), 1f)
    val count = widths.size
    val rotationReach = (sin(rotationDegrees * 1.15 * Math.PI / 180) * maxScale).toFloat()
    val reservedWidths = FloatArray(count) { widths[it] * maxScale + heights[it] * rotationReach + gap }
    val reservedHeights = FloatArray(count) { heights[it] * maxScale + widths[it] * rotationReach + gap }
    val typicalHeight = reservedHeights.sorted()[count / 2]
    // Wide landscape stages still read as a phrase, rather than a screen-long ribbon.
    val rowLimit = min(width, typicalHeight * 7.5f).coerceAtLeast(1f)
    val budget = rowLimit / .88f
    val sums = FloatArray(count + 1)
    for (index in widths.indices) sums[index + 1] = sums[index] + reservedWidths[index]
    val protectedStart = IntArray(count)
    for (index in 1 until count) protectedStart[index] =
        if ((breakPenalties?.get(index) ?: 0f) >= 40f) protectedStart[index - 1] else index
    var rowStarts: List<Int> = emptyList()
    // An indivisible punctuation group may exceed the width budget. It must still
    // produce a row (scaled to fit), rather than require more rows than can exist.
    val groupCount = 1 + (1 until count).count { protectedStart[it] == it }
    val minimumRows = ceil(sums[count] / budget).toInt().coerceIn(1, groupCount)
    for (rows in minimumRows..groupCount) {
        val target = sums[count] / rows
        val costs = Array(rows + 1) { FloatArray(count + 1) { Float.POSITIVE_INFINITY } }
        val previous = Array(rows + 1) { IntArray(count + 1) { -1 } }
        costs[0][0] = 0f
        for (row in 1..rows) for (end in row..count) {
            for (start in end - 1 downTo row - 1) {
                if (start > 0 && (breakPenalties?.get(start) ?: 0f) >= 40f) continue
                val rowWidth = sums[end] - sums[start]
                if (rowWidth > budget && end - start > 1 && protectedStart[end - 1] != start) break
                if (!costs[row - 1][start].isFinite()) continue
                val deviation = (rowWidth - target) / target.coerceAtLeast(1f)
                val overflow = ((rowWidth - rowLimit) / rowLimit).coerceAtLeast(0f)
                val orphan = if (rows > 1 && rowWidth < target * .5f) .9f else 0f
                val cost = costs[row - 1][start] + deviation * deviation + overflow * overflow * 2f + orphan +
                    (breakPenalties?.get(start) ?: 0f) * .08f
                if (cost < costs[row][end]) { costs[row][end] = cost; previous[row][end] = start }
            }
        }
        if (previous[rows][count] >= 0) {
            val starts = ArrayList<Int>(rows)
            var end = count
            for (row in rows downTo 1) { end = previous[row][end]; starts.add(end) }
            rowStarts = starts.asReversed().toList()
            break
        }
    }
    val rowWidths = rowStarts.mapIndexed { index, start -> sums[rowStarts.getOrNull(index + 1) ?: count] - sums[start] }
    val rowHeights = rowStarts.mapIndexed { index, start ->
        (start until (rowStarts.getOrNull(index + 1) ?: count)).maxOf { reservedHeights[it] }
    }
    val totalHeight = rowHeights.sum()
    val totalWidth = rowWidths.maxOrNull() ?: width
    val fit = min(1f, min(rowLimit / totalWidth.coerceAtLeast(1f), height / totalHeight.coerceAtLeast(1f)))
    val slots = ArrayList<PopLyricSlot>(count)
    var top = (height - totalHeight * fit) / 2f
    val indent = min(width * .06f, typicalHeight * .35f) * fit
    for (row in rowStarts.indices) {
        val slack = (width - rowWidths[row] * fit).coerceAtLeast(0f)
        val offset = when {
            rowStarts.size == 1 || layoutStyle == "centered" -> 0f
            layoutStyle == "stairs" -> (row.toFloat() / (rowStarts.size - 1) * 2f - 1f) * indent
            row % 2 == 0 -> -indent * .6f
            else -> indent * .6f
        }
        var left = (slack / 2f + offset).coerceIn(0f, slack)
        val until = rowStarts.getOrNull(row + 1) ?: count
        for (index in rowStarts[row] until until) {
            slots += PopLyricSlot(left + reservedWidths[index] * fit / 2f, top + rowHeights[row] * fit / 2f)
            left += reservedWidths[index] * fit
        }
        top += rowHeights[row] * fit
    }
    return PopLyricPacking(slots, fit, rowStarts)
}
