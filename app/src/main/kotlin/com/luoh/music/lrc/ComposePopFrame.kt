package com.luoh.music.lrc

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** One playback-time geometry plan shared by drawing and subtitle placement. */
internal class PopFrameBuffer(private val scenes: PopScenes, private val style: PopLyricsStyle,
    private val allowMotion: Boolean, val width: Float, val height: Float, private val density: Float,
    private val cameraPath: PopCameraPath, private val sentencePlan: PopTransitionPlan,
    private val sentenceStartMs: Long, private val sentenceDurationMs: Int, private val enteringSentence: Boolean) {
    val glyphs = scenes.pages.map { page -> Array(page.glyphs.size) { FloatArray(8) } }
    val centersX = FloatArray(scenes.pages.size)
    val centersY = FloatArray(scenes.pages.size)
    val fits = FloatArray(scenes.pages.size) { 1f }
    val bottoms = FloatArray(scenes.pages.size) { height / 2f }
    val alphas = FloatArray(scenes.pages.size)
    // Keep resting ink independent of animated scale, so fitting cannot cancel springs and zooms.
    private val restingBounds = scenes.pages.map { page ->
        page.glyphs.map { glyph ->
            val scale = page.scale * glyph.baseScale
            val angle = Math.toRadians(glyph.angle.toDouble())
            val halfWidth = (glyph.ink.width * abs(cos(angle)) + glyph.ink.height * abs(sin(angle))).toFloat() * scale / 2f
            val halfHeight = (glyph.ink.height * abs(cos(angle)) + glyph.ink.width * abs(sin(angle))).toFloat() * scale / 2f
            floatArrayOf(glyph.slot.x - halfWidth, glyph.slot.x + halfWidth,
                glyph.slot.y - halfHeight, glyph.slot.y + halfHeight)
        }
    }
    private val settledAt = scenes.pages.map { page -> page.timing.passStartMs.indices.maxOf { index ->
        page.timing.passStartMs[index] + page.timing.passMs[index]
    } }
    private var preparedAt = Long.MIN_VALUE
    var window = popPhraseWindow(scenes.ranges, scenes.timeline, scenes.handoffs, scenes.timeline.startMs, allowMotion)
        private set
    private val plans = scenes.pages.map { page ->
        val seed = PopVisualMotion.seed(page.timeline.startMs, page.timeline.tokens.joinToString("") { it.text })
        val flow = PopStageMotion.flow(scenes.cameraPath, scenes.cameraPath, page.timeline.startMs,
            PopMotionOrchestration.cameraStyle(style), width, height)
        PopMotionOrchestration.transition(style, seed, flow)
    }

    fun prepare(now: Long): PopFrameBuffer {
        if (preparedAt == now) return this
        preparedAt = now
        window = popPhraseWindow(scenes.ranges, scenes.timeline, scenes.handoffs, now, allowMotion)
        val handoff = scenes.handoffs[window.current]
        val fraction = if (window.retiring != null && handoff.durationMs > 0L)
            ((now - handoff.startMs).toFloat() / handoff.durationMs).coerceIn(0f, 1f) else 1f
        val sentenceProgress = if (sentenceDurationMs > 0)
            ((now - sentenceStartMs).toFloat() / sentenceDurationMs).coerceIn(0f, 1f) else 1f
        val progress = minOf(fraction, sentenceProgress)
        val wordGain = PopMotionOrchestration.handoffGain(progress, style.coordinateMotion, false)
        val rowGain = PopMotionOrchestration.handoffGain(progress, style.coordinateMotion, true)
        val camera = PopStageMotion.sample(now, cameraPath, PopMotionOrchestration.cameraStyle(style), allowMotion)
        val sentence = PopMotionOrchestration.pose(sentencePlan, sentenceProgress, enteringSentence, width, height)
        fun plan(index: Int, entering: Boolean) {
            val phrase = PopMotionOrchestration.pose(plans[window.current], fraction, entering, width, height)
            val pose = phrase.copy(x = phrase.x + sentence.x, y = phrase.y + sentence.y, scale = phrase.scale * sentence.scale)
            preparePage(index, now, pose, camera, wordGain, rowGain)
        }
        window.retiring?.let { plan(it, false) }
        plan(window.current, true)
        return this
    }

    private fun preparePage(pageIndex: Int, now: Long, pose: PopVisualPose, camera: PopStagePose,
        wordGain: Float, rowGain: Float) {
        alphas[pageIndex] = pose.alpha
        if (pose.alpha <= 0f) return
        val page = scenes.pages[pageIndex]
        val glyphFrames = glyphs[pageIndex]
        val timeline = page.timeline
        val timing = page.timing
        val animated = timeline.wordTimed && allowMotion
        val tilt = pose.x / width.coerceAtLeast(1f) * 5f - pose.y / height.coerceAtLeast(1f) * 5f +
            (camera.x - camera.y) * 8f + camera.rotation
        val radians = Math.toRadians(tilt.toDouble())
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        var restLeft = Float.POSITIVE_INFINITY
        var restRight = Float.NEGATIVE_INFINITY
        var restTop = Float.POSITIVE_INFINITY
        var restBottom = Float.NEGATIVE_INFINITY
        for (row in page.rows) {
            val enteredRow = if (animated && row.entranceMs > 0L)
                PopLyricMotion.enter(now - row.startMs, row.entranceMs) else 1f
            val passedRow = if (animated && row.passMs > 0L)
                PopLyricMotion.pass(now - row.passStartMs, row.passMs) else 0f
            val rowLife = if (animated) (1f - passedRow) * PopVisualMotion.travel(
                (now - row.startMs).toFloat() / row.entranceMs.coerceAtLeast(1L)) else 0f
            val sway = if (animated) sin((now - row.startMs) / 650f) * row.travel * .12f * rowLife * rowGain else 0f
            val rowY = if (animated) row.travel * .55f * (1f - enteredRow) * rowGain + sway else 0f
            val rowX = if (animated) row.travelX * .55f * (1f - enteredRow) * rowGain else 0f
            val rowScale = if (animated && row.travel > 0f) 1f - .04f * (1f - enteredRow) * rowGain else 1f
            for (index in row.from until row.until) {
                val token = timeline.tokens[index]
                val glyph = page.glyphs[index]
                val frame = glyphFrames[index]
                val elapsed = now - (token.startMs - timing.lookaheadMs[index] + token.visualDelayMs)
                if (animated && elapsed < 0L) { frame[4] = 0f; continue }
                val entered = if (animated && timing.entranceMs[index] > 0L)
                    PopLyricMotion.enter(elapsed, timing.entranceMs[index]) else 1f
                val passed = if (animated && timing.passMs[index] > 0L)
                    PopLyricMotion.pass(now - timing.passStartMs[index], timing.passMs[index])
                    else if (animated && now >= timing.passStartMs[index]) 1f else 0f
                val fadeMs = minOf(140L, timing.entranceMs[index] / 3).coerceAtLeast(1L)
                val visibility = if (animated && timing.entranceMs[index] > 0L)
                    PopVisualMotion.travel(elapsed.toFloat() / fadeMs) else 1f
                frame[4] = visibility * (if (animated) 1f - passed * (1f - style.passedOpacityPercent / 100f) else 1f)
                frame[5] = passed
                // Whitespace participates in the source clock, never in visible bounds or centering.
                if (glyph.ink.isEmpty) { frame[4] = 0f; continue }
                if (visibility <= 0f || frame[4] <= 0f) continue
                val rest = restingBounds[pageIndex][index]
                if (!restLeft.isFinite()) {
                    restLeft = rest[0]; restRight = rest[1]; restTop = rest[2]; restBottom = rest[3]
                } else {
                    restLeft += (rest[0] - restLeft).coerceAtMost(0f) * visibility
                    restRight += (rest[1] - restRight).coerceAtLeast(0f) * visibility
                    restTop += (rest[2] - restTop).coerceAtMost(0f) * visibility
                    restBottom += (rest[3] - restBottom).coerceAtLeast(0f) * visibility
                }
                val targetScale = 1f + (glyph.dynamics.activeScale - 1f) * wordGain
                val entranceScale = .78f + entered * (targetScale - .78f)
                val wordScale = if (animated) (entranceScale + (1f - entranceScale) * passed)
                    .coerceIn(.5f, glyph.dynamics.maxScale) else 1f
                val shake = PopSustainMotion.sample(if (animated) timing.sustain.getOrNull(index) else null, now)
                val baseScale = page.scale * wordScale * glyph.baseScale * rowScale
                val remaining = if (animated) 1f - entered else 0f
                val gx = row.center.x + (glyph.slot.x + glyph.entranceOffset.x * remaining * wordGain - row.center.x) * rowScale +
                    rowX + shake.xDp * density * wordGain * baseScale
                val gy = row.center.y + (glyph.slot.y + glyph.entranceOffset.y * remaining * wordGain - row.center.y) * rowScale +
                    rowY + shake.yDp * density * wordGain * baseScale
                val localX = gx - width / 2f
                val localY = gy - height / 2f
                // Centered parallax preserves camera and directional motion after recentering.
                val shearX = camera.x * 1.5f + pose.x / width.coerceAtLeast(1f) * .6f
                val shearY = camera.y * 1.5f + pose.y / height.coerceAtLeast(1f) * .6f
                val dx = (localX + localY * shearX) * pose.scale
                val dy = (localY + localX * shearY) * pose.scale
                frame[0] = width / 2f + dx * cosine - dy * sine
                frame[1] = height / 2f + dx * sine + dy * cosine
                frame[2] = baseScale * pose.scale
                frame[3] = tilt + if (animated) (glyph.angle * (1f - passed * .65f) +
                    style.rotationDegrees * glyph.dynamics.rotation * (1f - entered) + shake.degrees) * wordGain else 0f
                val angle = Math.toRadians(frame[3].toDouble())
                frame[6] = (glyph.ink.width * abs(cos(angle)) + glyph.ink.height * abs(sin(angle))).toFloat() * frame[2] / 2f
                frame[7] = (glyph.ink.height * abs(cos(angle)) + glyph.ink.width * abs(sin(angle))).toFloat() * frame[2] / 2f
                val gl = frame[0] - frame[6]
                val gr = frame[0] + frame[6]
                val gt = frame[1] - frame[7]
                val gb = frame[1] + frame[7]
                if (!left.isFinite()) { left = gl; right = gr; top = gt; bottom = gb } else {
                    // Fade new bounds in with the new glyph; a barely visible onset must not jerk the block.
                    left += (gl - left).coerceAtMost(0f) * visibility
                    right += (gr - right).coerceAtLeast(0f) * visibility
                    top += (gt - top).coerceAtMost(0f) * visibility
                    bottom += (gb - bottom).coerceAtLeast(0f) * visibility
                }
            }
        }
        if (!left.isFinite()) { alphas[pageIndex] = 0f; bottoms[pageIndex] = height / 2f; return }
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val hardFit = minOf(width * .98f / (right - left).coerceAtLeast(1f),
            height * .98f / (bottom - top).coerceAtLeast(1f))
        // A resting block at 94% leaves no space for a 145% word spring. Keep modest headroom
        // while singing, then settle to the requested fill after the complete phrase has passed.
        val settled = if (animated) PopVisualMotion.travel((now - settledAt[pageIndex]).toFloat() / POP_LYRIC_SETTLE_MS) else 1f
        val fill = style.stageFillPercent / 100f * (.88f + .12f * settled)
        val fillFit = minOf(width * fill / (restRight - restLeft).coerceAtLeast(1f),
            height * fill / (restBottom - restTop).coerceAtLeast(1f))
        // Fit the visible chunk, rather than reserving room for words which have not appeared.
        // New resting bounds blend with visibility; animated poses remain separate from the fit.
        val firstElapsed = now - (timeline.tokens[0].startMs - timing.lookaheadMs[0] + timeline.tokens[0].visualDelayMs)
        val grow = if (animated) PopVisualMotion.travel(firstElapsed.toFloat() / timing.entranceMs[0].coerceAtLeast(300L)) else 1f
        // Classic keeps the page's measured font size as words arrive; focus enlarges visible ink.
        val fit = if (style.sizingMode == "classic") minOf(1f, hardFit)
            else minOf(hardFit, if (fillFit > 1f) 1f + (fillFit - 1f) * grow else fillFit)
        centersX[pageIndex] = centerX
        centersY[pageIndex] = centerY
        fits[pageIndex] = fit
        bottoms[pageIndex] = height / 2f + (bottom - top) * fit / 2f
    }
}
