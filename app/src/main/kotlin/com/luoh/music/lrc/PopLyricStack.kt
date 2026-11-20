package com.luoh.music.lrc

internal data class PopStackMetric(val startMs: Long, val height: Float)
internal data class PopStackPlacement(val index: Int, val top: Float, val bottom: Float)
internal data class PopStackWindow(val current: Int, val visible: List<PopStackPlacement>, val motionUntilMs: Long?)

/** An immutable strip. Only its viewport moves; completed lyrics keep their slots.
 * Playback time drives the movement, so pauses and seeks reproduce the same picture. */
internal class PopStackFlow(private val blocks: List<PopStackMetric>, private val viewportHeight: Float,
    gap: Float, anchorPercent: Int, private val scrollMs: Int, private val downwards: Boolean = false) {
    private val tops = FloatArray(blocks.size)
    private val bottoms = FloatArray(blocks.size)
    private val groups: List<Int>
    private val targets: FloatArray

    init {
        require(viewportHeight > 0f && viewportHeight.isFinite())
        require(blocks.all { it.height > 0f && it.height.isFinite() })
        require(blocks.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
        var top = 0f
        for (index in blocks.indices) {
            tops[index] = top
            bottoms[index] = top + blocks[index].height
            top = bottoms[index] + gap.coerceAtLeast(0f)
        }
        // Simultaneous onsets share one viewport target, never an artificial delay.
        groups = blocks.indices.filter { it == blocks.lastIndex || blocks[it + 1].startMs > blocks[it].startMs }
        targets = FloatArray(groups.size) { group ->
            val index = groups[group]
            val percent = if (downwards) 100 - anchorPercent else anchorPercent
            val first = if (group == 0) 0 else groups[group - 1] + 1
            val span = bottoms[index] - tops[first]
            val anchor = (viewportHeight * percent.coerceIn(10, 90) / 100f)
                .coerceAtLeast(span.coerceAtMost(viewportHeight))
            anchor - bottoms[index]
        }
    }

    fun at(now: Long, animate: Boolean = true): PopStackWindow {
        val group = groups.indexOfLast { blocks[it].startMs <= now }
        if (group < 0) return PopStackWindow(-1, emptyList(), null)
        val current = groups[group]
        val start = blocks[current].startMs
        val available = groups.getOrNull(group + 1)?.let { blocks[it].startMs - start } ?: Long.MAX_VALUE
        val duration = minOf(scrollMs.coerceAtLeast(0).toLong(), available)
        val elapsed = (now - start).coerceAtLeast(0L)
        val moving = animate && group > 0 && elapsed < duration
        val progress = if (moving) {
            val fraction = elapsed.toFloat() / duration
            fraction * fraction * (3f - 2f * fraction)
        } else 1f
        val offset = if (moving) targets[group - 1] + (targets[group] - targets[group - 1]) * progress else targets[group]
        val visible = (0..current).mapNotNull { index ->
            val top = if (downwards) viewportHeight - (bottoms[index] + offset) else tops[index] + offset
            val bottom = top + blocks[index].height
            if (bottom <= 0f || top >= viewportHeight) null else PopStackPlacement(index, top, bottom)
        }
        return PopStackWindow(current, visible, if (moving) start + duration else null)
    }
}
