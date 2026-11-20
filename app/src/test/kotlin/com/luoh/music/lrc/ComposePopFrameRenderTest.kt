package com.luoh.music.lrc

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposePopFrameRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun largeTypeDoesNotIncreaseSungPhraseCapacityAcrossOrientations() {
        val size = mutableStateOf(Triple(400f, 760f, 16))
        val source = "晚风轻拂山海星光照亮远方".mapIndexed { index, char ->
            PopLyricToken(char.toString(), 1000L + index * 500, 1500L + index * 500)
        }
        val timeline = PopLyricTimeline(source, true)
        val style = PopLyricsStyle()
        lateinit var scenes: PopScenes
        compose.setContent {
            MaterialTheme {
                val (width, height, font) = size.value
                val builder = rememberPopSceneBuilder(PopRenderCache(), HomeLyricAppearance(fontSize = font),
                    style, Color.Black, Color.Blue, width, height)
                val measured = builder.build(timeline, popLyricRenderTiming(timeline, style))
                SideEffect { scenes = measured }
            }
        }
        compose.waitForIdle()
        val expected = scenes.ranges.map { page -> scenes.timeline.tokens.subList(page.from, page.until).joinToString("") { it.text } }
        assertTrue(expected.size >= 2)
        assertTrue(expected.all { it.length <= 6 })
        for (dimensions in listOf(Triple(800f, 320f, 16), Triple(400f, 760f, 48), Triple(800f, 320f, 48))) {
            compose.runOnIdle { size.value = dimensions }
            compose.waitForIdle()
            val actual = scenes.ranges.map { page -> scenes.timeline.tokens.subList(page.from, page.until).joinToString("") { it.text } }
            assertEquals("Rotation and font changes must preserve short phrase handoffs", expected, actual)
            assertTrue("Animated phrases use at most two rows", scenes.pages.all { it.rows.size <= 2 })
        }
        val handoffs = scenes.handoffs
        for (index in 1 until scenes.ranges.size) {
            val boundary = scenes.timeline.tokens[scenes.ranges[index].from].startMs
            assertEquals(PopPhraseWindow(index, index - 1), popPhraseWindow(scenes.ranges, scenes.timeline, handoffs, boundary, true))
            assertEquals("The old chunk must disappear after its exit", PopPhraseWindow(index, null),
                popPhraseWindow(scenes.ranges, scenes.timeline, handoffs, boundary + handoffs[index].durationMs, true))
        }
    }

    @Test fun viewportFillPreservesCenteredZoomInsteadOfNormalizingItAway() {
        val effect = mutableStateOf("zoom")
        val timeline = PopLyricTimeline(listOf(PopLyricToken("晚风", 1000, 9000)), false)
        val style = PopLyricsStyle(layout = "centered", camera = "off", breathing = false,
            rotationDegrees = 0, stageFillPercent = 94)
        lateinit var buffer: PopFrameBuffer
        compose.setContent {
            MaterialTheme {
                val builder = rememberPopSceneBuilder(PopRenderCache(), HomeLyricAppearance(),
                    style, Color.Black, Color.Blue, 400f, 600f)
                val scenes = builder.build(timeline, popLyricRenderTiming(timeline, style))
                val frame = PopFrameBuffer(scenes, style, true, 400f, 600f, 1f, scenes.cameraPath,
                    PopTransitionPlan(effect.value, PopVector(0f, 0f)), 1000L, 1000, true)
                SideEffect { buffer = frame }
            }
        }
        for (mode in listOf("zoom", "converge", "disperse")) {
            compose.runOnIdle { effect.value = mode }
            compose.waitForIdle()
            fun sizeAt(now: Long): Float {
                val frame = buffer.prepare(now)
                val glyph = frame.glyphs[0][0]
                assertTrue("$mode: large glyph stays inside the stage", glyph[6] * frame.fits[0] <= 196.01f)
                return glyph[2] * frame.fits[0]
            }
            val entering = sizeAt(1200L)
            val settled = sizeAt(2000L)
            assertTrue("$mode must retain visible scale travel after viewport fitting", abs(entering / settled - 1f) > .025f)
            if (mode == "converge") assertTrue(entering > settled) else assertTrue(entering < settled)
        }
    }
}
