package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class ComposeHomeInterludeTest {
    @get:Rule val compose = createComposeRule()
    private val document = LyricDocument(listOf(
        LyricLine(6000, "上一句", "上一句译文", endMs = 10000),
        LyricLine(20000, "下一句", "下一句译文", endMs = 24000)), true)

    @Before fun clearPreferences() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun show(positionMs: Long = 15000): HomePresentationState {
        val state = HomePresentationState().apply {
            setSnapshot(HomePresentationState.Snapshot(track = "Song", positionMs = positionMs, durationMs = 30000))
            setLyrics(this@ComposeHomeInterludeTest.document)
            setActive(true)
        }
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { Box(Modifier.size(400.dp, 700.dp)) { ComposeHomeScreen(state, {}, {}, {}) } }
            }
        }
        return state
    }

    @Test fun materialProgressHasItsOwnCenteredSpaceBetweenBilingualLines() {
        val state = show()
        compose.onNodeWithTag("home_interlude_progress").assertIsDisplayed()
            .assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        val row = compose.onNodeWithTag("home_interlude_row").fetchSemanticsNode().boundsInRoot
        val ring = compose.onNodeWithTag("home_interlude_progress").fetchSemanticsNode().boundsInRoot
        val previous = compose.onNodeWithText("上一句译文", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithText("下一句", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The gap must be below the previous translation", row.top > previous.bottom)
        assertTrue("The gap must push the next lyric down", row.bottom < next.top)
        assertEquals(88f, row.height, 1f)
        assertEquals(row.center.x, ring.center.x, 1f)
        assertEquals(row.center.y, ring.center.y, 1f)
        compose.runOnIdle { assertEquals(document, state.document) }
    }

    @Test fun pausedProgressStaysStillAndSeeksUpdateOrRemoveTheRow() {
        val state = show()
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("home_interlude_progress").assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        compose.runOnIdle { state.seek(18000) }
        compose.onNodeWithTag("home_interlude_progress").assertRangeInfoEquals(ProgressBarRangeInfo(.8f, 0f..1f))
        compose.runOnIdle { state.seek(20000) }
        compose.onNodeWithTag("home_interlude_row").assertDoesNotExist()
        compose.runOnIdle { state.seek(12000) }
        compose.onNodeWithTag("home_interlude_progress").assertRangeInfoEquals(ProgressBarRangeInfo(.2f, 0f..1f))
        compose.runOnIdle { state.setSnapshot(HomePresentationState.Snapshot(track = "Next", durationMs = 30000)) }
        compose.onNodeWithTag("home_interlude_row").assertDoesNotExist()
    }

    @Test fun introOutroAndLyricOffsetFollowTheSameClock() {
        val state = show(3000)
        compose.onNodeWithTag("home_interlude_progress").assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        val intro = compose.onNodeWithTag("home_interlude_row").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithText("上一句", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(intro.bottom < first.top)
        compose.runOnIdle { state.seek(27000) }
        compose.onNodeWithTag("home_interlude_progress").assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        compose.runOnIdle { state.seek(9000); state.setLyricOffset(6000) }
        compose.onNodeWithTag("home_interlude_progress").assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        compose.runOnIdle { state.setLyricOffset(0) }
        compose.onNodeWithTag("home_interlude_row").assertDoesNotExist()
    }
}
