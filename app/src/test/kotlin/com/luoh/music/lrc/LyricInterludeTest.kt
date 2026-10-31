package com.luoh.music.lrc

import org.junit.Assert.*
import org.junit.Test

class LyricInterludeTest {
    private val document = LyricDocument(listOf(
        LyricLine(6000, "上一句", endMs = 10000), LyricLine(20000, "下一句", endMs = 24000)), true)

    @Test fun introMiddleAndOutroUseTheirOwnDurations() {
        val intro = LyricInterlude.at(document, 3000, 30000)!!
        assertEquals(-1, intro.afterLine)
        assertEquals(.5f, intro.progress(3000), .0001f)
        val middle = LyricInterlude.at(document, 15000, 30000)!!
        assertEquals(LyricInterlude(0, 10000, 20000), middle)
        assertEquals(0f, middle.progress(9999), 0f)
        assertEquals(.5f, middle.progress(15000), .0001f)
        assertEquals(1f, middle.progress(21000), 0f)
        val outro = LyricInterlude.at(document, 27000, 30000)!!
        assertEquals(1, outro.afterLine)
        assertEquals(.5f, outro.progress(27000), .0001f)
    }

    @Test fun boundariesAndSeekingDoNotLeaveAnInterludeOnSungLines() {
        for (time in listOf(6000L, 9999L, 20000L, 23999L, 30000L)) {
            assertNull("No gap at $time", LyricInterlude.at(document, time, 30000))
        }
        assertEquals(0f, LyricInterlude.at(document, 10000, 30000)!!.progress(10000), 0f)
        assertNotNull(LyricInterlude.at(document, 19999, 30000))
        assertNull(LyricInterlude.at(document, 9000, 30000))
        assertEquals(.2f, LyricInterlude.at(document, 12000, 30000)!!.progress(12000), .0001f)
    }

    @Test fun explicitEmptyTimestampAndWordTimingDetermineTheGapStart() {
        val explicit = LyricParser.parse("[00:01.00]上一句\n[00:03.00]\n[00:13.00]下一句")
        assertEquals(3000L, LyricInterlude.at(explicit, 8000, 20000)!!.startMs)
        val words = LyricParser.parse("", wordLrc = "[1000,4000](1000,4000)上一句\n[15000,3000](15000,3000)下一句")
        assertEquals(5000L, LyricInterlude.at(words, 10000, 20000)!!.startMs)
    }

    @Test fun noIndicatorForShortGapsPlainLyricsOrUnknownOutroLength() {
        val short = LyricDocument(listOf(LyricLine(4000, "上一句", endMs = 5000), LyricLine(9500, "下一句")), true)
        assertNull(LyricInterlude.at(short, 2000, 20000))
        assertNull(LyricInterlude.at(short, 7000, 20000))
        assertNull(LyricInterlude.at(document.copy(timed = false), 15000, 30000))
        assertNull(LyricInterlude.at(document, 27000, 0))
        assertNull(LyricInterlude.at(document.copy(lines = emptyList()), 15000, 30000))
    }
}
