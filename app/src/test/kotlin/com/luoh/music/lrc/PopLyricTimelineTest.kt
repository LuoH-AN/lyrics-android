package com.luoh.music.lrc

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PopLyricTimelineTest {
    private val line = LyricLine(1000, "让音乐陪你", endMs = 7000, words = listOf(
        LyricWord(1000, 600, "让"), LyricWord(2400, 1200, "音乐"), LyricWord(5000, 1000, "陪你")))

    @Test fun parserTimingIsPreservedAndCanBeRevisitedAfterSeeking() {
        val plan = PopLyricTimeline.prepare(line)
        assertTrue(plan.wordTimed)
        assertEquals(line.text, plan.tokens.joinToString("") { it.text })
        assertEquals(listOf(1000L, 2400L, 5000L), plan.tokens.map { it.startMs })
        assertEquals(listOf(1600L, 3600L, 6000L), plan.tokens.map { it.endMs })
        assertEquals(0L, plan.nextChangeDelayMs(5100, 9000))
        assertEquals(0L, plan.nextChangeDelayMs(1100, 9000))
        assertEquals(310L, plan.nextChangeDelayMs(2000, 9000))
        assertEquals(1000L, plan.nextChangeDelayMs(7000, 9000))
        assertEquals(50L, plan.nextChangeDelayMs(8950, 9000))
    }

    @Test fun stableWordsSleepButEntrancesAndPassTransitionsRequestFrames() {
        val plan = PopLyricTimeline.prepare(line)
        assertEquals(910L, plan.nextChangeDelayMs(0, 9000))
        assertEquals(0L, plan.nextChangeDelayMs(910, 9000))
        assertEquals(0L, plan.nextChangeDelayMs(1329, 9000))
        assertEquals(270L, plan.nextChangeDelayMs(1330, 9000))
        assertEquals(0L, plan.nextChangeDelayMs(1600, 9000))
        assertEquals(0L, plan.nextChangeDelayMs(1879, 9000))
        assertEquals(430L, plan.nextChangeDelayMs(1880, 9000))
    }

    @Test fun translationsMissingTimingMismatchesAndTooManyWordsKeepTheWholeSentence() {
        for (candidate in listOf(line.copy(words = emptyList()),
            line.copy(words = listOf(LyricWord(1000, 2000, "其他版本"))),
            line.copy(text = "啊".repeat(65), words = List(65) { LyricWord(it * 100L, 100, "啊") }))) {
            val plan = PopLyricTimeline.prepare(candidate)
            assertFalse(plan.wordTimed)
            assertEquals(candidate.text, plan.tokens.single().text)
        }
        val translation = PopLyricTimeline.prepare(line, "Let the music stay")
        assertFalse(translation.wordTimed)
        assertEquals("Let the music stay", translation.tokens.single().text)
        assertEquals(1000L, translation.nextChangeDelayMs(1100, 9000))
    }

    @Test fun whitespaceAndComplexCharactersAreNotLostOrSplit() {
        val words = listOf(LyricWord(1000, 500, " 你好"), LyricWord(1500, 500, " 👩‍👩‍👧‍👦 "),
            LyricWord(2000, 500, "é! "))
        val text = words.joinToString("") { it.text }.trim()
        val plan = PopLyricTimeline.prepare(LyricLine(1000, text, words = words))
        assertTrue(plan.wordTimed)
        assertEquals(text, plan.tokens.joinToString("") { it.text })
        assertEquals(" 👩‍👩‍👧‍👦 ", plan.tokens[1].text)
        val rtl = LyricLine(1000, "مرحبا", words = listOf(LyricWord(1000, 500, "مر"), LyricWord(1500, 500, "حبا")))
        assertFalse(PopLyricTimeline.prepare(rtl).wordTimed)
        assertEquals(rtl.text, PopLyricTimeline.prepare(rtl).tokens.single().text)
    }

    @Test fun malformedClocksFallBackInsteadOfSchedulingNegativeOrOverflowedEvents() {
        val backwards = line.copy(words = listOf(LyricWord(2000, 1000, "让"),
            LyricWord(1000, 1000, "音乐"), LyricWord(3000, 1000, "陪你")))
        assertFalse(PopLyricTimeline.prepare(backwards).wordTimed)
        val overflow = LyricLine(1000, "让", words = listOf(LyricWord(1000, Long.MAX_VALUE, "让")))
        assertFalse(PopLyricTimeline.prepare(overflow).wordTimed)
        val noDuration = LyricLine(1000, "让音乐", words = listOf(LyricWord(1000, 0, "让"), LyricWord(1500, 0, "音乐")))
        assertEquals(1500L, PopLyricTimeline.prepare(noDuration).tokens[0].endMs)
        assertEquals(1501L, PopLyricTimeline.prepare(noDuration).tokens[1].endMs)
    }

    @Test fun sceneFitsBothPortraitAndLandscapeWithRoomForAnimatedGlyphs() {
        val widths = FloatArray(64) { if (it % 5 == 0) 100f else 40f }
        val heights = FloatArray(64) { 50f }
        for ((width, height) in listOf(320f to 480f, 700f to 240f, 160f to 100f)) {
            val packed = packPopLyrics(widths, heights, width, height, 8f)
            assertEquals(64, packed.slots.size)
            assertTrue(packed.scale in 0f..1f)
            for (index in widths.indices) {
                val slot = packed.slots[index]
                val halfWidth = (widths[index] * PopLyricMotion.MAX_SCALE + heights[index] * .52f) * packed.scale / 2f
                val halfHeight = (heights[index] * PopLyricMotion.MAX_SCALE + widths[index] * .52f) * packed.scale / 2f
                assertTrue(slot.x - halfWidth >= -.01f && slot.x + halfWidth <= width + .01f)
                assertTrue(slot.y - halfHeight >= -.01f && slot.y + halfHeight <= height + .01f)
                for (previous in 0 until index) {
                    val other = packed.slots[previous]
                    val otherWidth = (widths[previous] * PopLyricMotion.MAX_SCALE + heights[previous] * .52f) * packed.scale / 2f
                    val otherHeight = (heights[previous] * PopLyricMotion.MAX_SCALE + widths[previous] * .52f) * packed.scale / 2f
                    assertTrue(abs(slot.x - other.x) >= halfWidth + otherWidth - .01f ||
                        abs(slot.y - other.y) >= halfHeight + otherHeight - .01f)
                }
            }
        }
    }

    @Test fun longImportedTextHasBoundedShapingButRetainsItsFullSourceForSharing() {
        val text = "a".repeat(510) + "😀" + "b".repeat(300)
        val plan = PopLyricTimeline.prepare(LyricLine(1000, text, words = listOf(LyricWord(1000, 1000, text))))
        assertFalse(plan.wordTimed)
        assertEquals(text, plan.tokens.single().text)
        val preview = popLyricPreview(text)
        assertTrue(preview.length <= PopLyricTimeline.MAX_PREVIEW_CHARACTERS)
        assertTrue(preview.endsWith("…"))
        assertFalse(preview.dropLast(1).last().isHighSurrogate())
        assertEquals("短句", popLyricPreview("短句"))
    }

    @Test fun motionHasBoundedOvershootAndSettlesAtBothEnds() {
        assertEquals(0f, PopLyricMotion.enter(-1000), 0f)
        assertEquals(0f, PopLyricMotion.enter(0), 0f)
        assertEquals(1f, PopLyricMotion.enter(PopLyricMotion.DURATION_MS), 0f)
        assertEquals(1f, PopLyricMotion.enter(Long.MAX_VALUE), 0f)
        for (time in 0L..PopLyricMotion.DURATION_MS) assertTrue(PopLyricMotion.enter(time) in 0f..1.05f)
        assertEquals(0f, PopLyricMotion.pass(-1000), 0f)
        assertEquals(1f, PopLyricMotion.pass(1000), 0f)
    }
}
