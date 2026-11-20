package com.luoh.music.lrc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import org.junit.Assert.*
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
class ComposePopOrchestrationRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var root: View

    private fun capture(): Bitmap {
        compose.waitForIdle()
        return compose.runOnIdle {
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        // Only explicit native test runs produce these synthetic visual review fixtures.
        val file = File("build/reports/tests/testDebugUnitTest/visuals/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun show(state: HomePresentationState, style: PopLyricsStyle, width: Int, height: Int,
        appearance: HomeLyricAppearance = HomeLyricAppearance(), fontScale: Float = 1f) {
        compose.setContent {
            val view = LocalView.current
            SideEffect { root = view }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = false),
                LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    val frame = rememberPopPlayback(state, style)
                    Box(Modifier.size(width.dp, height.dp).background(MaterialTheme.colorScheme.background)) {
                        PopLyricsStage(state, Modifier.size(width.dp, height.dp)
                            .popStageCamera(frame, style, true), false, appearance, style, frame)
                    }
                }
            }
        }
    }

    @Test fun sentenceAndBilingualCaptionFreezeTogetherAndSeekingReproducesTheSameFrame() {
        val state = HomePresentationState(LyricClock { 0L }).apply {
            setSnapshot(HomePresentationState.Snapshot(track = "动效示例", durationMs = 8000))
            setLyrics(LyricDocument(listOf(
                LyricLine(1000, "晚风吹过", "The evening breeze", 2000,
                    listOf(LyricWord(1000, 450, "晚风"), LyricWord(1450, 550, "吹过"))),
                LyricLine(2000, "陪你到天亮", "Stay with you until the morning light", 4600,
                    listOf(LyricWord(2000, 500, "陪你"), LyricWord(2500, 700, "到"), LyricWord(3200, 1400, "天亮")))), true))
            setTranslationMode(LyricsOverlayService.TRANSLATION_BILINGUAL)
            setProgress(2150, false)
            setActive(true)
        }
        show(state, PopLyricsStyle(transition = "zoom", camera = "follow", pushDirection = "random"), 400, 760)
        val first = capture()
        compose.mainClock.advanceTimeBy(1500)
        val paused = capture()
        assertTrue("A paused sentence and its caption must not keep animating on Compose's wall clock", first.sameAs(paused))
        compose.runOnIdle { state.setProgress(3700, false) }
        val complete = capture()
        assertFalse(first.sameAs(complete))
        compose.runOnIdle { state.seek(1500) }
        capture().recycle()
        compose.runOnIdle { state.seek(2150) }
        val sought = capture()
        assertTrue("Seeking back must restore the same transition, emphasis and subtitle geometry", first.sameAs(sought))
        save(first, "coordinated-portrait-handoff")
        save(complete, "coordinated-portrait-held-note")
        listOf(first, paused, complete, sought).forEach { it.recycle() }
    }

    @Test
    @Config(qualifiers = "w800dp-h320dp-mdpi")
    fun landscapeLargeTextKeepsTheCaptionVisibleBelowTheActualLyricInk() {
        val state = HomePresentationState(LyricClock { 0L }).apply {
            setSnapshot(HomePresentationState.Snapshot(track = "横屏示例", durationMs = 6000))
            setLyrics(LyricDocument(listOf(LyricLine(1000, "让音乐陪你到天亮", "Let the music carry us through the longest night and into the morning light", 4000,
                listOf(LyricWord(1000, 700, "让音乐"), LyricWord(1700, 700, "陪你"), LyricWord(2400, 1600, "到天亮")))), true))
            setTranslationMode(LyricsOverlayService.TRANSLATION_BILINGUAL)
            setProgress(5000, false)
            setActive(true)
        }
        show(state, PopLyricsStyle(paginate = false, camera = "off", layout = "wave", motionStrength = "strong",
            rotationDegrees = 30, stageAnchorPercent = 60, translationMaxLines = 4), 800, 320,
            HomeLyricAppearance(fontSize = 48, translationSize = 32, fontWeight = 800), fontScale = 1.5f)
        val bitmap = capture()
        val stage = compose.onNodeWithTag("home_pop_stage", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val caption = compose.onNodeWithTag("home_pop_translation", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(caption.top >= stage.top && caption.bottom <= stage.bottom + 1f)
        assertTrue(caption.left >= stage.left && caption.right <= stage.right + 1f)
        val background = bitmap.getPixel(0, 0)
        var bottom = -1
        for (y in stage.top.toInt().coerceAtLeast(0) until caption.top.toInt().coerceAtMost(bitmap.height)) {
            for (x in stage.left.toInt().coerceAtLeast(0) until stage.right.toInt().coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                val contrast = kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(background)) +
                    kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(background)) +
                    kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(background))
                if (contrast > 300) bottom = y
            }
        }
        assertTrue("Large text must still produce visible native glyphs", bottom >= 0)
        assertTrue("The caption must have a real clear gap below lyric ink", caption.top - bottom >= 4f)
        save(bitmap, "coordinated-landscape-large-font")
        bitmap.recycle()
    }
}
