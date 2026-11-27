package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.lyrics.LyricClock
import com.luoh.music.lrc.lyrics.LyricDocument
import com.luoh.music.lrc.lyrics.LyricLine
import com.luoh.music.lrc.lyrics.LyricWord
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.home.ComposeHomeScreen
import com.luoh.music.lrc.ui.home.HomeLyricAppearance
import com.luoh.music.lrc.ui.home.HomeLyricsPrefs
import com.luoh.music.lrc.ui.home.HomePresentationState
import com.luoh.music.lrc.ui.home.pop.PopLyricsPrefs
import com.luoh.music.lrc.ui.home.pop.PopLyricsStage
import com.luoh.music.lrc.ui.home.pop.PopLyricsStyle
import com.luoh.music.lrc.ui.home.pop.PopPlaybackFrame
import com.luoh.music.lrc.ui.home.pop.rememberPopPlayback
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class ComposePopLyricsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val actions = RecordingHomeActions()
    // Keep playback time independent of Robolectric setup and Compose's frame clock.
    private val state = HomePresentationState(LyricClock { 0L }).apply {
        this.actions = this@ComposePopLyricsTest.actions
        setSnapshot(HomePresentationState.Snapshot(track = "Song", positionMs = 2000, durationMs = 15000))
        setLyrics(LyricDocument(listOf(
            LyricLine(1000, "让音乐陪你", "Let the music stay", 4000,
                listOf(LyricWord(1000, 1000, "让"), LyricWord(2000, 1000, "音乐"), LyricWord(3000, 1000, "陪你"))),
            LyricLine(11000, "直到天亮", endMs = 14000)), true))
        setActive(true)
    }

    @Before fun reset() {
        context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { Box(Modifier.size(400.dp, 700.dp)) { ComposeHomeScreen(state, {}, {}, {}) } }
            }
        }
    }

    @Test fun modePersistsUpdatesTheExistingPageWithoutRedundantNavigation() {
        assertEquals(HomeLyricsPrefs.POP, HomeLyricsPrefs.displayMode(context))
        show()
        compose.onNodeWithTag("home_pop_stage").assertIsDisplayed()
        compose.runOnIdle { HomeLyricsPrefs.saveDisplayMode(context, HomeLyricsPrefs.SCROLL) }
        compose.onNodeWithTag("home_pop_stage").assertDoesNotExist()
        compose.runOnIdle { HomeLyricsPrefs.saveDisplayMode(context, HomeLyricsPrefs.POP) }
        compose.onNodeWithTag("home_pop_stage").assertIsDisplayed()
        compose.onNodeWithText("让音乐陪你").assertIsDisplayed()
        compose.onNodeWithText("Let the music stay").assertIsDisplayed()
        compose.onNodeWithText("浏览歌词").assertDoesNotExist()
        compose.onNodeWithText("返回跃动").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(HomeLyricsPrefs.POP, HomeLyricsPrefs.displayMode(context))
            HomeLyricsPrefs.saveDisplayMode(context, "unknown")
            assertEquals(HomeLyricsPrefs.SCROLL, HomeLyricsPrefs.displayMode(context))
        }
        compose.onNodeWithTag("home_pop_stage").assertDoesNotExist()
    }

    @Test fun pausedProgressChangesSeekAndTranslationUseTheCurrentSentenceImmediately() {
        HomeLyricsPrefs.saveDisplayMode(context, HomeLyricsPrefs.POP)
        show()
        compose.onNodeWithText("让音乐陪你").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(1000L), actions.seeks) }
        compose.runOnIdle {
            state.setProgress(12000, false)
            assertFalse(state.playing)
        }
        compose.onNodeWithText("直到天亮").assertIsDisplayed()
        compose.onNodeWithText("让音乐陪你").assertDoesNotExist()
        compose.runOnIdle {
            state.setProgress(2000, false)
            state.setTranslationMode(LyricsOverlayService.TRANSLATION_TRANSLATED)
        }
        compose.onNodeWithText("Let the music stay").assertIsDisplayed()
        compose.onNodeWithText("让音乐陪你").assertDoesNotExist()
    }

    @Test fun interludeAndSharingRemainAvailable() {
        HomeLyricsPrefs.saveDisplayMode(context, HomeLyricsPrefs.POP)
        state.setProgress(7000, false)
        show()
        compose.onNodeWithTag("home_pop_interlude").assertIsDisplayed()
        compose.runOnIdle { state.setProgress(2000, false) }
        compose.onNodeWithTag("home_pop_interlude").assertDoesNotExist()
        compose.onNodeWithText("让音乐陪你").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(0, requireNotNull(state.shareDraft).initialIndex)
            assertEquals("Song", state.shareDraft?.title)
        }
    }

    @Test fun pausedSeekingSelectsOneSmallPageWhileSharingRetainsTheSourceSentence() {
        val text = "牵着你的手一阵莫名感动让我一直陪你到天亮"
        val line = LyricLine(1000, text, endMs = 11000, words = text.mapIndexed { index, character ->
            LyricWord(1000 + index * 250L, 250, character.toString())
        })
        PopLyricsPrefs.save(context, PopLyricsStyle(pageLength = 4))
        state.setLyrics(LyricDocument(listOf(line), true))
        state.setProgress(2000, false)
        show()
        fun displayed() = compose.onNodeWithTag("home_pop_canvas", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
        val first = displayed()
        assertTrue(first.isNotEmpty() && first.length < text.length)
        compose.runOnIdle { state.setProgress(6000, false) }
        assertNotEquals(first, displayed())
        compose.runOnIdle { state.setProgress(2000, false) }
        assertEquals(first, displayed())
        compose.onNodeWithTag("home_pop_canvas", useUnmergedTree = true).performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(line, requireNotNull(state.shareDraft).lines.single()) }
    }

    @Test fun phraseChangesReuseOneContinuousCameraPathForTheWholeSentence() {
        val text = "牵着你的手一阵莫名感动让我一直陪你到天亮"
        state.setLyrics(LyricDocument(listOf(LyricLine(1000, text, endMs = 11000,
            words = text.mapIndexed { index, character -> LyricWord(1000 + index * 250L, 250, character.toString()) })), true))
        state.setProgress(1800, false)
        val style = PopLyricsStyle(pageLength = 4)
        lateinit var observed: PopPlaybackFrame
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                val frame = rememberPopPlayback(state, style)
                MaterialTheme { PopLyricsStage(state, Modifier.size(400.dp, 700.dp), false,
                    HomeLyricAppearance(), style, frame) }
                SideEffect { observed = frame }
            }
        }
        val path = compose.runOnIdle { requireNotNull(observed.cameraPath.value) }
        compose.runOnIdle { state.setProgress(6000, false) }
        compose.runOnIdle { assertSame("Changing phrases must not restart the camera", path, observed.cameraPath.value) }
        compose.runOnIdle { state.setProgress(1800, false) }
        compose.runOnIdle { assertSame(path, observed.cameraPath.value) }
    }

    @Test fun upcomingTimelineAndVisualTimingAreReusedAfterPreheatAndSeeking() {
        // A frozen playback clock must not be paired with an automatically advancing
        // frame clock: the playing effect would keep requesting virtual frames forever.
        compose.mainClock.autoAdvance = false
        state.setProgress(9900, true)
        lateinit var observed: PopPlaybackFrame
        compose.setContent {
            val frame = rememberPopPlayback(state, PopLyricsStyle())
            SideEffect { observed = frame }
        }
        settlePlaybackUntil { observed.upcoming != null }
        val warmed = compose.runOnIdle { requireNotNull(observed.upcoming) }
        compose.runOnIdle { state.setProgress(11200, false) }
        settlePlaybackUntil { observed.index == 1 && observed.upcoming == null }
        compose.runOnIdle {
            assertSame(warmed.timeline, observed.timeline)
            assertSame(warmed.timing, observed.timing)
            assertNull(observed.upcoming)
        }
        compose.runOnIdle { state.setProgress(9900, true) }
        // Seeking changes the clock, active line and preheat status in successive
        // compositions. Wait for the requested state instead of guessing frame count.
        settlePlaybackUntil { observed.index == 0 && observed.upcoming != null }
        compose.runOnIdle {
            assertSame(warmed.timeline, requireNotNull(observed.upcoming).timeline)
            assertSame(warmed.timing, requireNotNull(observed.upcoming).timing)
        }
        compose.runOnIdle { state.setActive(false) }
        settlePlayback()
        compose.mainClock.autoAdvance = true
    }

    @Test fun preheatOnlyRunsInTheUpcomingWindowWhilePlayingAndVisible() {
        compose.mainClock.autoAdvance = false
        state.setProgress(9799, true)
        lateinit var observed: PopPlaybackFrame
        compose.setContent {
            val frame = rememberPopPlayback(state, PopLyricsStyle())
            SideEffect { observed = frame }
        }
        settlePlayback()
        compose.runOnIdle { assertNull(observed.upcoming) }
        compose.runOnIdle { state.setProgress(9800, false) }
        settlePlayback()
        compose.runOnIdle { assertNull(observed.upcoming) }
        compose.runOnIdle { state.setProgress(9800, true) }
        settlePlayback()
        compose.runOnIdle { assertNotNull(observed.upcoming) }
        compose.runOnIdle { state.setActive(false) }
        settlePlayback()
        compose.runOnIdle { assertNull(observed.upcoming) }
        compose.runOnIdle {
            state.setActive(true)
            state.setProgress(10821, true)
        }
        settlePlayback()
        compose.runOnIdle { assertNull(observed.upcoming) }
        compose.runOnIdle { state.setActive(false) }
        settlePlayback()
        compose.mainClock.autoAdvance = true
    }

    private fun settlePlayback() {
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
    }

    private fun settlePlaybackUntil(condition: () -> Boolean) {
        // SideEffect delivery also needs the Android main queue to drain between
        // frames; advancing only the virtual frame clock cannot wait for it.
        repeat(12) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (compose.runOnIdle(condition)) return
        }
        fail("Playback state did not settle at ${state.clock.positionMs()} ms")
    }

    @Test fun plainLyricsAndMissingLyricsKeepTheirExistingReadablePages() {
        HomeLyricsPrefs.saveDisplayMode(context, HomeLyricsPrefs.POP)
        state.setLyrics(LyricDocument(listOf(LyricLine(0, "没有时间轴的歌词")), false))
        show()
        compose.onNodeWithText("没有时间轴的歌词").assertIsDisplayed()
        compose.onNodeWithTag("home_pop_stage").assertDoesNotExist()
        compose.runOnIdle { state.setLyrics(LyricDocument(emptyList(), true)) }
        compose.onNodeWithText("未找到歌词").assertIsDisplayed()
    }
}
