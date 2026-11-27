package com.luoh.music.lrc

import com.luoh.music.lrc.data.lyrics.matchScore
import com.luoh.music.lrc.data.lyrics.searchPlans
import com.luoh.music.lrc.data.lyrics.versionTags
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class ChineseLyricsSearchTest {
    private fun queries(track: String, artist: String): List<String> =
        searchPlans(track, artist).map { it.query }

    @Test fun traditionalAndSimplifiedTitlesAndArtistsAreQueriedBeforeTitleOnlyFallbacks() {
        for ((title, singer) in listOf("愛與夢" to "陳奕迅", "爱与梦" to "陈奕迅")) {
            val plans = queries(title, singer)
            assertEquals("$title $singer", plans.first())
            assertTrue(plans.containsAll(listOf("愛與夢 陳奕迅", "爱与梦 陈奕迅", "愛與夢", "爱与梦")))
            assertTrue(plans.indexOf("爱与梦 陈奕迅") < plans.indexOf("爱与梦"))
            assertTrue(plans.indexOf("愛與夢 陳奕迅") < plans.indexOf("愛與夢"))
            assertEquals(plans.size, plans.distinct().size)
        }
    }

    @Test fun bracketsMixedScriptsAndMissingArtistsRemainSearchableWithoutDuplicateRequests() {
        val plans = queries("愛與夢（现场版）", "陈奕迅")
        assertEquals("愛與夢（现场版） 陈奕迅", plans.first())
        assertTrue(plans.containsAll(listOf("爱与梦（现场版） 陈奕迅", "愛與夢（現場版） 陳奕迅",
            "爱与梦 陈奕迅", "愛與夢 陳奕迅")))
        assertEquals(listOf("爱与梦", "愛與夢"), queries("爱与梦", ""))
        assertEquals(listOf("Song Artist", "Song"), queries("Song", "Artist"))
        assertTrue(queries("", "Artist").isEmpty())
    }

    @Test fun shortTitlesAndArtistsMatchAcrossScriptsWithoutDurationAndStillRejectWrongSongs() {
        assertEquals(100, matchScore("愛夢", "陳奕迅", "爱梦", "陈奕迅"))
        assertEquals(100, matchScore("爱梦", "陈奕迅", "愛夢", "陳奕迅"))
        assertEquals(0, matchScore("愛夢", "陳奕迅", "爱梦", "王菲"))
        assertEquals(20, matchScore("愛夢", "陳奕迅", "晴天", "陈奕迅"))
        assertEquals(versionTags("歌（现场版）"), versionTags("歌（現場版）"))
        assertNotEquals(versionTags("歌"), versionTags("歌（現場版）"))
        assertEquals(setOf("instrumental"), versionTags("歌（純音楽）"))
        assertEquals(setOf("instrumental"), versionTags("歌（純音樂）"))
    }

    @Test fun concurrentProvidersKeepBothScriptQueriesStable() {
        val workers = Executors.newFixedThreadPool(6)
        try {
            val jobs = List(24) { Callable { queries("愛與夢", "陳奕迅") } }
            workers.invokeAll(jobs).forEach { result ->
                assertTrue(result.get().containsAll(listOf("愛與夢 陳奕迅", "爱与梦 陈奕迅")))
            }
        } finally { workers.shutdownNow() }
    }
}
