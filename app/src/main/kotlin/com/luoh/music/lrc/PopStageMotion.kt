package com.luoh.music.lrc

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin

internal data class PopStagePose(val x: Float = 0f, val y: Float = 0f, val zoom: Float = 1f, val rotation: Float = 0f)
internal data class PopCameraPoint(val x: Float, val y: Float)
internal data class PopCameraBounds(val minX: Float = -.30f, val maxX: Float = .30f,
    val minY: Float = -.34f, val maxY: Float = .34f)
internal data class PopCameraWaypoint(val startMs: Long, val durationMs: Long,
    val from: PopCameraPoint, val target: PopCameraPoint, val velocity: PopCameraPoint = PopCameraPoint(0f, 0f))
internal data class PopCameraPath(val waypoints: List<PopCameraWaypoint>, val bounds: PopCameraBounds = PopCameraBounds())

/** Cache a continuous camera trajectory from actual measured word centers, never token parity. */
internal object PopStageMotion {
    const val FRAME_INTERVAL_NS = 16_000_000L
    private val wave = FloatArray(257) { sin(it / 256.0 * Math.PI * 2).toFloat() }
    private fun wave(time: Long, period: Long, offset: Int = 0): Float {
        val index = Math.floorMod(time, period) * 256 / period
        return wave[(index.toInt() + offset) % 256]
    }
    private fun at(point: PopCameraWaypoint, time: Long): PopCameraPoint {
        val progress = ((time - point.startMs).toFloat() / point.durationMs).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        val tangent = progress * (1f - progress) * (1f - progress) * point.durationMs
        return PopCameraPoint(point.from.x + (point.target.x - point.from.x) * eased + point.velocity.x * tangent,
            point.from.y + (point.target.y - point.from.y) * eased + point.velocity.y * tangent)
    }

    private fun velocity(point: PopCameraWaypoint, time: Long): PopCameraPoint {
        if (time < point.startMs || time >= point.startMs + point.durationMs) return PopCameraPoint(0f, 0f)
        val progress = (time - point.startMs).toFloat() / point.durationMs
        val eased = 6f * progress * (1f - progress) / point.durationMs
        val tangent = 1f - 4f * progress + 3f * progress * progress
        return PopCameraPoint((point.target.x - point.from.x) * eased + point.velocity.x * tangent,
            (point.target.y - point.from.y) * eased + point.velocity.y * tangent)
    }

    fun path(timeline: PopLyricTimeline, centers: List<PopCameraPoint>, style: PopLyricsStyle,
        bounds: PopCameraBounds = PopCameraBounds()): PopCameraPath {
        require(centers.size == timeline.tokens.size)
        val points = ArrayList<PopCameraWaypoint>(centers.size)
        val duration = (style.entranceMs * 1.2f).toLong().coerceIn(400L, 900L)
        val amount = style.cameraPercent / 100f
        var index = 0
        while (index < centers.size) {
            val token = timeline.tokens[index]
            var until = index + 1
            while (until < centers.size && timeline.tokens[until].startMs == token.startMs &&
                timeline.tokens[until].endMs == token.endMs) until++
            // A visual character cascade shares one word focus, avoiding a camera restart per letter.
            var x = 0f
            var y = 0f
            for (character in index until until) { x += centers[character].x; y += centers[character].y }
            val center = PopCameraPoint(x / (until - index), y / (until - index))
            val start = token.startMs - style.lookaheadMs + token.visualDelayMs
            val previous = points.lastOrNull()
            val from = previous?.let { at(it, start) } ?: PopCameraPoint(0f, 0f)
            points += PopCameraWaypoint(start, duration, from,
                PopCameraPoint((-center.x * amount).coerceIn(maxOf(-.30f, bounds.minX), minOf(.30f, bounds.maxX)) * .85f,
                    (-center.y * amount).coerceIn(maxOf(-.34f, bounds.minY), minOf(.34f, bounds.maxY)) * .85f),
                previous?.let { velocity(it, start) } ?: PopCameraPoint(0f, 0f))
            index = until
        }
        if (timeline.wordTimed && points.isNotEmpty()) {
            val end = timeline.tokens.maxOf { it.endMs }
            val previous = points.last()
            points += PopCameraWaypoint(end, (duration * 1.25f).toLong(), at(previous, end),
                PopCameraPoint(0f, 0f), velocity(previous, end))
        }
        return PopCameraPath(points, bounds)
    }

    fun nextChangeDelayMs(time: Long, path: PopCameraPath?, style: PopLyricsStyle): Long {
        if (style.camera != "follow" || style.cameraPercent == 0 || path == null) return 1000L
        val point = path.waypoints.lastOrNull { it.startMs <= time }
        if (point != null && time - point.startMs < point.durationMs) return 0L
        return path.waypoints.firstOrNull { it.startMs > time }?.let { (it.startMs - time).coerceIn(1L, 1000L) } ?: 1000L
    }

    fun sample(time: Long, path: PopCameraPath?, style: PopLyricsStyle, allowMotion: Boolean = true): PopStagePose {
        if (!allowMotion || style.camera == "off" || style.cameraPercent == 0 || path == null) return PopStagePose()
        val amount = style.cameraPercent / 100f
        val floatY = if (style.breathing) wave(time, 7000) * .014f * amount else 0f
        val floatX = if (style.breathing) wave(time, 8500, 40) * .007f * amount else 0f
        val breathe = if (style.breathing) wave(time, 7000, 64) * .008f * amount else 0f
        if (style.camera == "drift") return PopStagePose(floatX, floatY, 1f + breathe)
        val point = path.waypoints.lastOrNull { it.startMs <= time }
        val position = point?.let { at(it, time) } ?: PopCameraPoint(0f, 0f)
        return PopStagePose(position.x.coerceIn(path.bounds.minX, path.bounds.maxX) + floatX,
            position.y.coerceIn(path.bounds.minY, path.bounds.maxY) + floatY, 1f + breathe)
    }
}

internal fun Modifier.popStageCamera(frame: PopPlaybackFrame?, style: PopLyricsStyle, allowMotion: Boolean): Modifier {
    if (frame == null || !allowMotion || style.camera == "off" || style.cameraPercent == 0) return this
    return graphicsLayer {
        val pose = PopStageMotion.sample(frame.cameraPosition.value, if (frame.interlude == null) frame.cameraPath.value else null, style)
        translationX = pose.x * size.width
        translationY = pose.y * size.height
        rotationZ = pose.rotation
        scaleX = pose.zoom
        scaleY = pose.zoom
    }
}
