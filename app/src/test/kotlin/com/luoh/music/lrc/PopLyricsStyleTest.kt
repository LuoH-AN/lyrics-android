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
        val style = PopLyricsStyle(reveal = "character").copy( entranceMs = 100000,
            cameraPercent = -10, rotationDegrees = 90, groupGapDp = 999, innerGapDp = -5,
            rowGapPercent = 999, lineWidthPercent = 999, staggerPercent = -5, rowMotionPercent = 999,
            cameraSpeedPercent = 999, textHoldMs = 9999, passedOpacityPercent = -5, semanticGrouping = false,
            dynamicSpacing = false, adaptiveTiming = false, paginate = false, pageLength = 999, sustainShakePercent = 999)
        PopLyricsPrefs.save(context, style)
        val saved = PopLyricsPrefs.style(context)
        assertEquals("character", saved.reveal)
        assertEquals("custom", saved.preset)
        assertEquals(1000, saved.entranceMs)
        assertEquals(0, saved.cameraPercent)
        assertEquals(30, saved.rotationDegrees)
        assertEquals(style.normalized(), saved)
        assertEquals(24, saved.groupGapDp)
        assertEquals(0, saved.innerGapDp)
        assertEquals(180, saved.cameraSpeedPercent)
        assertEquals(800, saved.textHoldMs)
        assertFalse(saved.paginate)
        assertEquals(24, saved.pageLength)
        assertEquals(200, saved.sustainShakePercent)
        assertEquals(HomeLyricsPrefs.POP, HomeLyricsPrefs.displayMode(context))
        PopLyricsPrefs.save(context, PopLyricsStyle())
        assertEquals(PopLyricsStyle(), PopLyricsPrefs.style(context))
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
        val path = PopStageMotion.path(timeline, List(timeline.tokens.size) { PopCameraPoint(it / 10f, 0f) }, PopLyricsStyle())
        assertEquals("A word's letter cascade shares one focus, followed by one return to center", 4, path.waypoints.size)
        val long = LyricLine(1000, "字".repeat(100), words = listOf(LyricWord(1000, 1000, "字".repeat(100))))
        assertTrue(PopLyricTimeline.prepare(long, reveal = "character").tokens.size <= PopLyricTimeline.MAX_TOKENS)
    }

    @Test fun cameraFollowsMeasuredWordCentersContinuouslyWithoutAlternatingShake() {
        val line = LyricLine(1000, "音乐陪你到天亮", words = listOf(
            LyricWord(1000, 1000, "音乐"), LyricWord(2000, 1000, "陪你"), LyricWord(3000, 1000, "到天亮")))
        val timeline = PopLyricTimeline.prepare(line)
        val style = PopLyricsStyle()
        val path = PopStageMotion.path(timeline,
            listOf(PopCameraPoint(-.3f, -.15f), PopCameraPoint(0f, 0f), PopCameraPoint(.3f, .15f)), style)
        val left = PopStageMotion.sample(1600, path, style)
        val right = PopStageMotion.sample(3800, path, style)
        assertTrue(left.x > 0f && left.y > 0f)
        assertTrue(right.x < 0f && right.y < 0f)
        assertEquals(0f, right.rotation, 0f)
        assertEquals(1f, right.zoom, 0f)
        assertEquals(PopStagePose(), PopStageMotion.sample(5000, path, style))
        assertEquals(PopStagePose(), PopStageMotion.sample(6500, path, style))
        for (point in path.waypoints.drop(1)) {
            val before = PopStageMotion.sample(point.startMs - 1, path, style)
            val after = PopStageMotion.sample(point.startMs, path, style)
            assertTrue(kotlin.math.abs(before.x - after.x) < .002f)
            assertTrue(kotlin.math.abs(before.y - after.y) < .002f)
        }
        assertEquals(0L, PopStageMotion.nextChangeDelayMs(1100, path, style))
        assertEquals(1000L, PopStageMotion.nextChangeDelayMs(5000, path, style))
        assertEquals(PopStagePose(), PopStageMotion.sample(1200, path, style, allowMotion = false))
        assertEquals(PopStagePose(), PopStageMotion.sample(1200, path, style.copy(camera = "off")))
        assertEquals(PopStagePose(), PopStageMotion.sample(1200, null, style))
        val bounded = PopStageMotion.path(timeline,
            listOf(PopCameraPoint(-.3f, -.15f), PopCameraPoint(0f, 0f), PopCameraPoint(.3f, .15f)), style,
            PopCameraBounds(-.05f, .05f, -.04f, .04f))
        for (time in 850L..5000L step 20L) {
            val pose = PopStageMotion.sample(time, bounded, style)
            assertTrue(kotlin.math.abs(pose.x) <= .05f && kotlin.math.abs(pose.y) <= .04f)
        }
    }

    @Test fun oldNamedDefaultsMigrateToDirectCenteredParametersButCustomValuesSurvive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().putString("pop_preset", "flow").putString("pop_layout", "staggered")
            .putInt("pop_rotation", 20).putBoolean("pop_breathing", true).commit()
        assertEquals(PopLyricsStyle(), PopLyricsPrefs.style(context))
        prefs.edit().clear().putString("pop_preset", "custom").putString("pop_layout", "stairs")
            .putInt("pop_rotation", 12).commit()
        assertEquals("stairs", PopLyricsPrefs.style(context).layout)
        assertEquals(12, PopLyricsPrefs.style(context).rotationDegrees)
    }

    @Test fun cameraSpeedChangesDurationAndKeepsTheTrajectoryContinuousAndBounded() {
        val timeline = PopLyricTimeline(listOf(PopLyricToken("音", 1000, 2000), PopLyricToken("乐", 2000, 3000)), true)
        val centers = listOf(PopCameraPoint(-.2f, 0f), PopCameraPoint(.2f, 0f))
        val slow = PopStageMotion.path(timeline, centers, PopLyricsStyle(cameraSpeedPercent = 50))
        val fast = PopStageMotion.path(timeline, centers, PopLyricsStyle(cameraSpeedPercent = 180))
        assertTrue(slow.waypoints.first().durationMs > fast.waypoints.first().durationMs)
        assertEquals(PopStagePose(), PopStageMotion.sample(6000, slow, PopLyricsStyle(cameraSpeedPercent = 50)))
    }

    @Test fun denseWordsAndReturnToCenterPreserveCameraVelocity() {
        val timeline = PopLyricTimeline.prepare(LyricLine(1000, "音乐陪伴", words = listOf(
            LyricWord(1000, 150, "音"), LyricWord(1150, 150, "乐"),
            LyricWord(1300, 150, "陪"), LyricWord(1450, 150, "伴"))))
        val style = PopLyricsStyle()
        val path = PopStageMotion.path(timeline, listOf(PopCameraPoint(-.2f, -.1f),
            PopCameraPoint(-.1f, -.05f), PopCameraPoint(.1f, .05f), PopCameraPoint(.2f, .1f)), style)
        for (point in path.waypoints.drop(1)) {
            val before = PopStageMotion.sample(point.startMs - 1, path, style)
            val at = PopStageMotion.sample(point.startMs, path, style)
            val after = PopStageMotion.sample(point.startMs + 1, path, style)
            assertTrue("Horizontal velocity must continue across a word or return boundary",
                kotlin.math.abs((at.x - before.x) - (after.x - at.x)) < .00001f)
            assertTrue("Vertical velocity must continue across a word or return boundary",
                kotlin.math.abs((at.y - before.y) - (after.y - at.y)) < .00001f)
        }
        assertEquals(PopStagePose(), PopStageMotion.sample(3500, path, style))
        assertEquals(1000L, PopStageMotion.nextChangeDelayMs(3500, path, style))
    }
}
