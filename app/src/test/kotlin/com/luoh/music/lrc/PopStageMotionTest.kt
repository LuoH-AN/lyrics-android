package com.luoh.music.lrc

import org.junit.Assert.*
import org.junit.Test

class PopStageMotionTest {
    private val style = PopLyricsStyle(breathing = false)

    @Test fun adjacentSentencesCarryTheirCameraPositionAndVelocity() {
        val old = PopCameraPath(listOf(PopCameraWaypoint(1000, 1000,
            PopCameraPoint(0f, 0f), PopCameraPoint(.2f, -.1f))))
        val next = PopCameraPath(listOf(PopCameraWaypoint(1410, 400,
            PopCameraPoint(0f, 0f), PopCameraPoint(-.2f, .1f)),
            PopCameraWaypoint(1900, 400, PopCameraPoint(-.2f, .1f), PopCameraPoint(0f, 0f))))
        val joined = PopStageMotion.connect(next, old, 1500)
        val before = PopStageMotion.sample(1499, old, style)
        val at = PopStageMotion.sample(1500, old, style)
        val start = PopStageMotion.sample(1500, joined, style)
        val after = PopStageMotion.sample(1501, joined, style)
        assertEquals(at, start)
        assertEquals(at.x - before.x, after.x - start.x, .00001f)
        assertEquals(at.y - before.y, after.y - start.y, .00001f)
        assertEquals(PopStagePose(), PopStageMotion.sample(2400, joined, style))
        assertSame(next, PopStageMotion.connect(next, old, 10000))
        assertSame(old, PopStageMotion.connect(old, joined, 1000))
    }

    @Test fun driftMovesWithoutBreathingAndBothCameraModesRespectTheViewportBounds() {
        val path = PopCameraPath(emptyList(), PopCameraBounds(-.015f, .015f, -.012f, .012f))
        for (mode in listOf("drift", "orbit")) {
            val configured = style.copy(camera = mode)
            val poses = (0L..10000L step 50).map { PopStageMotion.sample(it, path, configured) }
            assertTrue(poses.map { it.x }.toSet().size > 20)
            assertTrue(poses.all { it.x in -0.015f..0.015f && it.y in -0.012f..0.012f })
            assertTrue(poses.all { it.zoom == 1f })
            assertEquals(PopStagePose(), PopStageMotion.sample(1000, path, configured, allowMotion = false))
        }
    }
}
