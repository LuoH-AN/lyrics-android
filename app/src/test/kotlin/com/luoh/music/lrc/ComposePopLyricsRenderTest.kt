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

    @Test fun nativeCanvasRevealsMoreWordsAsTheRealTimelineAdvances() {
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
                MaterialTheme { Box(Modifier.size(400.dp, 800.dp)) { ComposeHomeScreen(state, {}, {}, {}) } }
            }
        }
        fun capture(time: Long, name: String): Int {
            compose.runOnIdle { state.setProgress(time, false) }
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("home_pop_canvas").fetchSemanticsNode().boundsInRoot
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
        val first = capture(1100, "pop-first-word")
        val second = capture(2500, "pop-second-word")
        val complete = capture(4500, "pop-complete-sentence")
        assertTrue("The native canvas must draw the first word", first > 0)
        assertTrue("A later word must enter while the previous word stays visible", second > first)
        assertTrue("The complete sentence must retain the sung words", complete > second)
    }
}
