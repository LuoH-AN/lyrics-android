package com.luoh.music.lrc

import org.junit.Assert.*
import org.junit.Test

class PopLyricRenderTimingTest {
    @Test fun tinyAndFastSentencesFitTheirRealWindowsWithoutChangingTokens() {
        for (duration in listOf(80L, 400L, 6000L)) {
            val tokens = listOf(PopLyricToken("音乐", 1000, 1000 + duration))
            val timeline = PopLyricTimeline(tokens, true, 1000, 1000 + duration)
            val timing = popLyricRenderTiming(timeline, PopLyricsStyle(entranceMs = 600), 1000 + duration)
            assertEquals(tokens, timeline.tokens)
            if (duration == 80L) {
                assertEquals(0, timing.transitionMs)
                assertEquals(0L, timing.entranceMs[0])
                assertEquals(0L, timing.lookaheadMs[0])
                assertEquals(80L, timing.nextChangeDelayMs(tokens, 1000, 1080))
            } else if (duration == 400L) {
                assertTrue(timing.transitionMs < 160)
                assertTrue(timing.entranceMs[0] <= duration / 2)
            } else {
                assertTrue(timing.transitionMs in 220..600)
                assertEquals(600L, timing.entranceMs[0])
            }
        }
    }

    @Test fun holdCannotCrossNextSentenceAndSchedulerIncludesEveryHoldAndReturnWindow() {
        val timeline = PopLyricTimeline(listOf(PopLyricToken("唱", 1000, 2000)), true, 1000, 3000)
        val timing = popLyricRenderTiming(timeline, PopLyricsStyle(textHoldMs = 800), 2200)
        assertEquals(2200L, timing.passStartMs[0])
        assertEquals(200L, timing.nextChangeDelayMs(timeline.tokens, 2000, 4000))
        assertEquals(0L, timing.nextChangeDelayMs(timeline.tokens, 2300, 4000))
        assertEquals(1000L, timing.nextChangeDelayMs(timeline.tokens, 3000, 4000))
        val fixed = popLyricRenderTiming(PopLyricTimeline(listOf(PopLyricToken("啊", 1000, 1080)), true),
            PopLyricsStyle(adaptiveTiming = false, entranceMs = 600))
        assertTrue(fixed.transitionMs in 220..600)
        assertEquals(600L, fixed.entranceMs[0])
    }

    @Test fun imminentSentenceIsReusedAndCachesEvictLeastRecentlyUsedEntries() {
        val cache = PopLyricCache<String, Any>()
        val current = cache.getOrPut("current") { Any() }
        val upcoming = cache.getOrPut("upcoming") { Any() }
        cache.getOrPut("outgoing") { Any() }
        assertSame(upcoming, cache.getOrPut("upcoming") { error("Preheated data must be reused") })
        assertSame(current, cache.getOrPut("current") { error("Current data must be retained") })
        cache.getOrPut("new") { Any() }
        assertEquals(3, cache.size)
        assertEquals(4, cache.builds)
        cache.getOrPut("outgoing") { Any() }
        assertEquals(5, cache.builds)
        assertEquals(3, cache.size)
    }
}
