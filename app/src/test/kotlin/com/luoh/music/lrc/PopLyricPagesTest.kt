package com.luoh.music.lrc

import org.junit.Assert.*
import org.junit.Test

class PopLyricPagesTest {
    private fun tokens(text: String) = text.mapIndexed { index, character ->
        PopLyricToken(character.toString(), 1000 + index * 250L, 1250 + index * 250L)
    }

    @Test fun longSentencesBecomeSmallPagesWithoutLosingTextOrChangingClocks() {
        val tokens = tokens("牵着你的手一阵莫名感动让我一直陪你到天亮")
        val original = tokens.toList()
        val pages = paginatePopLyrics(tokens, FloatArray(tokens.size + 1), FloatArray(tokens.size) { 40f }, 320f, 10)
        assertTrue(pages.size >= 3)
        assertEquals(tokens.indices.toList(), pages.flatMap { (it.from until it.until).toList() })
        assertTrue(pages.all { it.until - it.from <= 8 })
        assertEquals(original, tokens)
        assertEquals(0, activePopLyricPage(pages, tokens, 0L))
        for ((index, page) in pages.withIndex()) {
            assertEquals(index, activePopLyricPage(pages, tokens, tokens[page.from].startMs))
            if (index > 0) assertEquals(index - 1,
                activePopLyricPage(pages, tokens, tokens[page.from].startMs - 1))
        }
        assertEquals(0, activePopLyricPage(pages, tokens, 1000L))
    }

    @Test fun capacityAndViewportBothControlPageSize() {
        val tokens = tokens("让音乐陪你走过每一个安静的夜晚直到天亮")
        fun pages(length: Int, width: Float) = paginatePopLyrics(tokens,
            FloatArray(tokens.size + 1), FloatArray(tokens.size) { 40f }, width, length)
        assertTrue(pages(4, 800f).size > pages(10, 800f).size)
        assertTrue(pages(10, 160f).size > pages(10, 800f).size)
    }

    @Test fun punctuationAndRealPausesWinOverArbitraryCharacterCuts() {
        val tokens = tokens("牵着你的手，一阵莫名感动")
        val penalties = popLyricBreakPenalties(tokens)
        val pages = paginatePopLyrics(tokens, penalties, FloatArray(tokens.size) { 30f }, 600f, 10)
        assertEquals(listOf(PopLyricPage(0, 6), PopLyricPage(6, tokens.size)), pages)
        assertEquals("牵着你的手，", tokens.subList(pages[0].from, pages[0].until).joinToString("") { it.text })
    }

    @Test fun simultaneousCharactersAndOverlappingHeldNotesStayOnTheSamePage() {
        val sharedClock = List(12) { PopLyricToken("字", 1000, 3000) }
        assertEquals(listOf(PopLyricPage(0, 12)), paginatePopLyrics(sharedClock,
            FloatArray(13), FloatArray(12) { 40f }, 160f, 4))
        val held = listOf(PopLyricToken("陪", 1000, 4000), PopLyricToken("你", 2000, 3000),
            PopLyricToken("到", 4000, 4500), PopLyricToken("家", 4500, 5000))
        val pages = paginatePopLyrics(held, FloatArray(5), FloatArray(4) { 80f }, 160f, 4)
        assertTrue(pages.none { it.from == 1 })
        assertEquals(0, activePopLyricPage(pages, held, 3500))
        assertEquals(1, activePopLyricPage(pages, held, 4000))
    }
}
