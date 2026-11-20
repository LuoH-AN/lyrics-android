package com.luoh.music.lrc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
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
        appearance: HomeLyricAppearance = HomeLyricAppearance(), fontScale: Float = 1f,
        scheme: (() -> PopLyricScheme)? = null, fontFamily: (() -> FontFamily)? = null) {
        compose.setContent {
            val selected = scheme?.invoke()
            val currentStyle = selected?.motion ?: style
            val currentAppearance = selected?.text ?: appearance
            val view = LocalView.current
            SideEffect { root = view }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = false),
                LocalDensity provides Density(density.density, fontScale)) {
                val typography = fontFamily?.invoke()?.let {
                    Typography(headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = it))
                } ?: MaterialTheme.typography
                MaterialTheme(typography = typography) {
                    val frame = rememberPopPlayback(state, currentStyle)
                    Box(Modifier.size(width.dp, height.dp).background(MaterialTheme.colorScheme.background)) {
                        PopLyricsStage(state, Modifier.size(width.dp, height.dp)
                            .popStageCamera(frame, currentStyle, true), false, currentAppearance, currentStyle, frame)
                    }
                }
            }
        }
    }

    private fun reviewBuiltIns(width: Int, height: Int, orientation: String) {
        val selected = mutableStateOf(PopLyricSchemes.recommended)
        val state = HomePresentationState(LyricClock { 0L }).apply {
            setSnapshot(HomePresentationState.Snapshot(track = "方案示例", durationMs = 6000))
            setLyrics(LyricDocument(listOf(LyricLine(1000, "让音乐陪你到天亮",
                "Let the music stay with you until the morning light", 4000,
                listOf(LyricWord(1000, 700, "让音乐"), LyricWord(1700, 700, "陪你"),
                    LyricWord(2400, 1600, "到天亮")))), true))
            setTranslationMode(LyricsOverlayService.TRANSLATION_BILINGUAL)
            setProgress(2900, false)
            setActive(true)
        }
        show(state, PopLyricsStyle(), width, height, scheme = { selected.value })
        for (scheme in PopLyricSchemes.builtIns) {
            compose.runOnIdle { selected.value = scheme }
            for (time in listOf(2900L, 4400L)) {
                compose.runOnIdle { state.setProgress(time, false) }
                val bitmap = capture()
                val stage = compose.onNodeWithTag("home_pop_stage", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val caption = compose.onNodeWithTag("home_pop_translation", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val label = "$orientation ${scheme.id} at $time"
                assertTrue("$label: caption stays within stage height", caption.top >= stage.top && caption.bottom <= stage.bottom + 1f)
                assertTrue("$label: caption stays within stage width", caption.left >= stage.left && caption.right <= stage.right + 1f)
                val background = bitmap.getPixel(0, 0)
                var ink = 0
                for (y in stage.top.toInt().coerceAtLeast(0) until caption.top.toInt().coerceAtMost(bitmap.height)) {
                    for (x in stage.left.toInt().coerceAtLeast(0) until stage.right.toInt().coerceAtMost(bitmap.width)) {
                        val pixel = bitmap.getPixel(x, y)
                        val contrast = kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(background)) +
                            kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(background)) +
                            kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(background))
                        if (contrast > 300) ink++
                    }
                }
                assertTrue("$label: main lyrics produce readable native ink", ink >= 100)
                save(bitmap, "scheme-$orientation-${scheme.id}-$time")
                bitmap.recycle()
            }
        }
    }

    @Test fun builtInSchemesKeepBilingualTextVisibleInPortrait() = reviewBuiltIns(400, 760, "portrait")

    @Test
    @Config(qualifiers = "w800dp-h320dp-mdpi")
    fun builtInSchemesKeepBilingualTextVisibleInLandscape() = reviewBuiltIns(800, 320, "landscape")

    private fun reviewSparseTypography(width: Int, height: Int, orientation: String) {
        val selected = mutableStateOf(PopLyricSchemes.recommended)
        val family = mutableStateOf(FontFamily.SansSerif)
        val state = HomePresentationState(LyricClock { 0L }).apply {
            setSnapshot(HomePresentationState.Snapshot(track = "字号与居中示例", durationMs = 9000))
            setActive(true)
        }
        show(state, PopLyricsStyle(), width, height, scheme = { selected.value }, fontFamily = { family.value })
        val samples = listOf("晚风轻拂", "山海", "让音乐陪你到天亮", "Stay with me", " Stay  with me ")
        for (font in listOf(16, 28, 48)) for ((sample, text) in samples.withIndex()) {
            val words = when (sample) {
                3 -> listOf("Stay ", "with ", "me")
                4 -> listOf(" Stay  ", "with ", "me ")
                else -> text.map { it.toString() }
            }
            compose.runOnIdle {
                family.value = when (font) { 16 -> FontFamily.SansSerif; 28 -> FontFamily.Serif; else -> FontFamily.Monospace }
                selected.value = PopLyricScheme("test", "字号示例", PopLyricsStyle(
                    layout = listOf("staggered", "wave", "random", "stairs")[sample % 4],
                    entrance = "random", transition = "random", camera = "orbit", pushDirection = "random",
                    paginate = false, stageAnchorPercent = 30, shortPhraseScalePercent = 110),
                    HomeLyricAppearance(fontSize = font, fontWeight = if (font == 16) 300 else 800))
                state.setLyrics(LyricDocument(listOf(LyricLine(1000, text, "Evening breeze", 6500,
                    words.mapIndexed { index, word -> LyricWord(1000 + index * 500L, 500, word) })), true))
                state.setTranslationMode(LyricsOverlayService.TRANSLATION_BILINGUAL)
            }
            for (time in listOf(1350L, 6800L)) {
                compose.runOnIdle { state.setProgress(time, false) }
                val bitmap = capture()
                val stage = compose.onNodeWithTag("home_pop_stage", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val caption = compose.onNodeWithTag("home_pop_translation", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val background = bitmap.getPixel(0, 0)
                var left = Int.MAX_VALUE
                var right = -1
                var top = Int.MAX_VALUE
                var bottom = -1
                var band = 0
                var longestBand = 0
                for (y in stage.top.toInt().coerceAtLeast(0) until caption.top.toInt().coerceAtMost(bitmap.height)) {
                    var painted = false
                    for (x in stage.left.toInt().coerceAtLeast(0) until stage.right.toInt().coerceAtMost(bitmap.width)) {
                        val pixel = bitmap.getPixel(x, y)
                        val contrast = kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(background)) +
                            kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(background)) +
                            kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(background))
                        if (contrast > 300) {
                            left = minOf(left, x); right = maxOf(right, x)
                            top = minOf(top, y); bottom = maxOf(bottom, y); painted = true
                        }
                    }
                    band = if (painted) band + 1 else 0
                    longestBand = maxOf(longestBand, band)
                }
                val label = "$orientation font=$font sample=$sample time=$time"
                save(bitmap, "centered-fill-$orientation-$font-$sample-$time")
                assertTrue("$label: visible lyric ink", right >= left)
                assertEquals("$label: horizontal ink center", stage.center.x, (left + right) / 2f, width * .04f)
                assertEquals("$label: vertical ink center", stage.center.y, (top + bottom) / 2f, height * .06f)
                assertTrue("$label: caption below lyric ink", caption.top - bottom >= 4f)
                if (time == 6800L && sample == 0) {
                    assertTrue("$label: sparse words occupy the stage width (ink=${right-left})", right - left >= width * .60f)
                    assertTrue("$label: small font preferences still yield large native glyphs (band=$longestBand)",
                        longestBand >= if (orientation == "portrait") 70 else 35)
                }
                bitmap.recycle()
            }
        }
    }

    @Test fun shortPhrasesUseLargeTypeAndStayCenteredAtEveryFontSizeInPortrait() = reviewSparseTypography(400, 760, "portrait")

    @Test
    @Config(qualifiers = "w800dp-h320dp-mdpi")
    fun shortPhrasesUseLargeTypeAndStayCenteredAtEveryFontSizeInLandscape() = reviewSparseTypography(800, 320, "landscape")

    private fun reviewSentenceHandoffs(width: Int, height: Int, orientation: String) {
        val selected = mutableStateOf(PopLyricSchemes.recommended)
        val state = HomePresentationState(LyricClock { 0L }).apply {
            setSnapshot(HomePresentationState.Snapshot(track = "居中转场示例", durationMs = 6000))
            setLyrics(LyricDocument(listOf(
                LyricLine(1000, "晚风", "Evening breeze", 2000,
                    listOf(LyricWord(1000, 1000, "晚风"))),
                LyricLine(2000, "轻拂山海", "Across the mountains and sea", 5000,
                    "轻拂山海".map { LyricWord(2000, 3000, it.toString()) })), true))
            setTranslationMode(LyricsOverlayService.TRANSLATION_BILINGUAL)
            setProgress(2200, false)
            setActive(true)
        }
        show(state, PopLyricsStyle(), width, height, scheme = { selected.value })
        for ((index, transition) in listOf("fade", "slide", "zoom", "converge", "disperse", "random").withIndex()) {
            compose.runOnIdle {
                selected.value = PopLyricScheme("test", "转场示例", PopLyricsStyle(transition = transition,
                    layout = "wave", camera = "orbit", entrance = "converge", pushDirection = "random",
                    paginate = false, activeScalePercent = 180, rotationDegrees = 20,
                    motionStrength = "strong", stageFillPercent = 100), HomeLyricAppearance(fontSize = 48))
            }
            for (time in listOf(2160L, 2350L, 2800L)) {
                compose.runOnIdle { state.setProgress(time, false) }
                val bitmap = capture()
                val stage = compose.onNodeWithTag("home_pop_stage", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val caption = compose.onNodeWithTag("home_pop_translation", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val background = bitmap.getPixel(0, 0)
                var left = Int.MAX_VALUE
                var right = -1
                var top = Int.MAX_VALUE
                var bottom = -1
                for (y in stage.top.toInt().coerceAtLeast(0) until caption.top.toInt().coerceAtMost(bitmap.height)) {
                    for (x in stage.left.toInt().coerceAtLeast(0) until stage.right.toInt().coerceAtMost(bitmap.width)) {
                        val pixel = bitmap.getPixel(x, y)
                        val contrast = kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(background)) +
                            kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(background)) +
                            kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(background))
                        if (contrast > 300) { left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y) }
                    }
                }
                val label = "$orientation $transition at $time"
                assertTrue("$label: lyric remains visible", right >= left)
                assertEquals("$label: centered through sentence and phrase motion", stage.center.x, (left + right) / 2f, width * .05f)
                assertTrue("$label: no glyph reaches a clipped stage edge", left > stage.left + 2f && right < stage.right - 2f)
                assertTrue("$label: subtitle has clear space below animated type", caption.top - bottom >= 4f)
                save(bitmap, "centered-handoff-$orientation-$index-$time")
                bitmap.recycle()
            }
        }
    }

    @Test fun everySentenceTransitionKeepsLargeTypeCenteredAndInsidePortrait() = reviewSentenceHandoffs(400, 760, "portrait")

    @Test
    @Config(qualifiers = "w800dp-h320dp-mdpi")
    fun everySentenceTransitionKeepsLargeTypeCenteredAndInsideLandscape() = reviewSentenceHandoffs(800, 320, "landscape")

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
        var band = 0
        var longestBand = 0
        for (y in stage.top.toInt().coerceAtLeast(0) until caption.top.toInt().coerceAtMost(bitmap.height)) {
            var paintedRow = false
            for (x in stage.left.toInt().coerceAtLeast(0) until stage.right.toInt().coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                val contrast = kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(background)) +
                    kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(background)) +
                    kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(background))
                if (contrast > 300) { bottom = y; paintedRow = true }
            }
            band = if (paintedRow) band + 1 else 0
            longestBand = maxOf(longestBand, band)
        }
        assertTrue("Large text must still produce visible native glyphs", bottom >= 0)
        assertTrue("The requested large type must stay readable; shrink motion before shrinking text (band=$longestBand)", longestBand >= 28)
        assertTrue("The caption must have a real clear gap below lyric ink", caption.top - bottom >= 4f)
        save(bitmap, "coordinated-landscape-large-font")
        bitmap.recycle()
    }
}
