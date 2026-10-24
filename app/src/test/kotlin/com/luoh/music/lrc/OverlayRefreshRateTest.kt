package com.luoh.music.lrc

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayRefreshRateTest {
    private val current = OverlayDisplayMode(1, 1080, 2400, 60f)

    @Test fun picksFastestModeAtCurrentResolution() {
        val modes = listOf(
            current,
            OverlayDisplayMode(2, 1080, 2400, 90f),
            OverlayDisplayMode(3, 1080, 2400, 120f),
            OverlayDisplayMode(4, 1440, 3200, 144f),
        )
        assertEquals(120f, preferredOverlayRefreshRate(modes, current), 0f)
    }

    @Test fun fallsBackToAnyModeWhenNoModeSharesTheSize() {
        val modes = listOf(OverlayDisplayMode(4, 1440, 3200, 144f))
        assertEquals(144f, preferredOverlayRefreshRate(modes, current), 0f)
    }

    @Test fun keepsCurrentRateWhenModesAreUnavailable() {
        assertEquals(60f, preferredOverlayRefreshRate(emptyList(), current), 0f)
        assertEquals(0f, preferredOverlayRefreshRate(emptyList(), current.copy(refreshRate = 0f)), 0f)
    }
}
