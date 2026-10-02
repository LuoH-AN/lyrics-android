package com.luoh.music.lrc

import kotlin.math.abs

/** A monotonic playback clock; callers decide whether a snapshot represents a real seek/new track. */
class LyricClock(private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L }) {
    var durationMs: Long = 0L
    var playing: Boolean = false
        private set
    var speed: Float = 1f
        private set
    private var anchorMs = 0L
    private var anchoredAtMs = nowMs()
    private var initialized = false

    fun sync(positionMs: Long, playing: Boolean, speed: Float = 1f, force: Boolean = false) {
        val now = nowMs()
        val extrapolated = positionAt(now)
        val reported = positionMs.coerceAtLeast(0L)
        anchorMs = if (!force && initialized && this.playing && playing &&
            abs(reported - extrapolated) <= 700L) extrapolated else reported
        anchoredAtMs = now
        this.playing = playing
        this.speed = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
        initialized = true
    }

    fun positionMs(): Long = positionAt(nowMs())

    private fun positionAt(now: Long): Long {
        val elapsed = if (playing) ((now - anchoredAtMs).coerceAtLeast(0L) * speed).toLong() else 0L
        val position = (anchorMs + elapsed).coerceAtLeast(0L)
        return if (durationMs > 0L) position.coerceAtMost(durationMs) else position
    }

    fun reset() {
        anchorMs = 0L
        anchoredAtMs = nowMs()
        durationMs = 0L
        playing = false
        speed = 1f
        initialized = false
    }
}
