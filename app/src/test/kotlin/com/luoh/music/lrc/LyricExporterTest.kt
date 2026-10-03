package com.luoh.music.lrc

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricExporterTest {
    @Test fun timedLyricsKeepOriginalRowsAndMergeTranslations() {
        val lrc = "[ti:song]\n[00:01.00]hello\n[00:03.00]world"
        val translated = "[00:01.00]你好\n[00:03.00]世界"
        assertEquals(
            "[ar:artist]\n[ti:song]\n[00:01.00]hello\n[00:01.000]你好\n[00:03.00]world\n[00:03.000]世界",
            LyricExporter.buildLrc("song", "artist", lrc, translated)
        )
    }

    @Test fun translationUsesTenMillisecondBuckets() {
        val lrc = "[00:01.004]hello"
        val translated = "[00:01.001]你好"
        assertEquals(
            "[00:01.004]hello\n[00:01.004]你好",
            LyricExporter.buildLrc("", "", lrc, translated)
        )
        assertEquals(
            "[00:01.004]hello",
            LyricExporter.buildLrc("", "", lrc, "[00:01.009]不同桶")
        )
    }

    @Test fun plainLyricsInterleaveTranslations() {
        assertEquals(
            "hello\n你好\nworld\n世界",
            LyricExporter.buildLrc("", "", "hello\nworld", "你好\n世界")
        )
    }

    @Test fun metadataHeaderIsAddedAndBlankPartsSkipped() {
        assertEquals(
            "[ti:歌名]\n[ar:歌手]\n[00:01.00]hello",
            LyricExporter.buildLrc("歌名", "歌手", "[00:01.00]hello", "")
        )
    }

    @Test fun fileNameSanitizesForbiddenCharacters() {
        assertEquals("歌手 - 歌名.lrc", LyricExporter.fileName("歌名", "歌手"))
        assertEquals("a b c.lrc", LyricExporter.fileName("a/b:c?", "  "))
        assertEquals("歌词.lrc", LyricExporter.fileName("", ""))
    }
}
