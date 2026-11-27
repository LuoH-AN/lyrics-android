package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.lyrics.LyricLine
import com.luoh.music.lrc.lyrics.LyricWord
import com.luoh.music.lrc.ui.home.HomeMotionPrefs
import com.luoh.music.lrc.ui.home.ScrollingCharacterMotion
import com.luoh.music.lrc.ui.home.scrollingLyricCues
import com.luoh.music.lrc.ui.home.scrollingLyricPulse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScrollingLyricMotionTest {
    @Test fun wordTimesArePreservedAndMultiCharacterWordsAreSubdivided() {
        val line = LyricLine(1000, "音乐 回来", words = listOf(
            LyricWord(1200, 400, "音乐"),
            LyricWord(2000, 600, "回来"),
        ))
        val cues = scrollingLyricCues(line, 2250)
        assertEquals(listOf("音", "乐", "回"), cues.map { line.text.substring(it.first, it.end) })
        assertEquals(listOf(1200L, 1400L, 2000L), cues.map { it.startMs })
    }

    @Test fun lineTimingKeepsGraphemesTogetherAndSkipsSpaces() {
        val line = LyricLine(1000, "e\u0301 👩‍🎤 亮")
        val cues = scrollingLyricCues(line, 2500)
        assertEquals(listOf("e\u0301", "👩‍🎤", "亮"), cues.map { line.text.substring(it.first, it.end) })
        assertEquals(listOf(1000L, 1500L, 2000L), cues.map { it.startMs })
        assertTrue(scrollingLyricCues(line, line.startMs).isEmpty())
    }

    @Test fun thePulseRisesAndReturnsWithinItsConfiguredDuration() {
        assertEquals(0f, scrollingLyricPulse(-1L, 400L), 0f)
        assertEquals(1f, scrollingLyricPulse(140L, 400L), .001f)
        assertTrue(scrollingLyricPulse(300L, 400L) in 0f..0.5f)
        assertEquals(0f, scrollingLyricPulse(400L, 400L), 0f)
        assertTrue(scrollingLyricPulse(400L, 600L) > 0f)
    }

    @Test fun existingSettingsDefaultOffAndCustomValuesSurviveSwitchingOff() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE).edit()
            .clear().putBoolean(HomeMotionPrefs.KEY_EASED_SCROLL, false).commit()
        val existing = HomeMotionPrefs.style(context)
        assertFalse(existing.characterMotion.enabled)
        val custom = ScrollingCharacterMotion(enabled = true, liftDp = 9, glowPercent = 40, durationMs = 600)
        HomeMotionPrefs.save(context, existing.copy(characterMotion = custom))
        val saved = HomeMotionPrefs.style(context)
        assertEquals(custom, saved.characterMotion)
        assertFalse(saved.easedScroll)
        HomeMotionPrefs.save(context, saved.copy(characterMotion = custom.copy(enabled = false)))
        assertEquals(custom.copy(enabled = false), HomeMotionPrefs.style(context).characterMotion)
    }
}
