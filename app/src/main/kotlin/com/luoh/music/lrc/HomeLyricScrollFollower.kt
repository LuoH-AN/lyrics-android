package com.luoh.music.lrc

import kotlin.math.abs
import kotlin.math.exp

/** A critically damped follower: changing the target preserves the current velocity. */
internal class HomeLyricScrollFollower {
    var velocity: Float = 0f
        private set

    fun reset() { velocity = 0f }

    // Lazy-list offsets are whole pixels, so half-pixel errors cannot always be eliminated.
    fun settled(error: Float): Boolean = abs(error) < .75f && abs(velocity) < 1f

    fun advance(error: Float, elapsedSeconds: Float, durationMs: Int, lineIntervalMs: Long): Float {
        val duration = minOf(durationMs.toLong(), (lineIntervalMs * .85).toLong()).coerceIn(180L, 1500L)
        val omega = 6f / (duration / 1000f)
        val dt = elapsedSeconds.coerceIn(.001f, .05f)
        val decay = exp(-omega * dt)
        val tangent = velocity - omega * error
        val move = error + (-error + tangent * dt) * decay
        velocity = (velocity - omega * tangent * dt) * decay
        return move
    }
}
