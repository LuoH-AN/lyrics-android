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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
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
import com.luoh.music.lrc.ui.home.pop.PopLyricsStyle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposePopLyricsRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun renderStage(landscape: Boolean) {
        compose.activity.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit().clear()
            .putString(HomeLyricsPrefs.KEY_DISPLAY_MODE, HomeLyricsPrefs.POP)
            .putBoolean(HomeLyricsPrefs.KEY_ALBUM_BACKGROUND, false).commit()
        // Isolate retention from active-word magnification and phrase pagination.
        // Visible-word fitting changes ink area, so frame identity is checked separately.
        PopLyricsPrefs.save(compose.activity, PopLyricsStyle(activeScalePercent = 100, paginate = false))
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
        val fingerprints = HashMap<String, Long>()
        fun capture(time: Long, name: String): Int {
            compose.runOnIdle { state.setProgress(time, false) }
            compose.waitForIdle()
            // The clickable sentence merges its children's semantics for accessibility.
            val bounds = compose.onNodeWithTag("home_pop_canvas", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val captionTop = compose.onAllNodesWithTag("home_pop_translation", useUnmergedTree = true)
                .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top ?: bounds.bottom
            var painted = 0
            var fingerprint = 17L
            var paintedBottom = Int.MIN_VALUE
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val background = bitmap.getPixel(bounds.left.toInt().coerceIn(0, bitmap.width - 1),
                    bounds.top.toInt().coerceIn(0, bitmap.height - 1))
                for (y in bounds.top.toInt().coerceAtLeast(0) until minOf(bounds.bottom, captionTop).toInt().coerceAtMost(bitmap.height)) {
                    for (x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(bitmap.width)) {
                        val pixel = bitmap.getPixel(x, y)
                        // Compare actual ink, excluding low-opacity glow around the active word.
                        val contrast = kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(background)) +
                            kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(background)) +
                            kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(background))
                        if (contrast > 300) {
                            painted++
                            paintedBottom = y
                            fingerprint = fingerprint * 31 + x + y.toLong() * bitmap.width
                        }
                    }
                }
                fingerprints[name] = fingerprint
                if (name.startsWith("pop-first-word") || name.startsWith("pop-second-word") || name.startsWith("pop-complete-sentence")) {
                    val file = File("build/reports/tests/testDebugUnitTest/visuals/$name.png")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
                bitmap.recycle()
            }
            if (name.startsWith("pop-complete-sentence")) assertTrue(
                "The bilingual caption must stay close to the rendered phrase",
                captionTop - paintedBottom in 8f..90f)
            return painted
        }
        val suffix = if (landscape) "-landscape" else ""
        if (landscape) {
            capture(4200, "pop-landscape-default")
            val cover = compose.onNodeWithTag("home_player").fetchSemanticsNode().boundsInRoot
            assertTrue(cover.width <= 56 && cover.right >= 780 && cover.bottom >= 380)
            compose.onNodeWithTag("home_player").performTouchInput {
                swipe(center, Offset(centerX, centerY - 100f), durationMillis = 300)
            }
            capture(2500, "pop-landscape-up-compact")
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("home_player").performTouchInput {
                down(center)
                moveBy(Offset(0f, -90f), delayMillis = 150)
            }
            compose.mainClock.advanceTimeByFrame()
            capture(2500, "pop-landscape-drag-up")
            compose.onNodeWithTag("home_player").performTouchInput { up() }
            compose.mainClock.advanceTimeByFrame()
            capture(2500, "pop-landscape-release-up")
            compose.mainClock.advanceTimeBy(180)
            capture(2500, "pop-landscape-settling-up")
            compose.mainClock.advanceTimeBy(450)
            compose.mainClock.autoAdvance = true
        }
        val first = capture(1100, "pop-first-word$suffix")
        val second = capture(2500, "pop-second-word$suffix")
        val complete = capture(4200, "pop-complete-sentence$suffix")
        assertTrue(capture(3400, "pop-follow-last-word$suffix") > 0)
        assertTrue(capture(4450, "pop-return-to-center$suffix") > 0)
        assertTrue("The native canvas must draw the first word", first > 0)
        assertTrue("Later words must produce native ink", second > 0 && complete > 0)
        assertTrue("The next onset must change the painted lyric", fingerprints["pop-first-word$suffix"] != fingerprints["pop-second-word$suffix"])
        assertTrue("The final onset must change the painted lyric", fingerprints["pop-second-word$suffix"] != fingerprints["pop-complete-sentence$suffix"])
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
        val left = compose.onNodeWithTag("home_player").fetchSemanticsNode().boundsInRoot
        assertTrue(left.left <= 20f && left.bottom >= (if (landscape) 400 else 800) - 25f)
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(center, Offset(centerX + 160f, centerY), durationMillis = 500)
        }
        compose.onNodeWithTag("home_player").performTouchInput {
            swipe(Offset(width * .2f, centerY), Offset(width * .8f, centerY), durationMillis = 500)
        }
        val right = compose.onNodeWithTag("home_player").fetchSemanticsNode().boundsInRoot
        assertTrue(right.right >= (if (landscape) 800 else 400) - 20f)
        capture(2500, "pop-right-cover$suffix")
        compose.runOnIdle { PopLyricsPrefs.save(compose.activity, PopLyricsStyle(reveal = "character", layout = "stairs", entrance = "scatter",
            travelPercent = 150, rotationDegrees = 15, activeScalePercent = 150)) }
        assertTrue(capture(2500, "pop-dynamic$suffix") > 0)
        compose.runOnIdle { PopLyricsPrefs.save(compose.activity, PopLyricsPrefs.style(compose.activity).copy(camera = "off")) }
        assertTrue(capture(2500, "pop-fixed-camera$suffix") > 0)
        if (!landscape) {
            compose.runOnIdle {
                PopLyricsPrefs.save(compose.activity, PopLyricsStyle())
                val words = listOf("让", "音乐", "陪你", "走过", "每一个", "夜晚")
                state.setLyrics(LyricDocument(listOf(LyricLine(1000, words.joinToString(""), endMs = 9500,
                    words = words.mapIndexed { index, text -> LyricWord(1000 + index * 700L, 700, text) })), true))
            }
            assertTrue(capture(6500, "pop-portrait-long-default") > 0)
            compose.runOnIdle { HomeLyricsPrefs.saveAppearance(compose.activity, HomeLyricAppearance(fontSize = 48)) }
            assertTrue(capture(6500, "pop-portrait-large-type") > 0)
            compose.runOnIdle { HomeLyricsPrefs.saveAppearance(compose.activity, HomeLyricAppearance(fontSize = 16)) }
            assertTrue(capture(6500, "pop-portrait-small-type") > 0)
        }
        compose.runOnIdle { HomeLyricsPrefs.saveAppearance(compose.activity, HomeLyricAppearance()) }
        val samples = listOf(
            "short" to listOf("晚风", "轻轻", "吹"),
            "long" to listOf("如果", "有一天", "我们", "走散了", "，", "就", "让", "这首歌", "带你", "找到", "回家的", "方向"),
            "english" to listOf("Let ", "the ", "music ", "carry ", "us ", "through ", "the ", "longest ", "night"),
            "characters" to listOf("让", "音乐", "陪你", "走过", "每一个", "夜晚"),
            "syllables" to listOf("音", "乐", "陪", "伴", "我", "们", "走", "过", "世", "界", "。"),
        )
        for (layout in listOf("centered", "staggered", "stairs")) {
            for ((name, words) in samples) {
                compose.runOnIdle {
                    PopLyricsPrefs.save(compose.activity, PopLyricsStyle(layout = layout,
                        reveal = if (name == "characters") "character" else "word"))
                    state.setLyrics(LyricDocument(listOf(LyricLine(1000, words.joinToString(""), endMs = 9500,
                        words = words.mapIndexed { index, word -> LyricWord(1000 + index * 250L, 250, word) })), true))
                }
                assertTrue(capture(8500, "pop-layout-$layout-$name$suffix") > 0)
            }
            compose.runOnIdle {
                state.setLyrics(LyricDocument(listOf(LyricLine(1000,
                    "在每一个安静的夜晚，让音乐陪你走过漫长的旅途，直到重新看见明亮的晨光。", endMs = 9500)), true))
            }
            assertTrue(capture(8500, "pop-layout-$layout-sentence$suffix") > 0)
        }
        compose.runOnIdle {
            PopLyricsPrefs.save(compose.activity, PopLyricsStyle())
            state.setLyrics(LyricDocument(listOf(LyricLine(1000, "啊", endMs = 1080,
                words = listOf(LyricWord(1000, 80, "啊")))), true))
        }
        assertTrue("A micro sentence must be visible on its first frame", capture(1000, "pop-micro-first$suffix") > 0)
        assertTrue(capture(1040, "pop-micro-middle$suffix") > 0)
        val detailedCover = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width)
                setPixel(x, y, android.graphics.Color.rgb(x, (x * 3 + y * 5) % 256, y))
        }
        compose.runOnIdle {
            PopLyricsPrefs.save(compose.activity, PopLyricsStyle())
            HomeLyricsPrefs.saveAlbumBackground(compose.activity, true)
            state.setSnapshot(state.snapshot.copy(cover = detailedCover))
            state.setLyrics(LyricDocument(listOf(LyricLine(1000, "让音乐陪你", endMs = 9500,
                words = listOf(LyricWord(1000, 1000, "让"), LyricWord(2000, 1000, "音乐"), LyricWord(3000, 1000, "陪你")))), true))
        }
        fun corners(): List<Int> {
            var pixels = emptyList<Int>()
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                pixels = listOf(8 to 8, root.width - 8 to 8, 8 to root.height - 8, root.width - 8 to root.height - 8)
                    .map { (x, y) -> bitmap.getPixel(x, y) }
                bitmap.recycle()
            }
            return pixels
        }
        compose.waitUntil(timeoutMillis = 5000) {
            corners().any { kotlin.math.abs(android.graphics.Color.red(it) - android.graphics.Color.blue(it)) > 10 }
        }
        capture(1100, "pop-stable-background-first$suffix")
        val firstBackground = corners()
        capture(3500, "pop-stable-background-last$suffix")
        assertEquals("Detailed artwork must stay still while the words and camera move", firstBackground, corners())
    }

    @Test fun nativeCanvasRevealsMoreWordsAsTheRealTimelineAdvances() = renderStage(false)

    @Test
    @Config(qualifiers = "zh-rCN-w800dp-h400dp-mdpi")
    fun landscapeStageAndAllThreePlayerFormsRenderNatively() = renderStage(true)
}
