package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
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

    @Before fun clearHomePreferences() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

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
        compose.onNodeWithContentDescription("开启桌面歌词").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("retry", "custom", "play", "overlay"), actions.calls) }
        compose.onNodeWithContentDescription("开启桌面歌词").assertExists()
        compose.runOnIdle { state.setOverlayState(true) }
        compose.onNodeWithContentDescription("关闭桌面歌词").assertExists()
    }

    @Test fun swipingPlayerKeepsCompactActionsAndRestoresPlaybackControls() {
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
        val fullHeight = compose.onNodeWithTag("home_player").fetchSemanticsNode().size.height
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(Offset(80f, 30f), Offset(80f, 150f), durationMillis = 500)
        }
        compose.onNodeWithContentDescription("展开播放控制栏").assertIsDisplayed()
        compose.onNodeWithText("Song").assertIsDisplayed()
        compose.onNodeWithText("Artist").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放").assertDoesNotExist()
        compose.onNodeWithContentDescription("上一首").assertDoesNotExist()
        compose.onNodeWithContentDescription("下一首").assertDoesNotExist()
        assertTrue(compose.onNodeWithTag("home_player").fetchSemanticsNode().size.height < fullHeight - 80)
        compose.onNodeWithContentDescription("开启桌面歌词").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("overlay"), actions.calls) }
        compose.onNodeWithContentDescription("开启桌面歌词").assertExists()
        compose.onNodeWithContentDescription("更多").performTouchInput { click() }
        compose.onNodeWithText("更多").assertIsDisplayed()
        compose.onNodeWithText("取消").assertDoesNotExist()
        compose.runOnIdle { requireNotNull(ShadowDialog.getLatestDialog()).onBackPressed() }
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(Offset(80f, 35f), Offset(80f, -100f), durationMillis = 500)
        }
        compose.onNodeWithContentDescription("收起播放控制栏").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("overlay", "play"), actions.calls) }
        assertEquals(fullHeight, compose.onNodeWithTag("home_player").fetchSemanticsNode().size.height)
    }

    @Test fun compactPlayerSurvivesSavedStateRestorationAndTrackChanges() {
        val state = HomePresentationState().apply {
            setSnapshot(HomePresentationState.Snapshot(track = "Song", artist = "Artist"))
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { Box(Modifier.size(400.dp, 700.dp)) { ComposeHomeScreen(state, {}, {}, {}) } }
            }
        }
        compose.onNodeWithContentDescription("收起播放控制栏").performTouchInput { click() }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.setSnapshot(HomePresentationState.Snapshot(track = "Next Song", artist = "Next Artist")) }
        compose.onNodeWithText("Next Song").assertIsDisplayed()
        compose.onNodeWithContentDescription("展开播放控制栏").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放").assertDoesNotExist()
        compose.onNodeWithContentDescription("展开播放控制栏").performTouchInput { click() }
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    @Test fun horizontalProgressDragSeeksWithoutCollapsingThePlayer() {
        val actions = RecordingHomeActions()
        val state = HomePresentationState().apply {
            this.actions = actions
            setSnapshot(HomePresentationState.Snapshot(track = "Song", durationMs = 10_000))
        }
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { Box(Modifier.size(400.dp, 700.dp)) { ComposeHomeScreen(state, {}, {}, {}) } }
            }
        }
        compose.onNode(hasContentDescription("播放进度", substring = true)).performTouchInput {
            swipe(Offset(width * .25f, centerY), Offset(width * .75f, centerY), durationMillis = 500)
        }
        compose.runOnIdle { assertTrue(actions.seeks.last() > 5_000) }
        compose.onNodeWithContentDescription("收起播放控制栏").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    @Test fun compactCoverShowsProgressAndTogglesPlaybackWithoutExpanding() {
        val actions = RecordingHomeActions()
        val state = HomePresentationState().apply {
            this.actions = actions
            setSnapshot(HomePresentationState.Snapshot(track = "Song", positionMs = 4_000, durationMs = 10_000))
        }
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { Box(Modifier.size(400.dp, 700.dp)) { ComposeHomeScreen(state, {}, {}, {}) } }
            }
        }
        compose.onNodeWithTag("home_cover_progress").assertDoesNotExist()
        compose.onNodeWithContentDescription("专辑封面").performTouchInput { click() }
        compose.runOnIdle { assertTrue(actions.calls.isEmpty()) }
        compose.onNodeWithContentDescription("收起播放控制栏").performTouchInput { click() }
        compose.onNodeWithTag("home_cover_progress").assertRangeInfoEquals(ProgressBarRangeInfo(.4f, 0f..1f))
        assertTrue("Compact controls should occupy at most 80dp", compose.onNodeWithTag("home_player").fetchSemanticsNode().size.height <= 80)
        compose.onNodeWithContentDescription("开始播放").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("play"), actions.calls)
            state.setProgress(6_000, false)
        }
        compose.onNodeWithTag("home_cover_progress").assertRangeInfoEquals(ProgressBarRangeInfo(.6f, 0f..1f))
        compose.runOnIdle { state.setProgress(6_000, true) }
        compose.onNodeWithContentDescription("暂停播放").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("play", "play"), actions.calls) }
        compose.onNodeWithContentDescription("展开播放控制栏").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放").assertDoesNotExist()
    }

    private fun exerciseThreePlayerForms(widthDp: Int, heightDp: Int) {
        val actions = RecordingHomeActions()
        val state = HomePresentationState().apply {
            this.actions = actions
            setSnapshot(HomePresentationState.Snapshot(track = "Song", artist = "Artist", durationMs = 10_000))
            setLyrics(LyricDocument(listOf(LyricLine(0, "歌词保持可见", endMs = 10_000)), true))
        }
        HomeLyricsPrefs.saveDisplayMode(ApplicationProvider.getApplicationContext(), HomeLyricsPrefs.POP)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { Box(Modifier.size(widthDp.dp, heightDp.dp)) { ComposeHomeScreen(state, {}, {}, {}) } }
            }
        }
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(Offset(80f, 30f), Offset(80f, 150f), durationMillis = 500)
        }
        compose.onNodeWithContentDescription("展开播放控制栏").assertIsDisplayed()
        val compact = compose.onNodeWithTag("home_player").fetchSemanticsNode().size
        val lyricBefore = compose.onNodeWithTag("home_pop_stage").fetchSemanticsNode().size
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(Offset(width * .8f, centerY), Offset(width * .2f, centerY), durationMillis = 500)
        }
        val cover = compose.onNodeWithTag("home_player").fetchSemanticsNode().size
        assertEquals(56, cover.width)
        assertEquals(56, cover.height)
        assertTrue(cover.width < compact.width / 2)
        compose.onNodeWithText("Song").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多").assertDoesNotExist()
        compose.onNodeWithContentDescription("开启桌面歌词").assertDoesNotExist()
        compose.onNodeWithText("歌词保持可见").assertIsDisplayed()
        val lyricAfter = compose.onNodeWithTag("home_pop_stage").fetchSemanticsNode().size
        if (widthDp > heightDp) assertTrue(lyricAfter.width > lyricBefore.width + 100)
        else assertTrue(lyricAfter.height > lyricBefore.height)
        compose.onNodeWithContentDescription("开始播放").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("play"), actions.calls) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.setSnapshot(HomePresentationState.Snapshot(track = "Next Song", durationMs = 10_000)) }
        assertEquals(56, compose.onNodeWithTag("home_player").fetchSemanticsNode().size.width)
        compose.onNodeWithText("Next Song").assertDoesNotExist()
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(center, Offset(centerX + 160f, centerY), durationMillis = 500)
        }
        compose.onNodeWithText("Next Song").assertIsDisplayed()
        compose.onNodeWithContentDescription("展开播放控制栏").assertIsDisplayed()
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(Offset(80f, 35f), Offset(80f, -100f), durationMillis = 500)
        }
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
        // Cover-only also has a discoverable long-press route back to the compact row.
        compose.onNodeWithContentDescription("收起播放控制栏").performTouchInput { click() }
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(Offset(width * .8f, centerY), Offset(width * .2f, centerY), durationMillis = 500)
        }
        compose.onNodeWithContentDescription("开始播放").performTouchInput { longClick() }
        compose.onNodeWithText("Next Song").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("play"), actions.calls) }
    }

    @Test fun portraitPlayerRetractsToOnlyTheCoverAndRestoresItsControls() = exerciseThreePlayerForms(400, 700)

    @Test
    @Config(qualifiers = "w800dp-h400dp-mdpi")
    fun phoneLandscapePlayerFreesLyricWidthAndPreservesTheSameGestures() = exerciseThreePlayerForms(800, 400)

    @Test
    @Config(qualifiers = "w800dp-h800dp-mdpi")
    fun coverOnlyStateSurvivesPortraitLandscapeLayoutChanges() {
        val landscape = mutableStateOf(false)
        val state = HomePresentationState().apply { setSnapshot(HomePresentationState.Snapshot(track = "Song")) }
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme {
                    Box(Modifier.size(if (landscape.value) 800.dp else 400.dp, if (landscape.value) 400.dp else 700.dp)) {
                        ComposeHomeScreen(state, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("收起播放控制栏").performTouchInput { click() }
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(Offset(width * .8f, centerY), Offset(width * .2f, centerY), durationMillis = 500)
        }
        repeat(2) {
            compose.runOnIdle { landscape.value = !landscape.value }
            assertEquals(56, compose.onNodeWithTag("home_player").fetchSemanticsNode().size.width)
            compose.onNodeWithContentDescription("开始播放").assertIsDisplayed()
            compose.onNodeWithText("Song").assertDoesNotExist()
        }
    }

    private fun exerciseRightDock(widthDp: Int, heightDp: Int) {
        val state = HomePresentationState().apply { setSnapshot(HomePresentationState.Snapshot(track = "Song")) }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { Box(Modifier.size(widthDp.dp, heightDp.dp)) { ComposeHomeScreen(state, {}, {}, {}) } }
            }
        }
        val fullBounds = compose.onNodeWithTag("home_player").fetchSemanticsNode().boundsInRoot
        assertTrue(fullBounds.width < widthDp * .9f)
        assertTrue(fullBounds.height < heightDp * .8f)
        assertTrue(fullBounds.bottom >= heightDp - 25f)
        assertTrue(fullBounds.left <= 20f)
        compose.onNodeWithContentDescription("收起播放控制栏").performTouchInput { click() }
        val compactBounds = compose.onNodeWithTag("home_player").fetchSemanticsNode().boundsInRoot
        assertTrue(compactBounds.width < fullBounds.width && compactBounds.width < widthDp * .8f)
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(Offset(width * .2f, centerY), Offset(width * .8f, centerY), durationMillis = 500)
        }
        var cover = compose.onNodeWithTag("home_player").fetchSemanticsNode().boundsInRoot
        assertEquals(56f, cover.width, .1f)
        assertEquals(widthDp - 16f, cover.right, .1f)
        assertEquals(heightDp - 16f, cover.bottom, .1f)
        restoration.emulateSavedInstanceStateRestore()
        cover = compose.onNodeWithTag("home_player").fetchSemanticsNode().boundsInRoot
        assertEquals(widthDp - 16f, cover.right, .1f)
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(center, Offset(centerX - 160f, centerY), durationMillis = 500)
        }
        compose.onNodeWithContentDescription("展开播放控制栏").assertIsDisplayed()
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(Offset(width * .8f, centerY), Offset(width * .2f, centerY), durationMillis = 500)
        }
        cover = compose.onNodeWithTag("home_player").fetchSemanticsNode().boundsInRoot
        assertEquals(16f, cover.left, .1f)
        assertEquals(heightDp - 16f, cover.bottom, .1f)
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(center, Offset(centerX + 160f, centerY), durationMillis = 500)
        }
        compose.onNodeWithContentDescription("展开播放控制栏").assertIsDisplayed()
    }

    @Test fun portraitCompactSwipesDockToEitherBottomCornerAndRestoreInward() = exerciseRightDock(400, 700)

    @Test
    @Config(qualifiers = "w800dp-h400dp-mdpi")
    fun landscapeHasBoundedCardsAndTheSameTwoBottomCornerGestures() = exerciseRightDock(800, 400)

    @Test fun backgroundPreferenceRemovesAndRestoresTheDrawingLayerImmediately() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = HomePresentationState()
        compose.setContent { MaterialTheme { ComposeHomeScreen(state, {}, {}, {}) } }
        compose.onNodeWithTag("home_album_background").assertExists()
        compose.runOnIdle { HomeLyricsPrefs.saveAlbumBackground(context, false) }
        compose.onNodeWithTag("home_album_background").assertDoesNotExist()
        compose.runOnIdle { assertFalse(HomeLyricsPrefs.albumBackground(context)) }
        compose.runOnIdle { HomeLyricsPrefs.saveAlbumBackground(context, true) }
        compose.onNodeWithTag("home_album_background").assertExists()
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
            compose.onNodeWithText("取消").assertDoesNotExist()
            compose.mainClock.autoAdvance = false
            // Repeated Back presses during one close must share the same dismissal.
            compose.runOnIdle { repeat(2) { dialog.onBackPressed() } }
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
        // Let entrance measure the lazy rows before pausing to test interrupted dismissal.
        compose.onNodeWithContentDescription("更多").performTouchInput { click() }
        compose.onNodeWithText("添加自定义歌词").assertExists()
        compose.mainClock.autoAdvance = false
        repeat(2) { compose.onNodeWithText("添加自定义歌词").performTouchInput { click() } }
        compose.runOnIdle { assertTrue(actions.calls.isEmpty()) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("custom"), actions.calls) }
        compose.onNodeWithText("更多").assertDoesNotExist()
    }
}
