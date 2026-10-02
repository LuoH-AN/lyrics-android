package com.luoh.music.lrc

import org.junit.Assert.*
import org.junit.Test

class LyricParserTest {
    @Test fun multipleTimestampsAndFractionalMinutesRemainSorted() {
        val result = LyricParser.parse("[001:02.3]later\n[00:01.00][00:02.50]repeat")
        assertTrue(result.timed)
        assertEquals(listOf(1000L, 2500L, 62300L), result.lines.map { it.startMs })
        assertEquals(listOf("repeat", "repeat", "later"), result.lines.map { it.text })
    }

    @Test fun emptyTimestampEndsPreviousLineAndCreditsAreNotDisplayed() {
        val result = LyricParser.parse("[00:00.00]作词：someone\n[00:01.00]first\n[00:03.00]\n[00:08.00]second")
        assertEquals(2, result.lines.size)
        assertEquals(3000L, result.lines.first().endMs)
        assertEquals("second", result.lines.last().text)
    }

    @Test fun translationUsesTenMillisecondBuckets() {
        val result = LyricParser.parse("[00:01.004]hello\n[00:02.006]world", "[00:01.001]你好\n[00:02.009]世界")
        assertEquals(listOf("你好", "世界"), result.lines.map { it.translation })
        assertEquals("", LyricParser.parse("[00:01.00]hello", "[00:01.02]wrong").lines.single().translation)
    }

    @Test fun canonicalQrcMarkersPrecedeTheirText() {
        val result = LyricParser.parse("[00:01.00]你好", wordLrc = "[1000,2000](1000,1000,0)你(2000,1000,0)好")
        val line = result.lines.single()
        assertEquals(listOf("你", "好"), line.words.map { it.text })
        assertEquals(listOf(1000L, 2000L), line.words.map { it.startMs })
        assertEquals(3000L, line.endMs)
        assertEquals(.5f, LyricTiming.sungCharacters(line, 1500L), .001f)
    }

    @Test fun sparseWordTrackFallsBackToWholeLineLyrics() {
        val result = LyricParser.parse("[00:01.00]first\n[00:03.00]second\n[00:05.00]third",
            wordLrc = "[1000,1000](1000,1000)first")
        assertEquals(3, result.lines.size)
        assertTrue(result.lines.all { it.words.isEmpty() })
    }

    @Test fun seventyPercentWordCoverageIsAccepted() {
        val lrc = (1..10).joinToString("\n") { "[00:${it.toString().padStart(2, '0')}.00]line $it" }
        val words = (1..7).joinToString("\n") { "[${it * 1000},900](${it * 1000},900)line $it" }
        assertEquals(7, LyricParser.parse(lrc, wordLrc = words).lines.count { it.words.isNotEmpty() })
    }

    @Test fun wordsOutsideAttachmentWindowAreIgnored() {
        val result = LyricParser.parse("[00:01.00]first", wordLrc = "[2200,1000](2200,1000)first")
        assertTrue(result.lines.single().words.isEmpty())
    }

    @Test fun plainLyricsAndPlainTranslationsAreRetainedWithoutFakeTimestamps() {
        val result = LyricParser.parse("[ti:song]\nhello\nworld", "你好\n世界")
        assertFalse(result.timed)
        assertEquals(listOf("hello", "world"), result.lines.map { it.text })
        assertEquals(listOf("你好", "世界"), result.lines.map { it.translation })
    }

    @Test fun positiveLrcOffsetAdvancesLyricsAndNormalizedCustomLrcIsNotShiftedAgain() {
        val raw = LyricParser.parse("[offset:+500]\n[00:01.00]line")
        val normalized = LyricParser.parse("[00:00.500]line", "[00:00.500]译文")
        assertEquals(500L, raw.lines.single().startMs)
        assertEquals(500L, normalized.lines.single().startMs)
        assertEquals("译文", normalized.lines.single().translation)
    }

    @Test fun emptyAndMetadataOnlyPayloadsHaveNoVisibleLines() {
        assertTrue(LyricParser.parse("").lines.isEmpty())
        assertTrue(LyricParser.parse("[ti:song]\n[ar:artist]").lines.isEmpty())
        assertTrue(LyricParser.parse("[00:00.00]作曲：artist\n[00:01.00]").lines.isEmpty())
    }

    @Test fun lineSelectionAndExplicitInterludeEndUseTheSameTimeline() {
        val lines = LyricParser.parse("[00:01.00]first\n[00:03.00]\n[00:10.00]next").lines
        assertEquals(-1, LyricTiming.activeIndex(lines, 999L))
        assertEquals(0, LyricTiming.activeIndex(lines, 1000L))
        assertEquals(0, LyricTiming.activeIndex(lines, 9999L))
        assertEquals(1, LyricTiming.activeIndex(lines, 10000L))
        assertEquals(3000L, LyricTiming.lineEnd(lines.first()))
    }

    @Test fun wordProgressHoldsBetweenWordsAndCompletesAtEnd() {
        val line = LyricLine(1000L, "hello world", words = listOf(
            LyricWord(1000L, 500L, "hello "), LyricWord(2000L, 1000L, "world")
        ))
        assertEquals(0f, LyricTiming.sungCharacters(line, 0L), .001f)
        assertEquals(6f, LyricTiming.sungCharacters(line, 1800L), .001f)
        assertEquals(8.5f, LyricTiming.sungCharacters(line, 2500L), .001f)
        assertEquals(11f, LyricTiming.sungCharacters(line, 5000L), .001f)
    }
}
