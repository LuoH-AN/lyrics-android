package com.luoh.music.lrc

import com.luoh.music.lrc.ui.home.HomeLyricAppearance
import com.luoh.music.lrc.ui.home.pop.PopLyricsStyle
import com.luoh.music.lrc.ui.home.pop.packPopLyrics
import com.luoh.music.lrc.ui.home.pop.popCaptionTop
import com.luoh.music.lrc.ui.home.pop.popStageLayout
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class PopStageLayoutTest {
    @Test fun mainLyricsStayAtViewportCenterAndOnlyActualCaptionHeightIsReserved() {
        for ((width, height) in listOf(400f to 800f, 800f to 320f)) {
            val layout = popStageLayout(width, height, 1f, 1f, HomeLyricAppearance(),
                PopLyricsStyle(stageAnchorPercent = 30), true, captionHeight = 24f)
            assertEquals(height / 2f, layout.insetY + layout.bodyTop + layout.bodyHeight / 2f, .001f)
            assertEquals(34f, layout.captionReserve, .001f)
            assertEquals(.5f, layout.anchor, 0f)
            val original = popStageLayout(width, height, 1f, 1f, HomeLyricAppearance(),
                PopLyricsStyle(stageAnchorPercent = 60), true, captionHeight = 0f)
            assertEquals(0f, original.captionReserve, 0f)
            assertEquals(height / 2f, original.insetY + original.bodyHeight / 2f, .001f)
        }
    }

    @Test fun portraitLandscapePreviewAndLargeFontsReserveSafeCaptionSpace() {
        for ((width, height) in listOf(400f to 800f, 800f to 320f, 320f to 156f, 160f to 100f)) {
            for (fontScale in listOf(1f, 1.5f, 2f)) for (maxLines in 1..4) {
                val layout = popStageLayout(width, height, 1f, fontScale,
                    HomeLyricAppearance(48, 32, 800), PopLyricsStyle(translationMaxLines = maxLines, stagePaddingDp = 40), true)
                assertTrue(layout.bodyWidth > 0f && layout.bodyHeight > 0f)
                assertTrue(layout.captionLines in 1..maxLines)
                assertTrue(layout.captionWidth <= layout.bodyWidth)
                assertTrue(layout.bodyHeight + layout.captionReserve + layout.insetY * 2f <= height + .01f)
                assertTrue(layout.insetX * 2f < width)
                assertTrue(layout.captionReserve < height * .5f)
            }
        }
        val original = popStageLayout(400f, 800f, 1f, 1f, HomeLyricAppearance(), PopLyricsStyle(), false)
        assertEquals(0f, original.captionReserve, 0f)
        assertEquals(0, original.captionLines)
    }

    @Test fun opticalAnchorChangesPlacementWithoutMovingGlyphsOutsideTheStage() {
        for (anchor in listOf(.3f, .46f, .6f)) for (mode in listOf("centered", "staggered", "stairs", "wave", "random")) {
            val packing = packPopLyrics(floatArrayOf(60f, 45f, 70f), FloatArray(3) { 45f },
                360f, 600f, 5f, mode, 1f, 0, anchorY = anchor)
            packing.slots.forEach { assertTrue(it.y - 45f * packing.scale / 2f >= 0f && it.y + 45f * packing.scale / 2f <= 600f) }
            assertTrue(packing.slots.map { it.y }.average() in 150.0..420.0)
        }
        fun pack(anchor: Float) = packPopLyrics(floatArrayOf(60f, 45f, 70f), FloatArray(3) { 45f },
            360f, 600f, 5f, "centered", 1f, 0, anchorY = anchor)
        assertTrue(pack(.3f).slots.first().y < pack(.6f).slots.first().y)
        assertEquals(pack(.3f).slots.map { it.x }, pack(.6f).slots.map { it.x })
    }

    @Test fun subtitleClearanceClosesOnlyAfterTheTallerOutgoingPhraseFades() {
        val samples = (0..1000).map { popCaptionTop(220f, 380f, it / 1000f, 10f, 700f) }
        assertEquals(390f, samples.first(), 0f)
        assertEquals(390f, samples[550], 0f)
        assertEquals(230f, samples.last(), .001f)
        samples.zipWithNext().forEach { (a, b) -> assertTrue(b <= a + .00001f) }
        assertTrue(abs(samples[551] - samples[550]) < .001f)
        assertTrue(abs(samples[1000] - samples[999]) < .001f)
        assertEquals(230f, popCaptionTop(220f, 100f, .1f, 10f, 700f), 0f)
        assertEquals(300f, popCaptionTop(380f, null, 1f, 10f, 300f), 0f)
    }
}
