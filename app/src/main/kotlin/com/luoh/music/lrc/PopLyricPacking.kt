package com.luoh.music.lrc

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class PopLyricSlot(val x: Float, val y: Float)
internal data class PopLyricPacking(val slots: List<PopLyricSlot>, val scale: Float)

/** Reserve the animated extents before drawing; resizing never depends on playback time. */
internal fun packPopLyrics(widths: FloatArray, heights: FloatArray, width: Float, height: Float, gap: Float, layoutStyle: String = "staggered", maxScale: Float = PopLyricMotion.MAX_SCALE, rotationDegrees: Int = 20): PopLyricPacking {
    require(widths.size == heights.size && widths.size <= PopLyricTimeline.MAX_TOKENS)
    if (widths.isEmpty() || width <= 0f || height <= 0f) return PopLyricPacking(emptyList(), 1f)
    val rotationReach = (sin(rotationDegrees * 1.15 * Math.PI / 180) * maxScale).toFloat()
    val reservedWidths = FloatArray(widths.size) { widths[it] * maxScale + heights[it] * rotationReach + gap }
    val reservedHeights = FloatArray(heights.size) { heights[it] * maxScale + widths[it] * rotationReach + gap }
    val rowStarts = ArrayList<Int>()
    val rowWidths = ArrayList<Float>()
    val rowHeights = ArrayList<Float>()
    val rowLimit = if (layoutStyle == "centered") width else width * if (height > width * 1.1f) .78f else .94f
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
            when {
                rowStarts.size == 1 || layoutStyle == "centered" -> .5f
                layoutStyle == "stairs" -> .12f + .76f * row / (rowStarts.size - 1)
                row % 2 == 0 -> .22f
                else -> .78f
            }
        val until = rowStarts.getOrNull(row + 1) ?: widths.size
        for (index in rowStarts[row] until until) {
            slots += PopLyricSlot(left + reservedWidths[index] * fit / 2f, top + rowHeights[row] * fit / 2f)
            left += reservedWidths[index] * fit
        }
        top += rowHeights[row] * fit
    }
    return PopLyricPacking(slots, fit)
}
