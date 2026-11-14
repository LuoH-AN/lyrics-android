package com.luoh.music.lrc

import kotlin.math.max
import kotlin.math.min

internal data class PopLyricSlot(val x: Float, val y: Float)
internal data class PopLyricPacking(val slots: List<PopLyricSlot>, val scale: Float)

/** Reserve the animated extents before drawing; resizing never depends on playback time. */
internal fun packPopLyrics(widths: FloatArray, heights: FloatArray, width: Float, height: Float, gap: Float): PopLyricPacking {
    require(widths.size == heights.size && widths.size <= PopLyricTimeline.MAX_TOKENS)
    if (widths.isEmpty() || width <= 0f || height <= 0f) return PopLyricPacking(emptyList(), 1f)
    val reservedWidths = FloatArray(widths.size) { widths[it] * PopLyricMotion.MAX_SCALE + heights[it] * .52f + gap }
    val reservedHeights = FloatArray(heights.size) { heights[it] * PopLyricMotion.MAX_SCALE + widths[it] * .52f + gap }
    val rowStarts = ArrayList<Int>()
    val rowWidths = ArrayList<Float>()
    val rowHeights = ArrayList<Float>()
    val rowLimit = width * if (height > width * 1.1f) .78f else .94f
    var start = 0
    var rowWidth = 0f
    var rowHeight = 0f
    for (index in widths.indices) {
        if (index > start && rowWidth + reservedWidths[index] > rowLimit) {
            rowStarts += start
            rowWidths += rowWidth
            rowHeights += rowHeight
            start = index
            rowWidth = 0f
            rowHeight = 0f
        }
        rowWidth += reservedWidths[index]
        rowHeight = max(rowHeight, reservedHeights[index])
    }
    rowStarts += start
    rowWidths += rowWidth
    rowHeights += rowHeight
    val totalHeight = rowHeights.sum()
    val totalWidth = rowWidths.maxOrNull() ?: width
    val fit = min(1f, min(width / totalWidth.coerceAtLeast(1f), height / totalHeight.coerceAtLeast(1f)))
    val slots = ArrayList<PopLyricSlot>(widths.size)
    var top = (height - totalHeight * fit) / 2f
    for (row in rowStarts.indices) {
        var left = (width - rowWidths[row] * fit) *
            if (rowStarts.size == 1) .5f else if (row % 2 == 0) .22f else .78f
        val until = rowStarts.getOrNull(row + 1) ?: widths.size
        for (index in rowStarts[row] until until) {
            slots += PopLyricSlot(left + reservedWidths[index] * fit / 2f, top + rowHeights[row] * fit / 2f)
            left += reservedWidths[index] * fit
        }
        top += rowHeights[row] * fit
    }
    return PopLyricPacking(slots, fit)
}
