package com.luoh.music.lrc

import org.junit.Assert.*
import org.junit.Test

class LyricClockTest {
    private var now = 0L
    private val clock = LyricClock { now }

    @Test fun playingClockExtrapolatesMonotonically() {
        clock.sync(1000L, true)
        now = 500L
        assertEquals(1500L, clock.positionMs())
        clock.sync(1450L, true)
        assertEquals(1500L, clock.positionMs())
        now = 700L
        assertEquals(1700L, clock.positionMs())
    }

    @Test fun largeSeekAndExplicitSmallSeekRealignImmediately() {
        clock.sync(1000L, true)
        now = 300L
        clock.sync(8000L, true)
        assertEquals(8000L, clock.positionMs())
        clock.sync(7800L, true, force = true)
        assertEquals(7800L, clock.positionMs())
    }

    @Test fun pauseFreezesAndResumeUsesTheReportedPosition() {
        clock.sync(1000L, true)
        now = 250L
        clock.sync(1200L, false)
        now = 5000L
        assertEquals(1200L, clock.positionMs())
        clock.sync(1300L, true)
        now = 5500L
        assertEquals(1800L, clock.positionMs())
    }

    @Test fun playbackSpeedAffectsExtrapolation() {
        clock.sync(1000L, true, 2f)
        now = 250L
        assertEquals(1500L, clock.positionMs())
        clock.sync(1500L, true, .5f)
        now = 650L
        assertEquals(1700L, clock.positionMs())
    }

    @Test fun durationCapsPositionAndUnknownDurationDoesNot() {
        clock.durationMs = 2000L
        clock.sync(1000L, true)
        now = 2000L
        assertEquals(2000L, clock.positionMs())
        clock.durationMs = 0L
        assertEquals(3000L, clock.positionMs())
    }

    @Test fun resetDropsOldSongAndNonFiniteSpeedIsSafe() {
        clock.sync(-100L, true, Float.NaN)
        now = 500L
        assertEquals(500L, clock.positionMs())
        clock.durationMs = 10_000L
        clock.reset()
        now = 9000L
        assertEquals(0L, clock.positionMs())
        assertFalse(clock.playing)
        assertEquals(0L, clock.durationMs)
        assertEquals(1f, clock.speed, 0f)
    }

    @Test fun explicitTrackChangeDoesNotCarrySmallPreviousPosition() {
        clock.sync(500L, true)
        now = 100L
        clock.sync(0L, true, force = true)
        assertEquals(0L, clock.positionMs())
    }
}
