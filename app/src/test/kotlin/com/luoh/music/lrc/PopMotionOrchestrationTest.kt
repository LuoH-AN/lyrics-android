package com.luoh.music.lrc

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PopMotionOrchestrationTest {
    @Test fun denseWordsYieldWhileHeldNotesStayExpressiveWithoutChangingTheClock() {
        fun word(duration: Long, style: PopLyricsStyle = PopLyricsStyle()) = PopMotionOrchestration.words(
            PopLyricTimeline(listOf(PopLyricToken("唱", 1000, 1000 + duration)), true), style).single()
        val quick = word(120)
        val regular = word(500)
        val held = word(2400)
        assertTrue(quick.activeScale < regular.activeScale)
        assertTrue(quick.rotation < regular.rotation)
        assertTrue(quick.travel < regular.travel)
        assertTrue(held.activeScale > regular.activeScale)
        assertTrue(held.sustain > regular.sustain)
        val tokens = listOf(PopLyricToken("快", 1000, 1120), PopLyricToken("长", 1120, 4000))
        val timeline = PopLyricTimeline(tokens, true)
        val plan = PopMotionOrchestration.words(timeline, PopLyricsStyle())
        assertEquals(tokens, timeline.tokens)
        assertTrue("An immediately following held word must not turn a dense attack into a big leap",
            plan[1].activeScale < regular.activeScale)
        assertEquals(word(120, PopLyricsStyle(adaptiveEmphasis = false)).activeScale,
            word(2400, PopLyricsStyle(adaptiveEmphasis = false)).activeScale, 0f)
    }

    @Test fun simultaneousLettersShareOneDensityAndThreeStrengthsRemainOrdered() {
        val tokens = listOf(PopLyricToken("音", 1000, 1700), PopLyricToken("乐", 1000, 1700, 25),
            PopLyricToken("陪", 1700, 2400))
        val timeline = PopLyricTimeline(tokens, true)
        val plan = PopMotionOrchestration.words(timeline, PopLyricsStyle())
        assertEquals(plan[0], plan[1])
        val strengths = listOf("gentle", "balanced", "strong").map { level ->
            PopMotionOrchestration.words(timeline, PopLyricsStyle(motionStrength = level)).first()
        }
        strengths.zipWithNext().forEach { (a, b) -> assertTrue(a.activeScale < b.activeScale) }
        for (level in listOf("gentle", "balanced", "strong")) {
            val quiet = PopLyricsStyle(motionStrength = level, activeScalePercent = 100,
                rotationDegrees = 0, travelPercent = 0, cameraPercent = 0, rowMotionPercent = 0)
            assertEquals(1f, PopMotionOrchestration.words(timeline, quiet).first().activeScale, 0f)
            assertEquals(0, PopMotionOrchestration.cameraStyle(quiet).cameraPercent)
        }
    }

    @Test fun secondaryLayersYieldUnderHeavyCombinationAndRecoverSmoothly() {
        val mild = PopMotionOrchestration.balance(PopLyricsStyle(travelPercent = 0, rotationDegrees = 0,
            activeScalePercent = 100, rowMotionPercent = 0, camera = "off", transition = "fade"))
        val heavyStyle = PopLyricsStyle(travelPercent = 200, rotationDegrees = 30, activeScalePercent = 180,
            rowMotionPercent = 100, camera = "orbit", cameraPercent = 200, transition = "disperse", motionStrength = "strong")
        val heavy = PopMotionOrchestration.balance(heavyStyle)
        assertTrue(heavy.row < mild.row && heavy.camera < mild.camera && heavy.transition < mild.transition)
        assertTrue("Words keep priority over secondary row travel", heavy.word > heavy.row)
        assertEquals(PopMotionBalance(), PopMotionOrchestration.balance(heavyStyle.copy(coordinateMotion = false)))
        for (row in listOf(false, true)) {
            val samples = (0..1000).map { PopMotionOrchestration.handoffGain(it / 1000f, true, row) }
            assertTrue(samples.all { it in .4f..1f })
            assertEquals(1f, samples.last(), 0f)
            samples.zipWithNext().forEach { (a, b) -> assertTrue(b >= a - .00001f) }
            assertTrue(abs(samples[1] - samples[0]) < .00001f)
            assertTrue(abs(samples[1000] - samples[999]) < .00001f)
        }
    }

    @Test fun shallowStagesYieldMotionBeforeReducingTypographyAndRespectTheOptOut() {
        val style = PopLyricsStyle(motionStrength = "strong", rotationDegrees = 30)
        val word = PopMotionOrchestration.words(PopLyricTimeline(listOf(PopLyricToken("音乐", 1000, 2600)), true), style).single()
        val spacious = PopMotionOrchestration.spatial(style, 100f, 800f)
        val shallow = PopMotionOrchestration.spatial(style, 100f, 210f)
        assertEquals(PopSpatialBudget(), spacious)
        val fitted = PopMotionOrchestration.fitWord(word, shallow)
        assertTrue(fitted.activeScale < word.activeScale && fitted.activeScale > 1f)
        assertTrue(fitted.rotation < word.rotation && fitted.rotation > 0f)
        assertTrue(fitted.travel < word.travel)
        assertTrue(shallow.row < spacious.row)
        assertEquals(PopSpatialBudget(), PopMotionOrchestration.spatial(style.copy(coordinateMotion = false), 100f, 100f))
        val extent = popGlyphExtent(240f, 100f, word.maxScale, 30f * word.rotation)
        val fittedExtent = popGlyphExtent(240f, 100f, fitted.maxScale, 30f * fitted.rotation)
        assertTrue("Yielding motion must leave more height for readable native type", fittedExtent.height < extent.height * .7f)
    }

    @Test fun flowGuidesAutomaticDirectionsButPreservesExplicitChoicesAndAxisConstraints() {
        val flow = PopVector(10f, -4f)
        for (mode in listOf("left", "right", "up", "down")) assertEquals(
            PopVisualMotion.direction(mode, 137), PopMotionOrchestration.direction(mode, 137, flow, true))
        assertEquals(PopVector(1f, 0f), PopMotionOrchestration.direction("random", 137, flow, true))
        assertEquals(PopVector(1f, 0f), PopMotionOrchestration.direction("horizontal", 137, flow, true))
        assertEquals(PopVector(0f, -1f), PopMotionOrchestration.direction("vertical", 137, flow, true))
        assertEquals(PopVisualMotion.direction("random", 137),
            PopMotionOrchestration.direction("random", 137, flow, false))
        assertEquals(PopVisualMotion.direction("random", 137),
            PopMotionOrchestration.direction("random", 137, PopVector(.01f, -.01f), true))
    }

    @Test fun zoomCarriesCameraFlowWithExactLandingAndDeterministicSeekSamples() {
        val style = PopLyricsStyle(transition = "zoom")
        val plan = PopMotionOrchestration.transition(style, 137, PopVector(10f, -2f))
        assertTrue(plan.drift.x > 0f && plan.drift.y < 0f)
        val start = PopMotionOrchestration.pose(plan, 0f, true, 400f, 700f)
        val end = PopMotionOrchestration.pose(plan, 1f, false, 400f, 700f)
        assertTrue(start.x < 0f && end.x > 0f)
        assertEquals(PopVisualPose(), PopMotionOrchestration.pose(plan, 1f, true, 400f, 700f))
        assertEquals(0f, end.alpha, 0f)
        val sample = PopMotionOrchestration.pose(plan, .35f, true, 400f, 700f)
        PopMotionOrchestration.pose(plan, .8f, true, 400f, 700f)
        assertEquals(sample, PopMotionOrchestration.pose(plan, .35f, true, 400f, 700f))
        val fixed = PopMotionOrchestration.transition(style.copy(flowTransitions = false), 137, PopVector(10f, -2f))
        assertEquals(PopVector(0f, 0f), fixed.drift)
        val slide = PopMotionOrchestration.transition(style.copy(transition = "slide", pushDirection = "left"), 137, PopVector(10f, -2f))
        assertEquals(PopVector(-1f, 0f), slide.direction)
        assertEquals(PopVector(0f, 0f), slide.drift)
    }
}
