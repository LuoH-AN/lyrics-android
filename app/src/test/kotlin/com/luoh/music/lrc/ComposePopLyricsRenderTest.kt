package com.luoh.music.lrc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposePopLyricsRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun renderStage(landscape: Boolean) {
        compose.activity.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit().clear()
            .putString(HomeLyricsPrefs.KEY_DISPLAY_MODE, HomeLyricsPrefs.POP)
            .putBoolean(HomeLyricsPrefs.KEY_ALBUM_BACKGROUND, false).commit()
        val state = HomePresentationState().apply {
            setSnapshot(HomePresentationState.Snapshot(track = "晚风与回声", artist = "逐词跃动",
                positionMs = 1000, durationMs = 10000))
            setLyrics(LyricDocument(listOf(LyricLine(1000, "让音乐陪你", "Let the music stay", 4500,
                listOf(LyricWord(1000, 1000, "让"), LyricWord(2000, 1000, "音乐"), LyricWord(3000, 1000, "陪你")))), true))
            setActive(true)
        }
        lateinit var root: View
        compose.setContent {
            val view = LocalView.current
            SideEffect { root = view }
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = false)) {
                MaterialTheme { Box(Modifier.size(if (landscape) 800.dp else 400.dp, if (landscape) 400.dp else 800.dp)) { ComposeHomeScreen(state, {}, {}, {}) } }
            }
        }
        fun capture(time: Long, name: String): Int {
            compose.runOnIdle { state.setProgress(time, false) }
            compose.waitForIdle()
            // The clickable sentence merges its children's semantics for accessibility.
            val bounds = compose.onNodeWithTag("home_pop_canvas", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            var painted = 0
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val background = bitmap.getPixel(bounds.left.toInt().coerceIn(0, bitmap.width - 1),
                    bounds.top.toInt().coerceIn(0, bitmap.height - 1))
                for (y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(bitmap.height)) {
                    for (x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(bitmap.width)) {
                        if (bitmap.getPixel(x, y) != background) painted++
                    }
                }
                val output = File("build/reports/material-ui/$name.png")
                output.parentFile.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            return painted
        }
        val suffix = if (landscape) "-landscape" else ""
        val first = capture(1100, "pop-first-word$suffix")
        val second = capture(2500, "pop-second-word$suffix")
        val complete = capture(4200, "pop-complete-sentence$suffix")
        assertTrue("The native canvas must draw the first word", first > 0)
        assertTrue("A later word must enter while the previous word stays visible", second > first)
        assertTrue("The complete sentence must retain the sung words", complete > second)
        compose.onNodeWithContentDescription("收起播放控制栏").performTouchInput { click() }
        capture(2500, "pop-compact$suffix")
        val before = compose.onNodeWithTag("home_pop_canvas", useUnmergedTree = true).fetchSemanticsNode().size
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(Offset(width * .8f, centerY), Offset(width * .2f, centerY), durationMillis = 500)
        }
        val after = compose.onNodeWithTag("home_pop_canvas", useUnmergedTree = true).fetchSemanticsNode().size
        assertTrue(compose.onNodeWithTag("home_player").fetchSemanticsNode().size.width <= 56)
        if (landscape) assertTrue(after.width > before.width + 100) else assertTrue(after.height > before.height)
        assertTrue(capture(2500, "pop-cover-only$suffix") > 0)
    }

    @Test fun nativeCanvasRevealsMoreWordsAsTheRealTimelineAdvances() = renderStage(false)

    @Test
    @Config(qualifiers = "w800dp-h400dp-mdpi")
    fun landscapeStageAndAllThreePlayerFormsRenderNatively() = renderStage(true)
}
