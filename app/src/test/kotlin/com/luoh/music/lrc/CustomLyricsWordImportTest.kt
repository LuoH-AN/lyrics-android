package com.luoh.music.lrc

import com.luoh.music.lrc.data.lyrics.CustomLyricsStore
import com.luoh.music.lrc.lyrics.LyricParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class CustomLyricsWordImportTest {
    @Test
    fun importsYrcAbsoluteWordTimeline() {
        val imported = CustomLyricsStore.parseImport(
            """
            [ti:测试歌曲]
            [ar:测试歌手]
            [1000,900](1000,200,0)你(1200,300,0)好(1500,400,0)呀
            [2500,600](2500,300,0)下(2800,300,0)句
            """.trimIndent()
        )

        assertEquals(2, imported.parsed.lineCount)
        assertEquals(2, imported.wordLineCount)
        assertEquals(3100L, imported.durationMs)
        assertTrue(imported.parsed.lyrics.contains("[00:01.000]你好呀"))
        assertTrue(imported.wordLyrics.contains("[1000,900](1000,200)你(1200,300)好(1500,400)呀"))

        val document = LyricParser.parse(imported.parsed.lyrics, wordLrc = imported.wordLyrics)
        assertEquals(2, document.lines.size)
        assertEquals(3, document.lines[0].words.size)
        assertEquals(1200L, document.lines[0].words[1].startMs)
        assertEquals(300L, document.lines[0].words[1].durationMs)
    }

    @Test
    fun importsDecodedKrcRelativeWordTimeline() {
        val imported = CustomLyricsStore.parseImport(
            "[2000,600]<0,150,0>测<150,450,0>试"
        )

        assertEquals("[2000,600](2000,150)测(2150,450)试", imported.wordLyrics)
        val document = LyricParser.parse(imported.parsed.lyrics, wordLrc = imported.wordLyrics)
        assertEquals(2000L, document.lines.single().words[0].startMs)
        assertEquals(2150L, document.lines.single().words[1].startMs)
    }

    @Test
    fun ordinaryLrcRemainsOrdinaryLrc() {
        val imported = CustomLyricsStore.parseImport(
            "[ti:测试]\n[00:01.000]第一句\n[00:02.500]第二句"
        )

        assertTrue(imported.wordLyrics.isBlank())
        assertEquals(2, imported.parsed.lineCount)
        assertFalse(imported.parsed.lyrics.isBlank())
    }
}
