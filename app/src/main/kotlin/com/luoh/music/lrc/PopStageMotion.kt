package com.luoh.music.lrc

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin

internal data class PopStagePose(val x: Float = 0f, val y: Float = 0f, val zoom: Float = 1f, val rotation: Float = 0f)

/** A single camera for the entire lyric field and its album background. No per-word camera objects. */
internal object PopStageMotion {
    private val wave = FloatArray(257) { sin(it / 256.0 * Math.PI * 2).toFloat() }
    private fun wave(time: Long, period: Long, offset: Int = 0): Float {
        val index = Math.floorMod(time, period) * 256 / period
        return wave[(index.toInt() + offset) % 256]
    }

    fun sample(time: Long, timeline: PopLyricTimeline?, style: PopLyricsStyle, allowMotion: Boolean = true): PopStagePose {
        if (!allowMotion || style.camera == "off" || style.cameraPercent == 0 || timeline == null) return PopStagePose()
        val amount = style.cameraPercent / 100f
        val floatY = if (style.breathing) wave(time, 7000) * .014f * amount else 0f
        val floatX = if (style.breathing) wave(time, 8500, 40) * .007f * amount else 0f
        val breathe = if (style.breathing) wave(time, 7000, 64) * .008f * amount else 0f
        if (style.camera == "drift") return PopStagePose(floatX, floatY, 1.02f + breathe)
        val tokens = timeline.tokens
        val index = tokens.indexOfLast { time >= it.startMs - style.lookaheadMs + it.visualDelayMs }
        if (index < 0) return PopStagePose(floatX, floatY, 1.02f + breathe)
        val token = tokens[index]
        val phase = PopLyricMotion.enter(time - (token.startMs - style.lookaheadMs + token.visualDelayMs),
            style.entranceMs.toLong()).coerceIn(0f, 1f)
        fun targetX(i: Int) = ((i % 3 + 3) % 3 - 1) * .022f * amount
        fun targetY(i: Int) = (if (i % 2 == 0) -1f else 1f) * .019f * amount
        val panX = targetX(index - 1) + (targetX(index) - targetX(index - 1)) * phase
        val panY = targetY(index - 1) + (targetY(index) - targetY(index - 1)) * phase
        val turn = (if (index % 2 == 0) -.7f else .7f) * amount * phase
        val pulse = (1f - phase) * .025f * amount
        return PopStagePose(panX + floatX, panY + floatY, 1.025f + breathe + pulse, turn)
    }
}

internal fun Modifier.popStageCamera(frame: PopPlaybackFrame?, style: PopLyricsStyle, allowMotion: Boolean,
    background: Boolean = false): Modifier {
    if (frame == null || !allowMotion || style.camera == "off" || style.cameraPercent == 0) return this
    return graphicsLayer {
        val pose = PopStageMotion.sample(frame.position.value, if (frame.interlude == null) frame.timeline else null, style)
        translationX = pose.x * size.width
        translationY = pose.y * size.height
        rotationZ = pose.rotation
        val overscan = if (background) 1.3f else 1f
        scaleX = pose.zoom * overscan
        scaleY = pose.zoom * overscan
    }
}
