package com.luoh.music.lrc

import com.luoh.music.lrc.ui.home.pop.PopLyricMotion
import com.luoh.music.lrc.ui.home.pop.PopVector
import com.luoh.music.lrc.ui.home.pop.PopVisualMotion
import com.luoh.music.lrc.ui.home.pop.PopVisualPose
import org.junit.Assert.*
import org.junit.Test

class PopVisualMotionTest {
    @Test fun randomChoicesAreStableAndCoverEveryEffectAndDirection() {
        val seeds = (0..1000).map { PopVisualMotion.seed(it * 1000L, "歌词 $it") }
        val entrances = seeds.map { PopVisualMotion.entrance("random", it) }
        assertEquals(setOf("scatter", "rise", "zoom", "converge", "disperse"), entrances.toSet())
        assertEquals(entrances, seeds.map { PopVisualMotion.entrance("random", it) })
        assertEquals(setOf("fade", "slide", "zoom", "converge", "disperse"),
            seeds.map { PopVisualMotion.transition("random", it) }.toSet())
        assertEquals(4, seeds.map { PopVisualMotion.direction("random", it) }.toSet().size)
        assertEquals(setOf(PopVector(-1f, 0f), PopVector(1f, 0f)),
            seeds.map { PopVisualMotion.direction("horizontal", it) }.toSet())
        assertEquals(setOf(PopVector(0f, -1f), PopVector(0f, 1f)),
            seeds.map { PopVisualMotion.direction("vertical", it) }.toSet())
    }

    @Test fun travelIsMonotonicWithSoftEndpointsAndACompleteLanding() {
        assertEquals(0f, PopVisualMotion.travel(-1f), 0f)
        assertEquals(1f, PopVisualMotion.travel(2f), 0f)
        val samples = (0..1000).map { PopVisualMotion.travel(it / 1000f) }
        samples.zipWithNext().forEach { (a, b) -> assertTrue(b >= a - .00001f) }
        assertTrue(samples[1] < .00001f)
        assertTrue(1f - samples[999] < .00001f)
        assertTrue(samples[500] > .5f)
    }

    @Test fun slidesEnterAgainstThePushAndLeaveInTheSelectedDirection() {
        for (mode in listOf("left", "right", "up", "down")) {
            val direction = PopVisualMotion.direction(mode, 0)
            val enter = PopVisualMotion.phrase("slide", direction, 0f, true, 400f, 700f)
            val exit = PopVisualMotion.phrase("slide", direction, 1f, false, 400f, 700f)
            assertEquals(-enter.x, exit.x, .001f)
            assertEquals(-enter.y, exit.y, .001f)
            assertEquals(0f, enter.alpha, 0f)
            assertEquals(PopVisualPose(), PopVisualMotion.phrase("slide", direction, 1f, true, 400f, 700f))
            assertEquals(0f, exit.alpha, 0f)
        }
    }

    @Test fun convergeComesFromAllFourEdgesAndDisperseStartsAtTheCenter() {
        val offsets = (0..100).map {
            PopVisualMotion.entranceOffset("converge", it.toLong(), 200f, 300f, 400f, 600f, 72f, 48f, 1f)
        }.toSet()
        assertEquals(setOf(PopVector(-200f, 0f), PopVector(200f, 0f),
            PopVector(0f, -300f), PopVector(0f, 300f)), offsets)
        val offset = PopVisualMotion.entranceOffset("disperse", 0, 80f, 90f, 400f, 600f, 72f, 48f, 1f)
        assertEquals(200f, 80f + offset.x, 0f)
        assertEquals(300f, 90f + offset.y, 0f)
    }

    @Test fun springHasOneRestrainedReboundAndSettlesWithoutAnEndJump() {
        val samples = (0L..600L).map { PopLyricMotion.enter(it, 600) }
        assertTrue(samples.max() in 1.01f..1.05f)
        assertEquals(0f, samples.first(), 0f)
        assertEquals(1f, samples.last(), 0f)
        assertTrue(kotlin.math.abs(samples[600] - samples[599]) < .0001f)
        assertTrue(samples.zipWithNext().maxOf { (a, b) -> kotlin.math.abs(b - a) } < .02f)
    }
}
