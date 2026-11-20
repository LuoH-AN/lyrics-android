package com.luoh.music.lrc

internal data class PopStackMetric(val startMs: Long, val height: Float, val width: Float = 0f, val seed: Long = startMs)
internal data class PopStackPlacement(val index: Int, val top: Float, val bottom: Float,
    val left: Float = 0f, val right: Float = 1f) {
    fun moved(x: Float, y: Float) = copy(top = top + y, bottom = bottom + y, left = left + x, right = right + x)
}
internal data class PopStackWindow(val current: Int, val visible: List<PopStackPlacement>, val motionUntilMs: Long?)
private data class PopStackStep(val current: Int, val from: List<PopStackPlacement>, val to: List<PopStackPlacement>)

/** Cache the strip's pushes, including direction changes. Old text keeps its size and
 * relative spacing; once pushed outside, it cannot reappear when the direction reverses. */
internal class PopStackFlow(private val blocks: List<PopStackMetric>, private val viewportHeight: Float,
    gap: Float, anchorPercent: Int, private val scrollMs: Int, downwards: Boolean = false,
    private val viewportWidth: Float = 1f, direction: String = if (downwards) "down" else "up") {
    private val groups: List<Int>
    private val steps: List<PopStackStep>

    init {
        require(viewportHeight > 0f && viewportHeight.isFinite() && viewportWidth > 0f && viewportWidth.isFinite())
        require(blocks.all { it.height > 0f && it.height.isFinite() && it.width >= 0f && it.width.isFinite() })
        require(blocks.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
        groups = blocks.indices.filter { it == blocks.lastIndex || blocks[it + 1].startMs > blocks[it].startMs }
        val spacing = gap.coerceAtLeast(0f)
        var prior = emptyList<PopStackPlacement>()
        steps = groups.mapIndexed { group, current ->
            val vector = PopVisualMotion.direction(direction, blocks[current].seed)
            val first = if (group == 0) 0 else groups[group - 1] + 1
            val span = (first..current).sumOf { blocks[it].height.toDouble() }.toFloat() + (current - first) * spacing
            val fraction = anchorPercent.coerceIn(10, 90) / 100f
            val horizontal = vector.x != 0f
            val groupWidth = (first..current).maxOf { blocks[it].width.takeIf { w -> w > 0f } ?: viewportWidth }
            val edgeY = if (vector.y > 0f) viewportHeight * fraction else viewportHeight * fraction - span
            // Oversize text remains centered over the viewport instead of vanishing below it.
            var top = if (horizontal) (viewportHeight - span) / 2f else
                edgeY.coerceIn(minOf(0f, viewportHeight - span), maxOf(0f, viewportHeight - span))
            val groupLeft = if (horizontal) {
                val edge = if (vector.x > 0f) viewportWidth * fraction else viewportWidth * fraction - groupWidth
                edge.coerceIn(minOf(0f, viewportWidth - groupWidth), maxOf(0f, viewportWidth - groupWidth))
            } else (viewportWidth - groupWidth) / 2f
            val incoming = (first..current).map { index ->
                val width = blocks[index].width.takeIf { it > 0f } ?: viewportWidth
                val left = groupLeft + (groupWidth - width) / 2f
                PopStackPlacement(index, top, top + blocks[index].height, left, left + width).also {
                    top = it.bottom + spacing
                }
            }
            val shift = if (prior.isEmpty()) 0f else when {
                vector.x < 0f -> (prior.maxOf { it.right } - incoming.minOf { it.left } + spacing).coerceAtLeast(0f)
                vector.x > 0f -> (incoming.maxOf { it.right } - prior.minOf { it.left } + spacing).coerceAtLeast(0f)
                vector.y < 0f -> (prior.maxOf { it.bottom } - incoming.minOf { it.top } + spacing).coerceAtLeast(0f)
                else -> (incoming.maxOf { it.bottom } - prior.minOf { it.top } + spacing).coerceAtLeast(0f)
            }
            val dx = vector.x * shift
            val dy = vector.y * shift
            val moved = prior.map { it.moved(dx, dy) }
            val from = prior + incoming.map { it.moved(-dx, -dy) }
            val to = moved + incoming
            // Expiration is spatial and cached, so changing direction cannot revive retired text.
            prior = to.filter(::onScreen)
            PopStackStep(current, from, to)
        }
    }

    private fun onScreen(p: PopStackPlacement) = p.bottom > 0f && p.top < viewportHeight &&
        p.right > 0f && p.left < viewportWidth

    fun at(now: Long, animate: Boolean = true): PopStackWindow {
        val group = groups.indexOfLast { blocks[it].startMs <= now }
        if (group < 0) return PopStackWindow(-1, emptyList(), null)
        val step = steps[group]
        val start = blocks[step.current].startMs
        val available = groups.getOrNull(group + 1)?.let { blocks[it].startMs - start } ?: Long.MAX_VALUE
        val duration = minOf(scrollMs.coerceAtLeast(0).toLong(), available)
        val moving = animate && group > 0 && now - start < duration
        val progress = if (moving) PopVisualMotion.travel((now - start).toFloat() / duration) else 1f
        val visible = step.to.mapIndexedNotNull { index, target ->
            val from = step.from[index]
            from.moved((target.left - from.left) * progress, (target.top - from.top) * progress).takeIf(::onScreen)
        }
        return PopStackWindow(step.current, visible, if (moving) start + duration else null)
    }
}
