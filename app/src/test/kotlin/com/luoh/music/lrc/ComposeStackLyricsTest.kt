package com.luoh.music.lrc

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class ComposeStackLyricsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun state(lines: List<LyricLine>) = HomePresentationState().apply {
        setSnapshot(HomePresentationState.Snapshot(track = "堆叠测试", durationMs = 60000))
        setLyrics(LyricDocument(lines, true))
        setActive(true)
    }

    private fun show(state: HomePresentationState, style: PopLyricsStyle = PopLyricsStyle(
        presentation = "stack", stackScrollMs = 0, pageLength = 24, camera = "off", breathing = false)) {
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                val frame = rememberPopPlayback(state, style)
                MaterialTheme { PopLyricsStage(state, Modifier.size(400.dp, 700.dp), false,
                    HomeLyricAppearance(fontSize = 16), style, frame) }
            }
        }
    }

    private fun text() = compose.onNodeWithTag("home_pop_stack_canvas").fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("\n") { it.text }

    @Test fun historySurvivesSourceLineChangesAndSeeksRestoreItAfterClipping() {
        val lines = (0..11).map { LyricLine(1000L + it * 2000L, "第${it}行歌词", endMs = 2000L + it * 2000L) }
        val state = state(lines)
        state.setProgress(3500, false)
        show(state)
        assertTrue(text().contains(lines[0].text))
        assertTrue(text().contains(lines[1].text))
        assertFalse(text().contains(lines[2].text))
        compose.runOnIdle { state.setProgress(23500, false) }
        assertFalse(text().contains(lines[0].text))
        assertTrue(text().contains(lines.last().text))
        compose.runOnIdle { state.setProgress(3500, false) }
        assertTrue(text().contains(lines[0].text))
        assertTrue(text().contains(lines[1].text))
    }

    @Test fun anInterludeKeepsThePreviousLyricsAndChangingSongsClearsTheirHistory() {
        val state = state(listOf(LyricLine(1000, "唱完仍然保留", endMs = 2000), LyricLine(12000, "下一句", endMs = 14000)))
        state.setProgress(7000, false)
        show(state)
        assertTrue(text().contains("唱完仍然保留"))
        assertFalse(text().contains("下一句"))
        compose.runOnIdle {
            state.setLyrics(LyricDocument(listOf(LyricLine(0, "新的歌曲", endMs = 10000)), true))
            state.setProgress(1000, false)
        }
        assertEquals("新的歌曲", text())
    }

    @Test fun bilingualTranslationsRemainAttachedToTheirOwnHistoricalLines() {
        val state = state(listOf(LyricLine(1000, "第一句", "First sentence", 2000),
            LyricLine(3000, "第二句", "Second sentence", 4000)))
        state.setTranslationMode(LyricsOverlayService.TRANSLATION_BILINGUAL)
        state.setProgress(3500, false)
        show(state)
        assertTrue(text().contains("第一句\nFirst sentence"))
        assertTrue(text().contains("第二句\nSecond sentence"))
    }

    @Test fun hidingStackedTranslationsKeepsPrimaryLyricsReadable() {
        val state = state(listOf(LyricLine(1000, "第一句", "First sentence", 2000)))
        state.setTranslationMode(LyricsOverlayService.TRANSLATION_BILINGUAL)
        state.setProgress(1500, false)
        show(state, PopLyricsStyle(presentation = "stack", stackTranslations = false))
        assertEquals("第一句", text())
    }

    @Test fun sharingAShortPhraseStillUsesTheCompleteSourceLine() {
        val source = "让音乐陪着你穿过城市的灯火到天亮"
        val line = LyricLine(1000, source, endMs = 10000,
            words = source.mapIndexed { index, char -> LyricWord(1000L + index * 250, 250, char.toString()) })
        val state = state(listOf(line))
        state.setProgress(1800, false)
        show(state, PopLyricsStyle(presentation = "stack", pageLength = 4))
        compose.onNodeWithTag("home_pop_stack_canvas").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.runOnIdle {
            val draft = requireNotNull(state.shareDraft)
            assertEquals(0, draft.initialIndex)
            assertEquals(source, draft.lines[draft.initialIndex].text)
        }
    }
}
