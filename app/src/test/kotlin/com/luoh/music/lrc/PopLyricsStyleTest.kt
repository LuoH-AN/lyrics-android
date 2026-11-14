package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PopLyricsStyleTest {
    @Test fun customSettingsPersistNormalizeAndRemainIndependentOfScrolling() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val style = PopLyricsStyle.preset("dynamic").copy(preset = "custom", entranceMs = 100000,
            cameraPercent = -10, rotationDegrees = 90)
        PopLyricsPrefs.save(context, style)
        val saved = PopLyricsPrefs.style(context)
        assertEquals("character", saved.reveal)
        assertEquals("custom", saved.preset)
        assertEquals(1000, saved.entranceMs)
        assertEquals(0, saved.cameraPercent)
        assertEquals(30, saved.rotationDegrees)
        assertEquals(HomeLyricsPrefs.SCROLL, HomeLyricsPrefs.displayMode(context))
        PopLyricsPrefs.save(context, PopLyricsStyle.preset("calm"))
        assertEquals(PopLyricsStyle.preset("calm"), PopLyricsPrefs.style(context))
    }

    @Test fun characterChoreographyKeepsRealWordTimestampsAndComplexTextIntact() {
        val line = LyricLine(1000, "音乐é👩‍👩‍👧‍👦", endMs = 4000, words = listOf(
            LyricWord(1000, 1000, "音乐"), LyricWord(2000, 1000, "é"), LyricWord(3000, 1000, "👩‍👩‍👧‍👦")))
        val timeline = PopLyricTimeline.prepare(line, reveal = "character")
        assertTrue(timeline.wordTimed)
        assertEquals(listOf("音", "乐", "é", "👩‍👩‍👧‍👦"), timeline.tokens.map { it.text })
        assertEquals(listOf(1000L, 1000L, 2000L, 3000L), timeline.tokens.map { it.startMs })
        assertEquals(listOf(2000L, 2000L, 3000L, 4000L), timeline.tokens.map { it.endMs })
        assertEquals(line.text, timeline.tokens.joinToString("") { it.text })
        assertTrue(timeline.tokens[1].visualDelayMs > timeline.tokens[0].visualDelayMs)
        val long = LyricLine(1000, "字".repeat(100), words = listOf(LyricWord(1000, 1000, "字".repeat(100))))
        assertTrue(PopLyricTimeline.prepare(long, reveal = "character").tokens.size <= PopLyricTimeline.MAX_TOKENS)
    }

    @Test fun sharedCameraMovesWithWordsHasBoundedPosesAndHonorsStaticModes() {
        val line = LyricLine(1000, "音乐陪你", words = listOf(LyricWord(1000, 1000, "音乐"), LyricWord(2000, 1000, "陪你")))
        val timeline = PopLyricTimeline.prepare(line)
        val style = PopLyricsStyle.preset("dynamic")
        assertNotEquals(PopStageMotion.sample(1200, timeline, style), PopStageMotion.sample(2500, timeline, style))
        for (time in 0L..10000L step 25) {
            val pose = PopStageMotion.sample(time, timeline, style.copy(cameraPercent = 200))
            assertTrue(pose.x in -.07f.. .07f && pose.y in -.07f.. .07f)
            assertTrue(pose.zoom in .98f..1.1f && pose.rotation in -1.5f..1.5f)
        }
        assertEquals(PopStagePose(), PopStageMotion.sample(1200, timeline, style, allowMotion = false))
        assertEquals(PopStagePose(), PopStageMotion.sample(1200, timeline, style.copy(camera = "off")))
        assertEquals(PopStagePose(), PopStageMotion.sample(1200, null, style))
        assertEquals(PopStageMotion.sample(2500, timeline, style), PopStageMotion.sample(2500, timeline, style))
    }
}
