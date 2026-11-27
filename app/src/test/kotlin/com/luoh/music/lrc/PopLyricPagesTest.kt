package com.luoh.music.lrc

import com.luoh.music.lrc.ui.home.pop.PopLyricPage
import com.luoh.music.lrc.ui.home.pop.PopLyricTimeline
import com.luoh.music.lrc.ui.home.pop.PopLyricToken
import com.luoh.music.lrc.ui.home.pop.PopLyricsStyle
import com.luoh.music.lrc.ui.home.pop.PopPhraseWindow
import com.luoh.music.lrc.ui.home.pop.activePopLyricPage
import com.luoh.music.lrc.ui.home.pop.paginatePopLyrics
import com.luoh.music.lrc.ui.home.pop.popLyricBreakPenalties
import com.luoh.music.lrc.ui.home.pop.popPhraseHandoffs
import com.luoh.music.lrc.ui.home.pop.popPhraseWindow
import org.junit.Assert.*
import org.junit.Test

class PopLyricPagesTest {
    private fun tokens(text: String) = text.mapIndexed { index, character ->
        PopLyricToken(character.toString(), 1000 + index * 250L, 1250 + index * 250L)
    }

    @Test fun sungPhraseCapacityIsIndependentOfFontInkAndViewport() {
        val source = tokens("晚风吹过山海星光照亮远方音乐陪你直到天亮")
        val pages = paginatePopLyrics(source, FloatArray(source.size + 1), PopLyricsStyle().pageLength)
        assertTrue(pages.size >= 4)
        assertTrue(pages.all { it.until - it.from <= 6 })
        assertEquals(source.indices.toList(), pages.flatMap { (it.from until it.until).toList() })
        val timeline = PopLyricTimeline(source, true)
        val handoffs = popPhraseHandoffs(pages, timeline)
        for ((index, page) in pages.withIndex()) {
            val now = source[page.from].startMs + handoffs[index].durationMs
            assertEquals(PopPhraseWindow(index, null), popPhraseWindow(pages, timeline, handoffs, now, true))
        }
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

    @Test fun completedPhraseStaysThroughThePauseThenRetiresAsTheNextPhraseEnters() {
        val source = tokens("牵着你的手一阵莫名感动").mapIndexed { index, token ->
            if (index < 5) token else token.copy(startMs = token.startMs + 300, endMs = token.endMs + 300)
        }
        val original = source.toList()
        val timeline = PopLyricTimeline(source, true)
        val pages = listOf(PopLyricPage(0, 5), PopLyricPage(5, source.size))
        val handoffs = popPhraseHandoffs(pages, timeline)
        val start = source[5].startMs
        fun window(now: Long, animate: Boolean = true) = popPhraseWindow(pages, timeline, handoffs, now, animate)
        assertEquals(PopPhraseWindow(0, null), window(source[4].endMs + 100))
        assertEquals(PopPhraseWindow(1, 0), window(start))
        assertEquals(PopPhraseWindow(1, 0), window(start + 90))
        assertEquals(PopPhraseWindow(1, 0), window(start + 180))
        assertEquals(PopPhraseWindow(1, null), window(start + handoffs[1].durationMs))
        // Pause/seek can settle directly on the current phrase without stale predecessors.
        assertEquals(PopPhraseWindow(1, null), window(start, false))
        assertEquals(PopPhraseWindow(0, null), window(1000, false))
        assertEquals(PopPhraseWindow(1, 0), window(start))
        assertEquals(original, timeline.tokens)
    }

    @Test fun retirementNeverBuildsUpOlderPhrasesAndMicroPhrasesAppearImmediately() {
        val timeline = PopLyricTimeline(tokens("让音乐陪你走过夜晚"), true)
        val pages = listOf(PopLyricPage(0, 3), PopLyricPage(3, 6), PopLyricPage(6, 9))
        val handoffs = popPhraseHandoffs(pages, timeline)
        val start = timeline.tokens[6].startMs
        assertEquals(PopPhraseWindow(2, 1), popPhraseWindow(pages, timeline, handoffs, start, true))
        assertEquals(PopPhraseWindow(2, null), popPhraseWindow(pages, timeline, handoffs, start + handoffs[2].durationMs, true))
        val fast = PopLyricTimeline(listOf(PopLyricToken("你", 1000, 1080), PopLyricToken("好", 1080, 1160)), true)
        val fastPages = listOf(PopLyricPage(0, 1), PopLyricPage(1, 2))
        assertEquals(PopPhraseWindow(1, null), popPhraseWindow(fastPages, fast,
            popPhraseHandoffs(fastPages, fast), 1080, true))
    }
}
