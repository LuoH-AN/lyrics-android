package com.luoh.music.lrc

import com.luoh.music.lrc.ui.home.pop.PopLyricGroups
import com.luoh.music.lrc.ui.home.pop.PopLyricTimeline
import com.luoh.music.lrc.ui.home.pop.PopLyricToken
import com.luoh.music.lrc.ui.home.pop.groupPopLyrics
import com.luoh.music.lrc.ui.home.pop.packPopLyrics
import com.luoh.music.lrc.ui.home.pop.popLayoutTokens
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class PopLyricGroupingTest {
    @Test fun providerPhrasesCanWrapAtLexicalBoundariesWithoutInventingWordClocks() {
        val source = PopLyricToken("让音乐陪你走过每一个夜晚", 1000, 8000)
        val timeline = PopLyricTimeline(listOf(source), true)
        val expanded = popLayoutTokens(timeline)
        assertTrue(expanded.tokens.size > 1)
        assertEquals(source.text, expanded.tokens.joinToString("") { it.text })
        assertTrue(expanded.tokens.all { it.startMs == 1000L && it.endMs == 8000L })
        assertTrue(expanded.sourceIndices.all { it == 0 })
        val untimed = popLayoutTokens(PopLyricTimeline(listOf(source), false))
        assertEquals(listOf(source), untimed.tokens)
    }

    private fun tokens(parts: List<String>) = parts.mapIndexed { index, text -> PopLyricToken(text, 1000 + index * 200L, 1200 + index * 200L) }
    private fun texts(tokens: List<PopLyricToken>, groups: PopLyricGroups) = groups.units.map {
        tokens.subList(it.from, it.until).joinToString("") { token -> token.text }
    }

    @Test fun installedIcuDictionaryGroupsChineseAcrossIndividuallyTimedCharacters() {
        val tokens = tokens(listOf("音", "乐", "世", "界", "。"))
        val before = tokens.toList()
        val groups = groupPopLyrics(tokens)
        assertEquals(listOf("音乐", "世界。"), texts(tokens, groups))
        assertEquals(before, tokens)
        assertTrue(groups.tightBoundaries[1])
        assertEquals(40f, groups.penalties[1], 0f)
        assertEquals(40f, groups.penalties[4], 0f)
        assertTrue(groupPopLyrics(tokens, false).penalties[1] < 40f)
    }

    @Test fun contractionsPunctuationSpacesAndEmojiPreserveEverySourceUnitAndTime() {
        val tokens = tokens(listOf("It", "’", "s", " ", "a", " ", "world", "!", " ", "👩‍👩‍👧‍👦"))
        val groups = groupPopLyrics(tokens)
        val texts = texts(tokens, groups)
        assertTrue(texts.contains("It’s"))
        assertTrue(texts.contains("world!"))
        assertEquals(tokens.joinToString("") { it.text }, texts.joinToString(""))
        assertEquals(tokens.size, groups.units.sumOf { it.until - it.from })
        assertTrue(groups.units.last().until == tokens.size)
    }

    @Test fun realPauseOverridesInferredWordAndOversizedGroupsRemainRenderable() {
        val paused = listOf(PopLyricToken("音", 1000, 1200), PopLyricToken("乐", 1500, 1800))
        assertEquals(listOf("音", "乐"), texts(paused, groupPopLyrics(paused)))
        val tokens = tokens(listOf("音", "乐", "，"))
        val groups = groupPopLyrics(tokens)
        val packed = packPopLyrics(FloatArray(3) { 100f }, FloatArray(3) { 40f }, 100f, 100f, 0f,
            "centered", 1f, 0, groups.penalties)
        assertEquals(3, packed.slots.size)
        assertEquals(listOf(0), packed.rowStarts)
        assertTrue(packed.scale <= .34f)
    }
}
