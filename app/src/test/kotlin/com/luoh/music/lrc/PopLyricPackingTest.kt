package com.luoh.music.lrc

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PopLyricPackingTest {
    @Test fun viewportFitEnlargesShortPhrasesAndCentersEveryComposedLayout() {
        for (mode in listOf("centered", "staggered", "stairs", "wave", "random")) {
            for ((width, height) in listOf(360f to 680f, 760f to 240f)) {
                for (glyphSize in listOf(22f, 42f, 70f)) {
                    val widths = floatArrayOf(glyphSize, glyphSize * .7f, glyphSize, glyphSize * 1.2f)
                    val heights = FloatArray(4) { glyphSize * 1.15f }
                    val extents = widths.map { PopGlyphExtent(it * 1.2f, glyphSize * 1.4f) }
                    val packed = packPopLyrics(widths, heights, width, height, 4f, mode, 1.2f, 0,
                        extents = extents, maxFitScale = 16f, indentPercent = 150, fillViewport = true)
                    val left = packed.slots.indices.minOf { packed.slots[it].x - extents[it].width * packed.scale / 2f }
                    val right = packed.slots.indices.maxOf { packed.slots[it].x + extents[it].width * packed.scale / 2f }
                    val top = packed.slots.indices.minOf { packed.slots[it].y - extents[it].height * packed.scale / 2f }
                    val bottom = packed.slots.indices.maxOf { packed.slots[it].y + extents[it].height * packed.scale / 2f }
                    assertEquals(width / 2f, (left + right) / 2f, .01f)
                    assertEquals(height / 2f, (top + bottom) / 2f, .01f)
                    assertTrue(left >= -.01f && right <= width + .01f && top >= -.01f && bottom <= height + .01f)
                    assertTrue("Sparse type must fill a physical stage dimension", (right - left) >= width * .75f || (bottom - top) >= height * .75f)
                    if (glyphSize == 22f) assertTrue("Small font preferences must not cap sparse stage type", packed.scale > 2f)
                }
            }
        }
    }

    @Test fun waveAndRandomLayoutsAreStableAndKeepEveryGlyphInsideTheStage() {
        for (mode in listOf("wave", "random")) for (seed in 0L..20L) {
            val widths = FloatArray(12) { if (it % 3 == 0) 55f else 30f }
            val heights = FloatArray(12) { 45f }
            val packed = packPopLyrics(widths, heights, 320f, 500f, 4f, mode, 1f, 0, layoutSeed = seed)
            assertEquals(packed, packPopLyrics(widths, heights, 320f, 500f, 4f, mode, 1f, 0, layoutSeed = seed))
            assertEquals(widths.size, packed.slots.size)
            for (index in widths.indices) {
                val point = packed.slots[index]
                assertTrue(point.x - widths[index] * packed.scale / 2f >= -.01f)
                assertTrue(point.x + widths[index] * packed.scale / 2f <= 320.01f)
                assertTrue(point.y - heights[index] * packed.scale / 2f >= -.01f)
                assertTrue(point.y + heights[index] * packed.scale / 2f <= 500.01f)
            }
        }
    }

    @Test fun sparsePagesEnlargeTheirTypeWithinTheActualMotionBounds() {
        for (mode in listOf("centered", "staggered", "stairs")) {
            val widths = floatArrayOf(42f, 42f, 42f)
            val heights = FloatArray(3) { 48f }
            val extents = widths.map { popGlyphExtent(it, 48f, 1.55f, 12f) }
            val packed = packPopLyrics(widths, heights, 800f, 320f, 4f, mode, 1.55f, 12,
                extents = extents, rowMotionReach = 12f, maxFitScale = 1.8f, horizontalMotionReach = 20f)
            assertTrue("Short pages should use the spare stage space for larger type", packed.scale > 1.3f)
            for (index in widths.indices) {
                val slot = packed.slots[index]
                val halfWidth = (extents[index].width / 2f + 20f) * packed.scale
                val halfHeight = (extents[index].height / 2f + 12f) * packed.scale
                assertTrue(slot.x - halfWidth >= 0f && slot.x + halfWidth <= 800f)
                assertTrue(slot.y - halfHeight >= 0f && slot.y + halfHeight <= 320f)
            }
            val smallStage = packPopLyrics(widths, heights, 160f, 100f, 4f, mode, 1.55f, 12,
                extents = extents, rowMotionReach = 12f, maxFitScale = 1.8f)
            assertTrue(smallStage.scale < packed.scale)
        }
    }

    @Test fun lineLengthPreferenceChangesRowsOnPhoneSizedStages() {
        for (mode in listOf("centered", "staggered", "stairs")) {
            fun pack(percent: Int) = packPopLyrics(FloatArray(12) { 40f }, FloatArray(12) { 70f },
                320f, 800f, 0f, mode, 1f, 0, lineWidthPercent = percent)
            assertTrue(pack(60).rowStarts.size > pack(100).rowStarts.size)
            assertTrue(popLyricRowLimit(320f, 70f, mode, 60) < popLyricRowLimit(320f, 70f, mode, 100))
        }
    }

    @Test fun balancedLinesAvoidAnOverlongFirstRowAndTinyTailInEveryMode() {
        for (mode in listOf("centered", "staggered", "stairs")) {
            val widths = floatArrayOf(80f, 80f, 80f, 80f, 20f)
            val packed = packPopLyrics(widths, FloatArray(5) { 40f }, 260f, 400f, 0f, mode, 1f, 0)
            assertEquals(listOf(0, 2), packed.rowStarts)
            assertEquals(1f, packed.scale, 0f)
            assertTrue(packed.slots[2].y > packed.slots[1].y)
            for (index in widths.indices) {
                assertTrue(packed.slots[index].x - widths[index] / 2 >= 0f)
                assertTrue(packed.slots[index].x + widths[index] / 2 <= 260f)
            }
        }
    }

    @Test fun characterRevealKeepsOriginalWordGroupsAndClosingPunctuationWithTheirPhrase() {
        val characters = listOf("音", "乐", "陪", "你", "到", "家").mapIndexed { index, text ->
            PopLyricToken(text, 1000 + index / 2 * 500L, 1500 + index / 2 * 500L)
        }
        val grouped = packPopLyrics(FloatArray(6) { 40f }, FloatArray(6) { 40f }, 150f, 400f, 0f,
            "centered", 1f, 0, popLyricBreakPenalties(characters))
        assertEquals(2, grouped.rowStarts.size)
        assertTrue(grouped.rowStarts[1] in listOf(2, 4))
        val text = listOf("我", "们", "，", "一", "起", "唱", "歌")
        val punctuated = text.mapIndexed { index, word -> PopLyricToken(word, index * 500L, (index + 1) * 500L) }
        val packed = packPopLyrics(FloatArray(7) { 40f }, FloatArray(7) { 40f }, 150f, 400f, 0f,
            "centered", 1f, 0, popLyricBreakPenalties(punctuated))
        assertEquals(listOf(0, 3), packed.rowStarts)
        assertEquals("我们，一起唱歌", punctuated.joinToString("") { it.text })
    }

    @Test fun landscapeLimitsLineLengthAndShortPhrasesStayCentered() {
        for (mode in listOf("centered", "staggered", "stairs")) {
            val long = packPopLyrics(FloatArray(20) { 32f }, FloatArray(20) { 40f }, 800f, 320f, 0f, mode, 1f, 0)
            assertTrue("Landscape text should wrap into readable short rows", long.rowStarts.size >= 2)
            assertTrue(long.scale >= .88f)
            val short = packPopLyrics(FloatArray(3) { 60f }, FloatArray(3) { 40f }, 800f, 320f, 0f, mode, 1f, 0)
            assertEquals(listOf(0), short.rowStarts)
            assertEquals(400f, (short.slots.first().x + short.slots.last().x) / 2, .01f)
        }
    }

    @Test fun oversizedPunctuationGroupsStillProduceEveryGlyphAndFitTheViewport() {
        val samples = listOf(
            listOf("很长的词", "，", "继续") to floatArrayOf(420f, 40f, 40f),
            listOf("（", "（", "长词", "）", "）") to floatArrayOf(40f, 40f, 420f, 40f, 40f),
        )
        for ((words, widths) in samples) for (mode in listOf("centered", "staggered", "stairs")) {
            val tokens = words.mapIndexed { index, word -> PopLyricToken(word, index * 500L, (index + 1) * 500L) }
            val packed = packPopLyrics(widths, FloatArray(words.size) { 40f }, 150f, 400f, 0f,
                mode, 1f, 0, popLyricBreakPenalties(tokens))
            assertEquals(words.size, packed.slots.size)
            assertEquals(if (words.size == 3) listOf(0, 2) else listOf(0), packed.rowStarts)
            for (index in widths.indices) {
                assertTrue(packed.slots[index].x - widths[index] * packed.scale / 2 >= -.01f)
                assertTrue(packed.slots[index].x + widths[index] * packed.scale / 2 <= 150.01f)
            }
        }
    }

    @Test fun actualRotationBoundsTightGroupsAndRowMotionFitWithoutExtraUniformPadding() {
        val widths = floatArrayOf(100f, 40f, 100f, 40f)
        val heights = FloatArray(4) { 40f }
        val extents = widths.mapIndexed { index, w -> popGlyphExtent(w, heights[index], 1.4f, if (index % 2 == 0) 10f else 25f) }
        val gaps = floatArrayOf(0f, 0f, 12f, 0f, 0f)
        val packed = packPopLyrics(widths, heights, 500f, 200f, 8f, "centered", 1.4f, 25,
            extents = extents, boundaryGaps = gaps, rowGap = 8f, rowMotionReach = 12f)
        assertEquals(4, packed.slots.size)
        for (index in widths.indices) {
            val extent = extents[index]
            assertTrue(packed.slots[index].x - extent.width * packed.scale / 2 >= -.01f)
            assertTrue(packed.slots[index].x + extent.width * packed.scale / 2 <= 500.01f)
            assertTrue(packed.slots[index].y - (extent.height / 2 + 12f) * packed.scale >= -.01f)
            assertTrue(packed.slots[index].y + (extent.height / 2 + 12f) * packed.scale <= 200.01f)
            for (degrees in -10..10) {
                val radians = degrees * Math.PI / 180
                val hw = (widths[index] * kotlin.math.cos(radians) + heights[index] * kotlin.math.abs(kotlin.math.sin(radians))) * 1.4f / 2
                val hh = (heights[index] * kotlin.math.cos(radians) + widths[index] * kotlin.math.abs(kotlin.math.sin(radians))) * 1.4f / 2
                assertTrue(hw <= extent.width / 2 + .01f && hh <= extent.height / 2 + .01f)
            }
        }
        assertTrue(extents[0].width < widths[0] * 1.4f + heights[0] * kotlin.math.sin(25 * 1.15 * Math.PI / 180).toFloat() * 1.4f)
    }

    @Test fun allLayoutsFitDenseSentencesAtFontAndRotationExtremesWithoutOverlapping() {
        for (mode in listOf("centered", "staggered", "stairs")) {
            val widths = FloatArray(64) { if (it % 5 == 0) 140f else 38f }
            val heights = FloatArray(64) { 62f }
            val packed = packPopLyrics(widths, heights, 160f, 100f, 8f, mode, 1.86f, 30)
            val reach = (kotlin.math.sin(30 * 1.15 * Math.PI / 180) * 1.86f).toFloat()
            assertEquals(64, packed.slots.size)
            for (index in widths.indices) {
                val slot = packed.slots[index]
                val hw = (widths[index] * 1.86f + heights[index] * reach) * packed.scale / 2
                val hh = (heights[index] * 1.86f + widths[index] * reach) * packed.scale / 2
                assertTrue(slot.x - hw >= -.01f && slot.x + hw <= 160.01f)
                assertTrue(slot.y - hh >= -.01f && slot.y + hh <= 100.01f)
                for (other in 0 until index) {
                    val ow = (widths[other] * 1.86f + heights[other] * reach) * packed.scale / 2
                    val oh = (heights[other] * 1.86f + widths[other] * reach) * packed.scale / 2
                    assertTrue(abs(slot.x - packed.slots[other].x) >= hw + ow - .01f ||
                        abs(slot.y - packed.slots[other].y) >= hh + oh - .01f)
                }
            }
        }
    }
}
