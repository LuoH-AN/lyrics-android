package com.luoh.music.lrc

import com.luoh.music.lrc.ui.home.pop.PopLyricTimeline
import com.luoh.music.lrc.ui.home.pop.PopLyricToken
import com.luoh.music.lrc.ui.home.pop.PopLyricsStyle
import com.luoh.music.lrc.ui.home.pop.PopSustainMotion
import com.luoh.music.lrc.ui.home.pop.PopSustainPose
import com.luoh.music.lrc.ui.home.pop.popGlyphExtent
import com.luoh.music.lrc.ui.home.pop.popLyricRenderTiming
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class PopSustainMotionTest {
    @Test fun realLongNotesTrembleAfterTheirEntranceAndSettleBeforeTheirEnd() {
        val token = PopLyricToken("爱", 1000, 4000)
        val before = token.copy()
        val plan = requireNotNull(PopSustainMotion.plan(token, 420, 137, 100))
        assertEquals(PopSustainPose(), PopSustainMotion.sample(plan, 1000))
        assertEquals(PopSustainPose(), PopSustainMotion.sample(plan, plan.startMs))
        val mid = PopSustainMotion.sample(plan, 2400)
        assertTrue(abs(mid.xDp) + abs(mid.yDp) + abs(mid.degrees) > .1f)
        assertEquals(mid, PopSustainMotion.sample(plan, 2400))
        assertEquals(PopSustainPose(), PopSustainMotion.sample(plan, 4000))
        val tail = PopSustainMotion.sample(plan, 3999)
        assertTrue(abs(tail.xDp) + abs(tail.yDp) + abs(tail.degrees) < .001f)
        assertEquals(before, token)
        assertNull(PopSustainMotion.plan(token.copy(endMs = 1400), 420, 137, 100))
        assertNull(PopSustainMotion.plan(token, 420, 137, 0))
    }

    @Test fun heldNotesKeepRequestingFramesEvenWithTheCameraAndBreathingOff() {
        val token = PopLyricToken("爱", 1000, 4000)
        val timeline = PopLyricTimeline(listOf(token), true)
        val style = PopLyricsStyle(camera = "off", breathing = false)
        assertEquals(0L, popLyricRenderTiming(timeline, style).nextChangeDelayMs(timeline.tokens, 2400, 6000))
        assertTrue(popLyricRenderTiming(timeline, style.copy(sustainShakePercent = 0))
            .nextChangeDelayMs(timeline.tokens, 2400, 6000) > 0L)
        assertTrue(popLyricRenderTiming(PopLyricTimeline(listOf(token), false), style).sustain.all { it == null })
    }

    @Test fun rotationAndTranslationAlwaysStayInsideTheReservedShakeBounds() {
        val plan = requireNotNull(PopSustainMotion.plan(PopLyricToken("爱", 1000, 4000), 420, 0, 200))
        val scale = 1.7f
        val angle = 20f
        val extent = popGlyphExtent(80f, 50f, scale, angle + plan.reachDegrees)
        for (now in plan.startMs..plan.endMs step 8) {
            val pose = PopSustainMotion.sample(plan, now)
            assertTrue(abs(pose.xDp) <= plan.reachXDp && abs(pose.yDp) <= plan.reachYDp)
            assertTrue(abs(pose.degrees) <= plan.reachDegrees)
            val radians = (angle + pose.degrees) * Math.PI / 180
            val halfWidth = (80 * abs(cos(radians)) + 50 * abs(sin(radians))) * scale / 2 + abs(pose.xDp) * scale
            val halfHeight = (50 * abs(cos(radians)) + 80 * abs(sin(radians))) * scale / 2 + abs(pose.yDp) * scale
            assertTrue(halfWidth <= extent.width / 2 + plan.reachXDp * scale + .001f)
            assertTrue(halfHeight <= extent.height / 2 + plan.reachYDp * scale + .001f)
        }
    }
}
