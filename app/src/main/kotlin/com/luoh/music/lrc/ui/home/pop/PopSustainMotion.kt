package com.luoh.music.lrc.ui.home.pop

import kotlin.math.sin

internal data class PopSustainPlan(val startMs: Long, val endMs: Long, val rampMs: Long,
    val amount: Float, val phase: Int) {
    val reachXDp get() = 1.5f * amount
    val reachYDp get() = amount
    val reachDegrees get() = 1.1f * amount
}

internal data class PopSustainPose(val xDp: Float = 0f, val yDp: Float = 0f, val degrees: Float = 0f)

/** A small, continuous tremble on genuine held notes. No random values or audio clocks. */
internal object PopSustainMotion {
    private val rest = PopSustainPose()
    private val wave = FloatArray(257) { sin(it * Math.PI * 2.0 / 256).toFloat() }

    fun plan(token: PopLyricToken, entranceMs: Long, phase: Int, percent: Int): PopSustainPlan? {
        val duration = token.endMs - token.startMs
        if (percent <= 0 || duration < 650L) return null
        val start = token.startMs + (entranceMs / 2).coerceIn(0L, 250L)
        val remaining = token.endMs - start
        if (remaining <= 0L) return null
        val amount = ((duration - 650L) / 700f).coerceIn(.35f, 1f) * percent.coerceIn(0, 200) / 100f
        return PopSustainPlan(start, token.endMs, minOf(140L, remaining / 3).coerceAtLeast(1L),
            amount, Math.floorMod(phase, 256))
    }

    private fun wave(elapsedMs: Long, periodMs: Long, phase: Int): Float {
        val sample = Math.floorMod(elapsedMs, periodMs) * 256f / periodMs
        val index = sample.toInt()
        val from = wave[(index + phase) % 256]
        val to = wave[(index + phase + 1) % 256]
        return from + (to - from) * (sample - index)
    }

    fun sample(plan: PopSustainPlan?, now: Long): PopSustainPose {
        if (plan == null || now <= plan.startMs || now >= plan.endMs) return rest
        val elapsed = now - plan.startMs
        val envelope = PopLyricMotion.pass(elapsed, plan.rampMs) * PopLyricMotion.pass(plan.endMs - now, plan.rampMs)
        return PopSustainPose(
            wave(elapsed, 151L, plan.phase) * plan.reachXDp * envelope,
            wave(elapsed, 193L, (plan.phase + 73) % 256) * plan.reachYDp * envelope,
            wave(elapsed, 239L, (plan.phase + 139) % 256) * plan.reachDegrees * envelope)
    }
}
