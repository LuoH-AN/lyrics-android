package com.luoh.music.lrc

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
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
            else -> .15f
        }
    }
    return penalties
}

internal data class PopGlyphExtent(val width: Float, val height: Float)

internal fun popLyricRowLimit(width: Float, typicalHeight: Float, layoutStyle: String, lineWidthPercent: Int): Float {
    val composed = layoutStyle != "centered"
    // Apply the preference AFTER the viewport cap, so it also works on phones.
    return (min(width * (if (composed) .88f else 1f),
        typicalHeight * (if (composed) 5.8f else 7.5f)) * lineWidthPercent / 100f)
        .coerceIn(1f, width.coerceAtLeast(1f))
}

/** The maximum rectangle reached by this glyph's actual angle sweep, computed only during layout. */
internal fun popGlyphExtent(width: Float, height: Float, scale: Float, angleDegrees: Float): PopGlyphExtent {
    val angle = Math.toRadians(kotlin.math.abs(angleDegrees).toDouble()).coerceAtMost(Math.PI / 2)
    fun reach(a: Float, b: Float): Float {
        val critical = atan2(b.toDouble(), a.toDouble())
        return (if (critical <= angle) hypot(a.toDouble(), b.toDouble()) else
            max(a.toDouble(), a * cos(angle) + b * sin(angle))).toFloat() * scale
    }
    return PopGlyphExtent(reach(width, height), reach(height, width))
}

/** Phrase-aware composition is cached per sentence/viewport, never run in a draw frame. */
internal fun packPopLyrics(widths: FloatArray, heights: FloatArray, width: Float, height: Float, gap: Float,
    layoutStyle: String = "staggered", maxScale: Float = PopLyricMotion.MAX_SCALE, rotationDegrees: Int = 20,
    breakPenalties: FloatArray? = null, extents: List<PopGlyphExtent>? = null,
    boundaryGaps: FloatArray? = null, rowGap: Float = gap, indentPercent: Int = 100,
    lineWidthPercent: Int = 100, rowMotionReach: Float = 0f, maxFitScale: Float = 1f,
    horizontalMotionReach: Float = 0f, layoutSeed: Long = 0L, anchorY: Float = .5f,
    fillViewport: Boolean = false): PopLyricPacking {
    val layout = PopVisualMotion.layout(layoutStyle, layoutSeed)
    require(widths.size == heights.size && widths.size <= PopLyricTimeline.MAX_TOKENS)
    require(breakPenalties == null || breakPenalties.size == widths.size + 1)
    require(extents == null || extents.size == widths.size)
    require(boundaryGaps == null || boundaryGaps.size == widths.size + 1)
    if (widths.isEmpty() || width <= 0f || height <= 0f) return PopLyricPacking(emptyList(), 1f)
    val count = widths.size
    val rotationReach = (sin(rotationDegrees * 1.15 * Math.PI / 180) * maxScale).toFloat()
    val reservedWidths = FloatArray(count) { extents?.get(it)?.width ?: (widths[it] * maxScale + heights[it] * rotationReach) }
    val reservedHeights = FloatArray(count) {
        (extents?.get(it)?.height ?: (heights[it] * maxScale + widths[it] * rotationReach)) + rowMotionReach * 2
    }
    val gaps = FloatArray(count) { if (it == 0) 0f else boundaryGaps?.get(it) ?: gap }
    val typicalHeight = reservedHeights.sorted()[count / 2]
    // Wide landscape stages still read as a phrase, rather than a screen-long ribbon.
    val composed = layout != "centered"
    val rowLimit = if (fillViewport) (width * lineWidthPercent / 100f).coerceIn(1f, width)
        else popLyricRowLimit(width, typicalHeight, layout, lineWidthPercent)
    val budget = rowLimit / .88f
    val sums = FloatArray(count + 1)
    for (index in widths.indices) sums[index + 1] = sums[index] + reservedWidths[index] + gaps[index]
    fun rowWidth(start: Int, end: Int) = sums[end] - sums[start] - gaps[start]
    val protectedStart = IntArray(count)
    for (index in 1 until count) protectedStart[index] =
        if ((breakPenalties?.get(index) ?: 0f) >= 40f) protectedStart[index - 1] else index
    var rowStarts: List<Int> = emptyList()
    // An indivisible punctuation group may exceed the width budget. It must still
    // produce a row (scaled to fit), rather than require more rows than can exist.
    val groupCount = 1 + (1 until count).count { protectedStart[it] == it }
    val minimumRows = ceil(sums[count] / budget).toInt().coerceIn(1, groupCount)
    var bestScore = -1f
    // Compare complete compositions, rather than stopping at the first row count that fits.
    // Four rows bound the work and keep individual phrases readable even on tall phones.
    val candidates = if (fillViewport) 1..minOf(groupCount, 4) else minimumRows..groupCount
    for (rows in candidates) {
        val target = sums[count] / rows
        // Alternating phrase lengths create a readable rhythm; centered keeps balanced lines.
        val weights = FloatArray(rows) { row -> when {
            !composed || rows == 1 -> 1f
            layout == "stairs" -> .78f + .44f * row / (rows - 1)
            layout == "wave" -> 1f + sin(row * 1.35f + PopVisualMotion.choice(layoutSeed, 8)) * .25f
            row % 2 == 0 -> .78f
            else -> 1.22f
        } }
        val averageWeight = weights.average().toFloat()
        val costs = Array(rows + 1) { FloatArray(count + 1) { Float.POSITIVE_INFINITY } }
        val previous = Array(rows + 1) { IntArray(count + 1) { -1 } }
        costs[0][0] = 0f
        for (row in 1..rows) for (end in row..count) {
            for (start in end - 1 downTo row - 1) {
                if (start > 0 && (breakPenalties?.get(start) ?: 0f) >= 40f) continue
                val rowWidth = rowWidth(start, end)
                if (!fillViewport && rowWidth > budget && end - start > 1 && protectedStart[end - 1] != start) break
                if (!costs[row - 1][start].isFinite()) continue
                val rowTarget = target * weights[row - 1] / averageWeight
                val deviation = (rowWidth - rowTarget) / rowTarget.coerceAtLeast(1f)
                val overflow = if (fillViewport) 0f else ((rowWidth - rowLimit) / rowLimit).coerceAtLeast(0f)
                val orphan = if (rows > 1 && rowWidth < target * .5f)
                    (1f - rowWidth / (target * .5f).coerceAtLeast(1f)) * 1.6f else 0f
                val cost = costs[row - 1][start] + deviation * deviation + overflow * overflow * 2f + orphan +
                    (breakPenalties?.get(start) ?: 0f) * .08f
                if (cost < costs[row][end]) { costs[row][end] = cost; previous[row][end] = start }
            }
        }
        if (previous[rows][count] >= 0) {
            val starts = ArrayList<Int>(rows)
            var end = count
            for (row in rows downTo 1) { end = previous[row][end]; starts.add(end) }
            val candidate = starts.asReversed().toList()
            if (!fillViewport) { rowStarts = candidate; break }
            val candidateWidth = candidate.mapIndexed { index, start ->
                rowWidth(start, candidate.getOrNull(index + 1) ?: count) }.maxOrNull() ?: 1f
            val candidateHeight = candidate.mapIndexed { index, start ->
                (start until (candidate.getOrNull(index + 1) ?: count)).maxOf { reservedHeights[it] }
            }.sum() + rowGap * (rows - 1)
            val scale = min(maxFitScale, minOf(rowLimit / (candidateWidth + horizontalMotionReach * 2f),
                height / candidateHeight.coerceAtLeast(1f)))
            val score = scale / (1f + costs[rows][count] * .025f)
            if (score > bestScore) { bestScore = score; rowStarts = candidate }
        }
    }
    val rowWidths = rowStarts.mapIndexed { index, start -> rowWidth(start, rowStarts.getOrNull(index + 1) ?: count) }
    val rowHeights = rowStarts.mapIndexed { index, start ->
        (start until (rowStarts.getOrNull(index + 1) ?: count)).maxOf { reservedHeights[it] } + rowGap
    }
    val totalHeight = rowHeights.sum() - rowGap
    val totalWidth = rowWidths.maxOrNull() ?: width
    val fit = min(maxFitScale, min(rowLimit / (totalWidth + horizontalMotionReach * 2f).coerceAtLeast(1f),
        height / totalHeight.coerceAtLeast(1f)))
    val slots = ArrayList<PopLyricSlot>(count)
    var top = ((height - totalHeight * fit) * (if (fillViewport) .5f else anchorY.coerceIn(0f, 1f))).coerceAtLeast(0f)
    val indent = min(width * .16f, typicalHeight * .9f) * fit * indentPercent / 100f
    for (row in rowStarts.indices) {
        val slack = (width - rowWidths[row] * fit).coerceAtLeast(0f)
        val offset = when {
            rowStarts.size == 1 || layout == "centered" -> 0f
            layout == "stairs" -> (row.toFloat() / (rowStarts.size - 1) * 2f - 1f) * indent
            layout == "wave" -> sin(row * 1.35f + PopVisualMotion.choice(layoutSeed, 8)) * indent
            row % 2 == 0 -> -indent
            else -> indent
        }
        val motionMargin = horizontalMotionReach * fit
        var left = (slack / 2f + offset).coerceIn(motionMargin, (slack - motionMargin).coerceAtLeast(motionMargin))
        val until = rowStarts.getOrNull(row + 1) ?: count
        for (index in rowStarts[row] until until) {
            slots += PopLyricSlot(left + reservedWidths[index] * fit / 2f, top + (rowHeights[row] - rowGap) * fit / 2f)
            left += (reservedWidths[index] + (gaps.getOrNull(index + 1) ?: 0f)) * fit
        }
        top += rowHeights[row] * fit
    }
    // Center the union of the actual row extents; alternating indents can otherwise bias the block.
    val left = slots.indices.minOf { slots[it].x - (reservedWidths[it] / 2f + horizontalMotionReach) * fit }
    val right = slots.indices.maxOf { slots[it].x + (reservedWidths[it] / 2f + horizontalMotionReach) * fit }
    val correction = width / 2f - (left + right) / 2f
    return PopLyricPacking(slots.map { it.copy(x = it.x + correction) }, fit, rowStarts)
}
