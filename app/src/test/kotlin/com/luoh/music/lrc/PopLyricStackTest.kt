package com.luoh.music.lrc

import org.junit.Assert.*
import org.junit.Test

class PopLyricStackTest {
    private fun flow(down: Boolean = false) = PopStackFlow(
        (0..8).map { PopStackMetric(1000L + it * 2000L, 100f) }, 500f, 20f,
        if (down) 35 else 65, 400, down)

    @Test fun completedLyricsStayUntilNewLyricsPushThemOutsideTheViewport() {
        val flow = flow()
        assertEquals(listOf(0), flow.at(2900).visible.map { it.index })
        assertEquals(listOf(0, 1), flow.at(3500).visible.map { it.index })
        assertEquals(listOf(0, 1, 2), flow.at(5500).visible.map { it.index })
        assertFalse(flow.at(9500).visible.any { it.index == 0 })
        assertTrue(flow.at(9500).visible.any { it.index == 4 })
        // Ending the song or a long interlude does not remove the last screen of lyrics.
        assertEquals(flow.at(17500).visible, flow.at(100000).visible)
    }

    @Test fun seekingAndPausingReproduceTheSamePlacements() {
        val flow = flow()
        val before = flow.at(3200)
        flow.at(17500)
        assertEquals(before, flow.at(3200))
        assertEquals(before, flow.at(3200))
        assertEquals(before, flow().at(3200))
        assertEquals(3400L, before.motionUntilMs)
        assertNull(flow.at(3400).motionUntilMs)
        assertNotEquals(before.visible, flow.at(3201).visible)
    }

    @Test fun onlyStartedPhrasesAreVisibleAndTheZeroMotionSettingIsImmediate() {
        val metrics = listOf(PopStackMetric(1000, 100f), PopStackMetric(2000, 100f))
        val flow = PopStackFlow(metrics, 500f, 20f, 65, 0)
        assertTrue(flow.at(999).visible.isEmpty())
        assertEquals(listOf(0), flow.at(1999).visible.map { it.index })
        assertEquals(listOf(0, 1), flow.at(2000).visible.map { it.index })
        assertNull(flow.at(2000).motionUntilMs)
        assertEquals(flow.at(2000), flow.at(2000, animate = false))
    }

    @Test fun fastAndSimultaneousOnsetsKeepSourceTimingAndAvoidJumpingAtTheNextOnset() {
        val metrics = listOf(PopStackMetric(1000, 60f), PopStackMetric(1000, 60f),
            PopStackMetric(1100, 60f), PopStackMetric(1200, 60f))
        val flow = PopStackFlow(metrics, 500f, 10f, 65, 1200)
        assertEquals(1, flow.at(1000).current)
        assertEquals(listOf(0, 1), flow.at(1000).visible.map { it.index })
        val before = flow.at(1199).visible.first { it.index == 0 }.top
        val after = flow.at(1200).visible.first { it.index == 0 }.top
        assertTrue(kotlin.math.abs(before - after) < .1f)
        assertEquals(2400L, flow.at(1200).motionUntilMs)
    }

    @Test fun reverseDirectionMirrorsPlacementWithoutChangingReadingOrderOrTiming() {
        for (time in listOf(999L, 1000L, 3200L, 5500L, 9500L)) {
            val up = flow().at(time)
            val down = flow(true).at(time)
            assertEquals(up.current, down.current)
            assertEquals(up.visible.map { it.index }, down.visible.map { it.index })
            up.visible.zip(down.visible).forEach { (a, b) ->
                assertEquals(500f - a.bottom, b.top, .001f)
            }
        }
    }

    @Test fun largerGapsPushHistoryOutEarlierAndVariableHeightTextDoesNotOverlap() {
        val metrics = listOf(PopStackMetric(0, 80f), PopStackMetric(1000, 180f), PopStackMetric(2000, 100f))
        val tight = PopStackFlow(metrics, 500f, 0f, 65, 0).at(2000)
        val roomy = PopStackFlow(metrics, 500f, 80f, 65, 0).at(2000)
        assertTrue(tight.visible.any { it.index == 0 })
        assertFalse(roomy.visible.any { it.index == 0 })
        roomy.visible.zipWithNext().forEach { (a, b) -> assertTrue(b.top >= a.bottom + 79f) }
    }

    @Test fun emptyLyricsAndSmallScreensRemainDefined() {
        assertEquals(PopStackWindow(-1, emptyList(), null), PopStackFlow(emptyList(), 50f, 0f, 65, 400).at(1000))
        val flow = PopStackFlow(listOf(PopStackMetric(0, 80f)), 50f, 0f, 10, 400)
        val placement = flow.at(0).visible.single()
        assertTrue(placement.top <= 0f && placement.bottom >= 50f)
    }

    @Test fun horizontalPushesUseActualWidthsAndKeepOldLyricsUntilTheyLeave() {
        val metrics = (0..10).map { PopStackMetric(it * 1000L, 80f, 70f) }
        for (direction in listOf("left", "right")) {
            val flow = PopStackFlow(metrics, 500f, 10f, 50, 400, viewportWidth = 400f, direction = direction)
            assertEquals(listOf(0, 1), flow.at(1500).visible.map { it.index })
            assertFalse(flow.at(9500).visible.any { it.index == 0 })
            val blocks = flow.at(2500).visible.sortedBy { it.left }
            blocks.zipWithNext().forEach { (a, b) -> assertTrue(a.right + 9f <= b.left) }
            blocks.forEach { assertEquals(70f, it.right - it.left, .001f) }
        }
    }

    @Test fun randomDirectionChangesStayDeterministicAndNeverReviveOffscreenHistory() {
        val metrics = (0..100).map { PopStackMetric(it * 1000L, 80f, 90f, it.toLong()) }
        for (direction in listOf("horizontal", "vertical", "random")) {
            val flow = PopStackFlow(metrics, 500f, 10f, 50, 400, viewportWidth = 400f, direction = direction)
            val retired = mutableSetOf<Int>()
            var previous = emptySet<Int>()
            for (index in metrics.indices) {
                val window = flow.at(index * 1000L + 500)
                val visible = window.visible.map { it.index }.toSet()
                retired.addAll(previous - visible)
                assertTrue(visible.intersect(retired).isEmpty())
                assertTrue(index in visible)
                val rects = window.visible
                for (a in rects.indices) for (b in a + 1 until rects.size) {
                    val x = rects[a]; val y = rects[b]
                    assertTrue(x.right <= y.left || y.right <= x.left || x.bottom <= y.top || y.bottom <= x.top)
                }
                assertEquals(window, flow.at(index * 1000L + 500))
                previous = visible
            }
            assertEquals(flow.at(2200), flow.at(2200))
            assertEquals(flow.at(2200), PopStackFlow(metrics, 500f, 10f, 50, 400,
                viewportWidth = 400f, direction = direction).at(2200))
        }
    }
}
