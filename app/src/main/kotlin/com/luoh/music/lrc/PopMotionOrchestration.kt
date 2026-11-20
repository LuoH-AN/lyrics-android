package com.luoh.music.lrc

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

internal data class PopMotionBalance(val word: Float = 1f, val row: Float = 1f,
    val camera: Float = 1f, val transition: Float = 1f)
internal data class PopSpatialBudget(val scale: Float = 1f, val rotation: Float = 1f,
    val travel: Float = 1f, val row: Float = 1f, val hierarchy: Float = 1f)
internal data class PopWordDynamics(val activeScale: Float = 1f, val rotation: Float = 1f,
    val travel: Float = 1f, val sustain: Float = 1f, val hierarchy: Float = 1f) {
    val maxScale get() = activeScale + (activeScale - .72f) * .05f
}
internal data class PopTransitionPlan(val effect: String, val direction: PopVector,
    val amount: Float = 1f, val drift: PopVector = PopVector(0f, 0f))

/** Plans use source onsets and durations, never inferred beats or render-frame history. */
internal object PopMotionOrchestration {
    fun strength(style: PopLyricsStyle): Float = when (style.motionStrength) {
        "gentle" -> .65f
        "strong" -> 1.18f
        else -> 1f
    }

    /** Give the words priority; secondary movement yields as the user raises other channels. */
    fun balance(style: PopLyricsStyle): PopMotionBalance {
        if (!style.coordinateMotion) return PopMotionBalance()
        val word = (style.travelPercent / 150f + style.rotationDegrees / 30f +
            (style.activeScalePercent - 100) / 80f).coerceIn(0f, 2f) / 2f
        val row = style.rowMotionPercent / 100f
        val camera = if (style.camera == "off") 0f else (style.cameraPercent / 120f).coerceAtMost(1.5f)
        val transition = if (style.transition == "fade") 0f else .7f
        val energy = strength(style)
        return PopMotionBalance(
            1f / (1f + .14f * row * camera * energy),
            1f / (1f + .50f * (word + camera + transition) * energy),
            1f / (1f + .20f * word * row * energy),
            1f / (1f + .24f * (word + row) * energy))
    }

    /** Preserve type size in shallow stages by yielding motion BEFORE the fit calculation. */
    fun spatial(style: PopLyricsStyle, glyphHeight: Float, stageHeight: Float): PopSpatialBudget {
        if (!style.coordinateMotion) return PopSpatialBudget()
        val room = PopVisualMotion.travel((stageHeight / glyphHeight.coerceAtLeast(1f) - 1.4f) / 3.2f)
        return PopSpatialBudget(.25f + .75f * room, .15f + .85f * room,
            .40f + .60f * room, .20f + .80f * room, .50f + .50f * room)
    }

    fun fitWord(word: PopWordDynamics, budget: PopSpatialBudget): PopWordDynamics = word.copy(
        activeScale = 1f + (word.activeScale - 1f) * budget.scale,
        rotation = word.rotation * budget.rotation, travel = word.travel * budget.travel,
        sustain = word.sustain * budget.hierarchy, hierarchy = 1f + (word.hierarchy - 1f) * budget.hierarchy)

    fun cameraStyle(style: PopLyricsStyle): PopLyricsStyle = style.copy(
        cameraPercent = (style.cameraPercent * strength(style) * balance(style).camera).roundToInt().coerceIn(0, 200))

    fun words(timeline: PopLyricTimeline, style: PopLyricsStyle): List<PopWordDynamics> {
        val strength = strength(style)
        val balance = balance(style)
        return timeline.tokens.mapIndexed { index, token ->
            val duration = (token.endMs - token.startMs).coerceAtLeast(1L)
            // Simultaneous letters/words share one density, rather than appearing to be rapid onsets.
            val next = timeline.tokens.drop(index + 1).firstOrNull { it.startMs > token.startMs }?.startMs
            val previous = timeline.tokens.take(index).lastOrNull { it.startMs < token.startMs }?.startMs
            val interval = minOf(next?.minus(token.startMs) ?: duration,
                previous?.let { token.startMs - it } ?: duration).coerceAtLeast(1L)
            val pace = if (style.adaptiveEmphasis) PopVisualMotion.travel((interval - 90L) / 360f) else 1f
            val held = if (style.adaptiveEmphasis) PopVisualMotion.travel((duration - 450L) / 1400f) * pace else 0f
            val emphasis = if (style.adaptiveEmphasis) .38f + .62f * pace + .10f * held else 1f
            PopWordDynamics(
                (1f + (style.activeScalePercent / 100f - 1f) * strength * emphasis).coerceAtMost(1.94f),
                strength * (if (style.adaptiveEmphasis) .30f + .70f * pace else 1f) * balance.word,
                strength * (if (style.adaptiveEmphasis) .55f + .45f * pace else 1f) * balance.word,
                strength * (if (style.adaptiveEmphasis) .60f + .25f * pace + .15f * held else 1f) * balance.word,
                if (style.adaptiveEmphasis) .97f + .06f * pace + .04f * held else 1f)
        }
    }

    /** Automatic directions follow screen-space flow. Explicit directions always win. */
    fun direction(mode: String, seed: Long, flow: PopVector, follow: Boolean): PopVector {
        if (!follow || hypot(flow.x, flow.y) < .8f || mode in listOf("left", "right", "up", "down"))
            return PopVisualMotion.direction(mode, seed)
        fun sign(value: Float) = if (value < 0f) -1f else 1f
        return when (mode) {
            "horizontal" -> if (abs(flow.x) >= .6f) PopVector(sign(flow.x), 0f) else PopVisualMotion.direction(mode, seed)
            "vertical" -> if (abs(flow.y) >= .6f) PopVector(0f, sign(flow.y)) else PopVisualMotion.direction(mode, seed)
            else -> if (abs(flow.x) >= abs(flow.y)) PopVector(sign(flow.x), 0f) else PopVector(0f, sign(flow.y))
        }
    }

    fun transition(style: PopLyricsStyle, seed: Long, flow: PopVector): PopTransitionPlan {
        val length = hypot(flow.x, flow.y)
        val effect = PopVisualMotion.transition(style.transition, seed)
        val drift = if (style.flowTransitions && length >= .8f && effect !in listOf("slide", "fade"))
            PopVector(flow.x / length * .025f, flow.y / length * .020f) else PopVector(0f, 0f)
        return PopTransitionPlan(effect, direction(style.pushDirection, seed, flow, style.flowTransitions),
            (strength(style) * balance(style).transition).coerceIn(.45f, 1.25f), drift)
    }

    fun pose(plan: PopTransitionPlan, progress: Float, entering: Boolean, width: Float, height: Float): PopVisualPose {
        val pose = PopVisualMotion.phrase(plan.effect, plan.direction, progress, entering, width, height, plan.amount)
        val remaining = if (entering) 1f - PopVisualMotion.travel(progress) else PopVisualMotion.travel(progress)
        val sign = if (entering) -1f else 1f
        return pose.copy(x = pose.x + plan.drift.x * width * remaining * sign * plan.amount,
            y = pose.y + plan.drift.y * height * remaining * sign * plan.amount)
    }

    /** Smoothly return secondary movement after the new phrase has landed. */
    fun handoffGain(progress: Float, coordinated: Boolean, row: Boolean): Float =
        if (!coordinated) 1f else 1f - (if (row) .55f else .28f) * (1f - PopVisualMotion.travel(progress))
}
