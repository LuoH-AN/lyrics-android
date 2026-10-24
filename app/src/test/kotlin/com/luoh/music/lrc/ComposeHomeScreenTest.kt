package com.luoh.music.lrc

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

internal class RecordingHomeActions : HomePresentationState.Actions {
    val calls = mutableListOf<String>()
    val seeks = mutableListOf<Long>()
    override fun toggleOverlay() { calls += "overlay" }
    override fun openSettings() { calls += "settings" }
    override fun seekTo(positionMs: Long) { seeks += positionMs }
    override fun togglePlay() { calls += "play" }
    override fun skipPrev() { calls += "previous" }
    override fun skipNext() { calls += "next" }
    override fun retryLyrics() { calls += "retry" }
    override fun editCustomLyrics() { calls += "custom" }
    override fun manageCustomLyrics() { calls += "library" }
    override fun manageLyricSources() { calls += "sources" }
    override fun exportLrc() { calls += "export" }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class ComposeHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun permissionActionsReachTheirCallbacksFromTheActualHomeGate() {
        val calls = mutableListOf<String>()
        val state = HomePresentationState().apply { gateVisible = true }
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(400.dp, 700.dp)) {
                    ComposeHomeScreen(state, { calls += "listener" }, { calls += "permission" }, { calls += "start" })
                }
            }
        }
        compose.onNodeWithText("通知使用权").performTouchInput { click() }
        compose.onNodeWithText("悬浮窗权限").performTouchInput { click() }
        compose.onNodeWithText("开启桌面歌词").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("listener", "permission", "start"), calls) }
    }

    @Test fun emptyLyricsKeepRetryCustomAndPlaybackActionsAndWaitForOverlayConfirmation() {
        val actions = RecordingHomeActions()
        val state = HomePresentationState().apply {
            this.actions = actions
            setSnapshot(HomePresentationState.Snapshot(track = "Song", artist = "Artist", durationMs = 10_000))
            setLyrics(LyricDocument(emptyList(), true))
        }
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { Box(Modifier.size(400.dp, 700.dp)) { ComposeHomeScreen(state, {}, {}, {}) } }
            }
        }
        compose.onNodeWithText("未找到歌词").assertExists()
        compose.onNodeWithContentDescription("上一首").assertIsNotEnabled()
        compose.onNodeWithContentDescription("重新获取歌词").performTouchInput { click() }
        compose.onNodeWithContentDescription("添加自定义歌词").performTouchInput { click() }
        compose.onNodeWithContentDescription("播放").performTouchInput { click() }
        compose.onNodeWithContentDescription("显示桌面歌词").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("retry", "custom", "play", "overlay"), actions.calls) }
        compose.onNodeWithContentDescription("显示桌面歌词").assertExists()
        compose.runOnIdle { state.setOverlayState(true) }
        compose.onNodeWithContentDescription("关闭桌面歌词").assertExists()
    }

    @Test fun moreSheetDimmingFollowsCloseMotionAndRepeatedOpenUsesANewDialog() {
        val actions = RecordingHomeActions()
        val state = HomePresentationState().apply {
            this.actions = actions
            setSnapshot(HomePresentationState.Snapshot(track = "Song", artist = "Artist", durationMs = 10_000))
            setLyrics(LyricDocument(emptyList(), true))
        }
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy.Default) {
                MaterialTheme { Box(Modifier.size(400.dp, 700.dp)) { ComposeHomeScreen(state, {}, {}, {}) } }
            }
        }
        var previousDialog: android.app.Dialog? = null
        repeat(2) {
            compose.onNodeWithContentDescription("更多").performTouchInput { click() }
            compose.onNodeWithText("更多").assertExists()
            lateinit var dialog: android.app.Dialog
            var partialDim = 0f
            var dismissCallbacks = 0
            compose.runOnIdle {
                dialog = requireNotNull(ShadowDialog.getLatestDialog())
                assertTrue(dialog.isShowing)
                previousDialog?.let { assertNotSame(it, dialog) }
                dialog.setOnDismissListener { dismissCallbacks++ }
                partialDim = requireNotNull(dialog.window).attributes.dimAmount
                assertEquals(0.32f * 0.45f, partialDim, 0.002f)
            }
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("取消"))
            compose.mainClock.autoAdvance = false
            // Repeated cancellation during one close must share the same dismissal.
            repeat(2) { compose.onNodeWithText("取消").performTouchInput { click() } }
            repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
            compose.runOnIdle {
                assertTrue("The dialog remains alive while the sheet exits", dialog.isShowing)
                val dim = requireNotNull(dialog.window).attributes.dimAmount
                assertTrue("Dimming must fall during the same frames as the sheet", dim > 0f && dim < partialDim)
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.runOnIdle {
                assertFalse(dialog.isShowing)
                assertEquals(1, dismissCallbacks)
                assertEquals(0f, requireNotNull(dialog.window).attributes.dimAmount, 0.002f)
                assertTrue(actions.calls.isEmpty())
            }
            compose.onNodeWithText("更多").assertDoesNotExist()
            previousDialog = dialog
        }
        // After cancellation/reopen cycles a selected action still runs exactly once, after exit.
        compose.onNodeWithContentDescription("更多").performTouchInput { click() }
        compose.mainClock.autoAdvance = false
        repeat(2) { compose.onNodeWithText("添加自定义歌词").performTouchInput { click() } }
        compose.runOnIdle { assertTrue(actions.calls.isEmpty()) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("custom"), actions.calls) }
        compose.onNodeWithText("更多").assertDoesNotExist()
    }
}
