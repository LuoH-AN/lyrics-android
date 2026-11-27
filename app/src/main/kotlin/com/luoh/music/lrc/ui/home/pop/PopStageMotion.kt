package com.luoh.music.lrc.ui.home.pop

import kotlin.math.sin

internal data class PopStagePose(val x: Float = 0f, val y: Float = 0f, val zoom: Float = 1f, val rotation: Float = 0f)
internal data class PopCameraPoint(val x: Float, val y: Float)
internal data class PopCameraBounds(val minX: Float = -.30f, val maxX: Float = .30f,
    val minY: Float = -.34f, val maxY: Float = .34f)
internal data class PopCameraWaypoint(val startMs: Long, val durationMs: Long,
    val from: PopCameraPoint, val target: PopCameraPoint, val velocity: PopCameraPoint = PopCameraPoint(0f, 0f),
    val acceleration: PopCameraPoint = PopCameraPoint(0f, 0f))
internal data class PopCameraPath(val waypoints: List<PopCameraWaypoint>, val bounds: PopCameraBounds = PopCameraBounds(),
    val initialBounds: PopCameraBounds? = null)

/** Cache a continuous camera trajectory from actual measured word centers, never token parity. */
internal object PopStageMotion {
    const val FRAME_INTERVAL_NS = 0L
    private val wave = FloatArray(257) { sin(it / 256.0 * Math.PI * 2).toFloat() }
    private fun wave(time: Long, period: Long, offset: Int = 0): Float {
        val sample = Math.floorMod(time, period).toFloat() * 256f / period
        val index = sample.toInt()
        val from = wave[(index + offset) % 256]
        val to = wave[(index + offset + 1) % 256]
        return from + (to - from) * (sample - index)
    }
    private fun at(point: PopCameraWaypoint, time: Long): PopCameraPoint {
        val p = ((time - point.startMs).toFloat() / point.durationMs).coerceIn(0f, 1f)
        val eased = p * p * p * (10f + p * (-15f + 6f * p))
        val tangent = p * (1f + p * p * (-6f + p * (8f - 3f * p))) * point.durationMs
        val curve = .5f * p * p * (1f - p) * (1f - p) * (1f - p) * point.durationMs * point.durationMs
        return PopCameraPoint(point.from.x + (point.target.x - point.from.x) * eased + point.velocity.x * tangent + point.acceleration.x * curve,
            point.from.y + (point.target.y - point.from.y) * eased + point.velocity.y * tangent + point.acceleration.y * curve)
    }

    private fun velocity(point: PopCameraWaypoint, time: Long): PopCameraPoint {
        if (time < point.startMs || time >= point.startMs + point.durationMs) return PopCameraPoint(0f, 0f)
        val p = (time - point.startMs).toFloat() / point.durationMs
        val eased = 30f * p * p * (1f - p) * (1f - p) / point.durationMs
        val tangent = 1f + p * p * (-18f + p * (32f - 15f * p))
        val curve = .5f * p * (2f + p * (-9f + p * (12f - 5f * p))) * point.durationMs
        return PopCameraPoint((point.target.x - point.from.x) * eased + point.velocity.x * tangent + point.acceleration.x * curve,
            (point.target.y - point.from.y) * eased + point.velocity.y * tangent + point.acceleration.y * curve)
    }

    private fun acceleration(point: PopCameraWaypoint, time: Long): PopCameraPoint {
        if (time < point.startMs || time >= point.startMs + point.durationMs) return PopCameraPoint(0f, 0f)
        val p = (time - point.startMs).toFloat() / point.durationMs
        val eased = p * (60f + p * (-180f + 120f * p)) / point.durationMs / point.durationMs
        val tangent = p * (-36f + p * (96f - 60f * p)) / point.durationMs
        val curve = 1f + p * (-9f + p * (18f - 10f * p))
        return PopCameraPoint((point.target.x - point.from.x) * eased + point.velocity.x * tangent + point.acceleration.x * curve,
            (point.target.y - point.from.y) * eased + point.velocity.y * tangent + point.acceleration.y * curve)
    }

    fun path(timeline: PopLyricTimeline, centers: List<PopCameraPoint>, style: PopLyricsStyle,
        bounds: PopCameraBounds = PopCameraBounds(), timing: PopLyricRenderTiming? = null): PopCameraPath {
        require(centers.size == timeline.tokens.size)
        val available = (timeline.endMs - timeline.startMs).coerceAtLeast(1L)
        if (!timeline.wordTimed || (style.adaptiveTiming && available <= 100L) ||
            centers.all { kotlin.math.abs(it.x) < .0001f && kotlin.math.abs(it.y) < .0001f })
            return PopCameraPath(emptyList(), bounds)
        val points = ArrayList<PopCameraWaypoint>(centers.size)
        val profile = timing ?: popLyricRenderTiming(timeline, style)
        val baseDuration = (320f * 100f / style.cameraSpeedPercent).toLong().coerceIn(140L, 640L)
        val duration = if (style.adaptiveTiming && available < 800L) minOf(baseDuration, (available / 2).coerceAtLeast(80L)) else baseDuration
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
            val start = token.startMs - profile.lookaheadMs[index] + token.visualDelayMs
            val previous = points.lastOrNull()
            val from = previous?.let { at(it, start) } ?: PopCameraPoint(0f, 0f)
            val nextOnset = timeline.tokens.getOrNull(until)?.let {
                it.startMs - profile.lookaheadMs[until] + it.visualDelayMs
            } ?: token.endMs
            // Resolve each focus while its word is being sung, rather than trailing several words.
            val focusDuration = if (style.adaptiveTiming) minOf(duration,
                ((nextOnset - start) * .95f).toLong().coerceAtLeast(120L)) else duration
            points += PopCameraWaypoint(start, focusDuration, from,
                PopCameraPoint((-center.x * amount).coerceIn(maxOf(-.30f, bounds.minX), minOf(.30f, bounds.maxX)) * .85f,
                    (-center.y * amount).coerceIn(maxOf(-.34f, bounds.minY), minOf(.34f, bounds.maxY)) * .85f),
                previous?.let { velocity(it, start) } ?: PopCameraPoint(0f, 0f),
                previous?.let { acceleration(it, start) } ?: PopCameraPoint(0f, 0f))
            index = until
        }
        if (timeline.wordTimed && points.isNotEmpty()) {
            val end = timeline.tokens.maxOf { it.endMs }
            val previous = points.last()
            points += PopCameraWaypoint(end, (duration * 1.5f).toLong(), at(previous, end),
                PopCameraPoint(0f, 0f), velocity(previous, end), acceleration(previous, end))
        }
        return PopCameraPath(points, bounds)
    }

    /** A fixed boundary sample makes a transition reproducible after pause or seek. */
    fun flow(outgoing: PopCameraPath?, incoming: PopCameraPath?, boundaryMs: Long,
        style: PopLyricsStyle, width: Float, height: Float): PopVector {
        if (style.camera == "off" || style.cameraPercent == 0) return PopVector(0f, 0f)
        val before = sample(boundaryMs - 100L, outgoing, style)
        val atBoundary = sample(boundaryMs, outgoing, style)
        val flow = PopVector((atBoundary.x - before.x) * width, (atBoundary.y - before.y) * height)
        if (kotlin.math.hypot(flow.x, flow.y) >= .8f) return flow
        val first = incoming?.waypoints?.firstOrNull() ?: return PopVector(0f, 0f)
        val from = sample(first.startMs, incoming, style)
        val to = sample(first.startMs + minOf(120L, first.durationMs / 2), incoming, style)
        return PopVector((to.x - from.x) * width, (to.y - from.y) * height)
    }

    fun nextChangeDelayMs(time: Long, path: PopCameraPath?, style: PopLyricsStyle): Long {
        if (style.camera !in listOf("follow", "orbit") || style.cameraPercent == 0 || path == null) return 1000L
        val point = path.waypoints.lastOrNull { it.startMs <= time }
        if (point != null && time - point.startMs < point.durationMs) return 0L
        return path.waypoints.firstOrNull { it.startMs > time }?.let { (it.startMs - time).coerceIn(1L, 1000L) } ?: 1000L
    }

    /** Carry the outgoing camera's position and velocity through an adjacent sentence. */
    fun connect(path: PopCameraPath, previous: PopCameraPath?, now: Long): PopCameraPath {
        val first = path.waypoints.firstOrNull() ?: return path
        val old = previous?.waypoints?.lastOrNull { it.startMs <= now } ?: return path
        if (first.startMs < previous.waypoints.first().startMs ||
            now - old.startMs > old.durationMs + 1500L || now - first.startMs !in 0L..500L) return path
        val points = ArrayList<PopCameraWaypoint>(path.waypoints.size)
        for ((index, point) in path.waypoints.withIndex()) {
            if (index > 0 && point.startMs <= now) continue
            val start = if (index == 0) now else point.startMs
            val predecessor = points.lastOrNull()
            val from = predecessor?.let { at(it, start) } ?: at(old, now)
            val speed = predecessor?.let { velocity(it, start) } ?: velocity(old, now)
            val bend = predecessor?.let { acceleration(it, start) } ?: acceleration(old, now)
            points += point.copy(startMs = start, from = from, velocity = speed, acceleration = bend)
        }
        return path.copy(waypoints = points, initialBounds = previous.bounds)
    }

    fun sample(time: Long, path: PopCameraPath?, style: PopLyricsStyle, allowMotion: Boolean = true): PopStagePose {
        if (!allowMotion || style.camera == "off" || style.cameraPercent == 0 || path == null) return PopStagePose()
        val first = path.waypoints.firstOrNull()
        val blend = if (first == null) 1f else PopVisualMotion.travel((time - first.startMs).toFloat() / first.durationMs)
        val bounds = path.initialBounds?.let { old ->
            PopCameraBounds(old.minX + (path.bounds.minX - old.minX) * blend,
                old.maxX + (path.bounds.maxX - old.maxX) * blend,
                old.minY + (path.bounds.minY - old.minY) * blend,
                old.maxY + (path.bounds.maxY - old.maxY) * blend)
        } ?: path.bounds
        val amount = style.cameraPercent / 100f
        val end = path.waypoints.lastOrNull()?.let { it.startMs + it.durationMs }
        val life = if (end == null) 1f else 1f - PopVisualMotion.travel((time - end) / 700f)
        val floatY = if (style.breathing) wave(time, 7000) * .010f * amount * life else 0f
        val floatX = if (style.breathing) wave(time, 8500, 40) * .006f * amount * life else 0f
        val breathe = if (style.breathing) wave(time, 7000, 64) * .006f * amount * life else 0f
        if (style.camera == "drift") return PopStagePose(
            (wave(time, 9200) * .032f * amount * life + floatX).coerceIn(bounds.minX, bounds.maxX),
            (wave(time, 7600, 64) * .024f * amount * life + floatY).coerceIn(bounds.minY, bounds.maxY), 1f + breathe)
        val point = path.waypoints.lastOrNull { it.startMs <= time }
        val position = point?.let { at(it, time) } ?: PopCameraPoint(0f, 0f)
        val orbitX = if (style.camera == "orbit") wave(time, 6000) * .022f * amount * life else 0f
        val orbitY = if (style.camera == "orbit") wave(time, 6000, 64) * .016f * amount * life else 0f
        return PopStagePose((position.x + floatX + orbitX).coerceIn(bounds.minX, bounds.maxX),
            (position.y + floatY + orbitY).coerceIn(bounds.minY, bounds.maxY), 1f + breathe)
    }
}
