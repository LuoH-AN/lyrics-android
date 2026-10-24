package com.luoh.music.lrc

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
