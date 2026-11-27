package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.lyrics.LyricLine
import com.luoh.music.lrc.lyrics.LyricWord
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.home.HomeLyricsPrefs
import com.luoh.music.lrc.ui.home.HomeMotionPrefs
import com.luoh.music.lrc.ui.home.ScrollingCharacterMotion
import com.luoh.music.lrc.ui.home.scrollingLyricCues
import com.luoh.music.lrc.ui.home.scrollingLyricGlow
import com.luoh.music.lrc.ui.home.scrollingLyricLift
import com.luoh.music.lrc.ui.settings.SettingsCategoryContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class ScrollingLyricMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsAreShownOnlyForScrollingModeInHomeLyricsSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        HomeLyricsPrefs.saveDisplayMode(context, HomeLyricsPrefs.SCROLL)
        val category = mutableStateOf("home")
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { SettingsCategoryContent(category.value, {}, {}) }
            }
        }
        compose.onNodeWithText("逐字浮动").assertIsOff().performClick()
        compose.onNodeWithText("逐字浮动").assertIsOn()
        compose.onNodeWithText("浮动高度").assertExists()
        compose.onNodeWithText("晚风轻轻吹").assertExists()
        compose.runOnIdle { HomeLyricsPrefs.saveDisplayMode(context, HomeLyricsPrefs.POP) }
        compose.onNodeWithText("逐字浮动").assertDoesNotExist()
        compose.runOnIdle { category.value = "motion" }
        compose.onNodeWithText("逐字浮动").assertDoesNotExist()
    }

    @Test fun wordTimesArePreservedAndMultiCharacterWordsAreSubdivided() {
        val line = LyricLine(1000, "音乐 回来", words = listOf(
            LyricWord(1200, 400, "音乐"),
            LyricWord(2000, 600, "回来"),
        ))
        val cues = scrollingLyricCues(line, 2250)
        assertEquals(listOf("音", "乐", "回"), cues.map { line.text.substring(it.first, it.end) })
        assertEquals(listOf(1200L, 1400L, 2000L), cues.map { it.startMs })
        assertEquals(listOf(1400L, 1600L, 2250L), cues.map { it.endMs })
    }

    @Test fun wordTimingKeepsGraphemesTogetherAndSkipsSpaces() {
        val text = "e\u0301 👩‍🎤 亮"
        val line = LyricLine(1000, text, words = listOf(LyricWord(1000L, 1500L, text)))
        val cues = scrollingLyricCues(line, null)
        assertEquals(listOf("e\u0301", "👩‍🎤", "亮"), cues.map { line.text.substring(it.first, it.end) })
        assertEquals(listOf(1000L, 1500L, 2000L), cues.map { it.startMs })
        assertTrue(scrollingLyricCues(line, line.startMs).isEmpty())
    }

    @Test fun lineOnlyLyricsDoNotInventSingingTimes() {
        assertTrue(scrollingLyricCues(LyricLine(1000L, "晚风"), 2500L).isEmpty())
    }

    @Test fun aSungCharacterRisesOnceAndStaysRaisedUntilLineChange() {
        assertEquals(0f, scrollingLyricLift(-1L, 800), 0f)
        assertTrue(scrollingLyricLift(200L, 800) < scrollingLyricLift(400L, 800))
        assertEquals(1f, scrollingLyricLift(800L, 800), 0f)
        assertEquals(1f, scrollingLyricLift(1800L, 800), 0f)
    }

    @Test fun aHeldNoteGlowsBrighterAndTheGlowFadesAfterTheWord() {
        val cue = scrollingLyricCues(LyricLine(1000L, "风", words = listOf(LyricWord(1000L, 900L, "风"))), null).single()
        val short = cue.copy(endMs = 1160L)
        assertTrue(scrollingLyricGlow(cue, 1120L) > scrollingLyricGlow(short, 1120L))
        assertEquals(scrollingLyricGlow(cue, 1899L), scrollingLyricGlow(cue, 1900L), .01f)
        assertTrue(scrollingLyricGlow(cue, 2000L) < scrollingLyricGlow(cue, 1900L))
        assertEquals(0f, scrollingLyricGlow(cue, 2500L), 0f)
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

    @Test fun previousBounceDefaultsMigrateButCustomValuesAreKept() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean(HomeMotionPrefs.KEY_EASED_SCROLL, true)
            .putBoolean("character_motion", true)
            .putInt("character_lift_dp", 5).putInt("character_duration_ms", 350)
            .putInt("character_glow_percent", 40).commit()
        val defaults = ScrollingCharacterMotion(enabled = true, glowPercent = 40)
        assertEquals(defaults, HomeMotionPrefs.style(context).characterMotion)
        prefs.edit().putInt("character_lift_dp", 7).putInt("character_duration_ms", 600).commit()
        assertEquals(defaults.copy(liftDp = 7, durationMs = 600), HomeMotionPrefs.style(context).characterMotion)
    }
}
