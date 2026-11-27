package com.luoh.music.lrc.ui.home.pop

import kotlin.math.cos
import kotlin.math.sin

internal data class PopVector(val x: Float, val y: Float)
internal data class PopVisualPose(val x: Float = 0f, val y: Float = 0f,
    val scale: Float = 1f, val rotation: Float = 0f, val alpha: Float = 1f)

/** Playback-stable choices. Drawing and seeking never consume a random generator. */
internal object PopVisualMotion {
    fun seed(time: Long, text: String, index: Int = 0): Long = time xor
        (text.hashCode().toLong() * 0x9e3779b9L) xor (index.toLong() * 0x85ebca6bL)

    fun choice(seed: Long, count: Int): Int {
        var value = seed
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return Math.floorMod(value xor (value ushr 31), count.toLong()).toInt()
    }

    fun layout(mode: String, seed: Long): String = if (mode == "random")
        listOf("staggered", "centered", "stairs", "wave")[choice(seed, 4)] else mode

    fun entrance(mode: String, seed: Long): String = if (mode == "random")
        listOf("scatter", "rise", "zoom", "converge", "disperse")[choice(seed, 5)] else mode

    fun transition(mode: String, seed: Long): String = if (mode == "random")
        listOf("fade", "slide", "zoom", "converge", "disperse")[choice(seed, 5)] else mode

    fun direction(mode: String, seed: Long): PopVector = when (mode) {
        "left" -> PopVector(-1f, 0f)
        "right" -> PopVector(1f, 0f)
        "up" -> PopVector(0f, -1f)
        "down" -> PopVector(0f, 1f)
        "horizontal" -> if (choice(seed, 2) == 0) PopVector(-1f, 0f) else PopVector(1f, 0f)
        "vertical" -> if (choice(seed, 2) == 0) PopVector(0f, -1f) else PopVector(0f, 1f)
        else -> direction(listOf("left", "right", "up", "down")[choice(seed, 4)], seed)
    }

    // C2 endpoints, with the peak speed before halfway: a gentle start and a longer landing.
    fun travel(fraction: Float): Float {
        val p = fraction.coerceIn(0f, 1f).toDouble()
        return (p * p * p * (20.0 + p * (-45.0 + p * (36.0 - 10.0 * p)))).coerceIn(0.0, 1.0).toFloat()
    }

    fun entranceOffset(mode: String, seed: Long, x: Float, y: Float, width: Float, height: Float,
        travelX: Float, travelY: Float, amount: Float): PopVector {
        val angle = choice(seed, 1024) * (Math.PI * 2 / 1024)
        return when (mode) {
            "scatter" -> PopVector((sin(angle) * travelX).toFloat(), (cos(angle) * travelY).toFloat())
            "rise" -> PopVector(0f, travelY)
            "converge" -> when (choice(seed, 4)) {
                0 -> PopVector(-x * amount, (height * .5f - y) * amount * .35f)
                1 -> PopVector((width - x) * amount, (height * .5f - y) * amount * .35f)
                2 -> PopVector((width * .5f - x) * amount * .35f, -y * amount)
                else -> PopVector((width * .5f - x) * amount * .35f, (height - y) * amount)
            }
            "disperse" -> PopVector((width * .5f - x) * amount, (height * .5f - y) * amount)
            else -> PopVector(0f, 0f)
        }
    }

    /** The same transition vocabulary for whole sentences and their short phrases. */
    fun phrase(mode: String, direction: PopVector, fraction: Float, entering: Boolean,
        width: Float, height: Float, amount: Float = 1f): PopVisualPose {
        val progress = travel(fraction)
        if ((entering && progress == 1f) || (!entering && progress == 0f)) return PopVisualPose()
        val remaining = (if (entering) 1f - progress else progress) * amount
        val alpha = if (entering) travel((fraction * 1.45f).coerceAtMost(1f)) else 1f - progress
        return when (mode) {
            "slide" -> PopVisualPose(direction.x * width * .18f * remaining * (if (entering) -1f else 1f),
                direction.y * height * .12f * remaining * (if (entering) -1f else 1f), alpha = alpha)
            "zoom" -> PopVisualPose(scale = 1f + remaining * (if (entering) -.12f else .08f), alpha = alpha)
            "converge" -> PopVisualPose(scale = 1f + remaining * (if (entering) .20f else -.18f), alpha = alpha)
            "disperse" -> PopVisualPose(scale = 1f + remaining * (if (entering) -.22f else .24f), alpha = alpha)
            else -> PopVisualPose(alpha = alpha)
        }
    }
}
