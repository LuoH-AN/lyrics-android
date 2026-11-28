package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.lyrics.LyricLine
import com.luoh.music.lrc.lyrics.LyricWord
import com.luoh.music.lrc.ui.home.HomeLyricAppearance
import com.luoh.music.lrc.ui.home.HomeLyricsPrefs
import com.luoh.music.lrc.ui.home.pop.PopCameraBounds
import com.luoh.music.lrc.ui.home.pop.PopCameraPoint
import com.luoh.music.lrc.ui.home.pop.PopLyricSchemes
import com.luoh.music.lrc.ui.home.pop.PopLyricTimeline
import com.luoh.music.lrc.ui.home.pop.PopLyricToken
import com.luoh.music.lrc.ui.home.pop.PopLyricsPrefs
import com.luoh.music.lrc.ui.home.pop.PopLyricsStyle
import com.luoh.music.lrc.ui.home.pop.PopStageMotion
import com.luoh.music.lrc.ui.home.pop.PopStagePose
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PopLyricsStyleTest {
    @Test fun sizingModesPersistInPreferencesAndSchemesWithCompatibleOldDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val classic = PopLyricsStyle(sizingMode = "classic", classicScalePercent = 125,
            entranceMs = 700, stageFillPercent = 88)
        PopLyricsPrefs.save(context, classic)
        assertEquals(classic, PopLyricsPrefs.style(context))
        assertTrue(PopLyricSchemes.save(context, "经典方案"))
        assertEquals(classic, PopLyricSchemes.saved(context).single().motion)
        PopLyricsPrefs.save(context, classic.copy(sizingMode = "focus"))
        assertEquals("classic", PopLyricSchemes.saved(context).single().motion.sizingMode)
        PopLyricSchemes.apply(context, PopLyricSchemes.saved(context).single())
        assertEquals(classic, PopLyricsPrefs.style(context))
        val snapshots = JSONArray(prefs.getString("pop_saved_schemes", "[]"))
        snapshots.getJSONObject(0).getJSONObject("motion").remove("sizingMode")
        snapshots.getJSONObject(0).getJSONObject("motion").remove("classicScalePercent")
        prefs.edit().remove("pop_sizing_mode").remove("pop_classic_scale")
            .putString("pop_saved_schemes", snapshots.toString()).commit()
        val compatible = classic.copy(sizingMode = "focus", classicScalePercent = 140)
        assertEquals(compatible, PopLyricsPrefs.style(context))
        assertEquals(compatible, PopLyricSchemes.saved(context).single().motion)
        assertEquals("focus", classic.copy(sizingMode = "unknown").normalized().sizingMode)
        assertEquals(80, classic.copy(classicScalePercent = -1).normalized().classicScalePercent)
        assertEquals(200, classic.copy(classicScalePercent = 999).normalized().classicScalePercent)
    }
    @Test fun previousPhraseDefaultIsShortenedOnceWithoutResettingOtherSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().putString("pop_preset", "custom").putInt("pop_defaults_revision", 3)
            .putInt("pop_page_length", 10).putInt("pop_entrance_ms", 700)
            .putString("pop_layout", "wave").putInt("pop_stage_fill", 96).commit()
        val migrated = PopLyricsPrefs.style(context)
        assertEquals(6, migrated.pageLength)
        assertEquals(700, migrated.entranceMs)
        assertEquals("wave", migrated.layout)
        assertEquals(96, migrated.stageFillPercent)
        PopLyricsPrefs.save(context, migrated.copy(pageLength = 10))
        assertEquals(10, PopLyricsPrefs.style(context).pageLength)
        prefs.edit().remove("pop_phrase_revision").putInt("pop_page_length", 14).commit()
        assertEquals(14, PopLyricsPrefs.style(context).pageLength)
    }
    @Test fun recommendedSchemeMatchesFreshInstallAndRestoresMotionAndTypographyTogether() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val recommended = PopLyricSchemes.recommended(context)
        assertEquals("flow", recommended.id)
        assertEquals(recommended.motion, PopLyricsPrefs.style(context))
        assertEquals(recommended.text, HomeLyricsPrefs.appearance(context))
        val custom = recommended.motion.copy(entranceMs = 750, layout = "stairs", stagePaddingDp = 32)
        val text = HomeLyricAppearance(fontSize = 40, translationSize = 24, fontWeight = 700)
        PopLyricsPrefs.save(context, custom)
        HomeLyricsPrefs.saveAppearance(context, text)
        assertTrue(PopLyricSchemes.save(context, "自定义方案"))
        PopLyricSchemes.apply(context, recommended)
        assertEquals(recommended.motion, PopLyricsPrefs.style(context))
        assertEquals(recommended.text, HomeLyricsPrefs.appearance(context))
        val saved = PopLyricSchemes.saved(context).single()
        assertEquals(custom, saved.motion)
        assertEquals(text, saved.text)
    }

    @Test fun everyBuiltInSchemeCanBeAppliedAndSavedWithItsOwnTypography() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val schemes = PopLyricSchemes.builtIns(context)
        assertEquals(9, schemes.size)
        assertEquals(schemes.size, schemes.map { it.id }.distinct().size)
        assertEquals(schemes.size, schemes.map { it.motion }.distinct().size)
        assertTrue(schemes.map { it.id }.containsAll(listOf("calm", "flow", "dynamic")))
        for (scheme in schemes) {
            PopLyricSchemes.apply(context, scheme)
            assertEquals(scheme.name, scheme.motion, PopLyricsPrefs.style(context))
            assertEquals(scheme.name, scheme.text, HomeLyricsPrefs.appearance(context))
            assertTrue(PopLyricSchemes.save(context, scheme.name))
            val saved = PopLyricSchemes.saved(context).first { it.name == scheme.name }
            assertEquals(scheme.motion, saved.motion)
            assertEquals(scheme.text, saved.text)
        }
    }

    @Test fun additionalBuiltInsDoNotConsumeTheUserSchemeLimit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        for (index in 1..PopLyricSchemes.LIMIT) assertTrue(PopLyricSchemes.save(context, "方案 $index"))
        assertFalse(PopLyricSchemes.save(context, "额外方案"))
        val first = PopLyricSchemes.saved(context).first()
        PopLyricSchemes.apply(context, PopLyricSchemes.builtIns(context).last())
        assertTrue(PopLyricSchemes.save(context, first.name))
        val updated = PopLyricSchemes.saved(context).first()
        assertEquals(first.id, updated.id)
        assertEquals(PopLyricSchemes.builtIns(context).last().motion, updated.motion)
        assertEquals(PopLyricSchemes.LIMIT, PopLyricSchemes.saved(context).size)
    }

    @Test fun expandedPresetCatalogPreservesExistingRevisionThreeSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val style = PopLyricsStyle(preset = "flow", entranceMs = 700, layout = "wave",
            cameraPercent = 25, rotationDegrees = 2, stagePaddingDp = 32)
        val text = HomeLyricAppearance(fontSize = 42, translationSize = 22, fontWeight = 600)
        PopLyricsPrefs.save(context, style)
        HomeLyricsPrefs.saveAppearance(context, text)
        assertEquals(3, prefs.getInt("pop_defaults_revision", 0))
        assertEquals(style, PopLyricsPrefs.style(context))
        assertEquals(text, HomeLyricsPrefs.appearance(context))
    }

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

    @Test fun savedSchemesKeepMotionAndTypographyWithoutChangingTheSnapshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val style = PopLyricsStyle(layout = "random", entrance = "random", transition = "random",
            pushDirection = "horizontal", camera = "orbit", entranceMs = 650)
        val text = HomeLyricAppearance(fontSize = 36, translationSize = 20, fontWeight = 700, lineSpacing = 16)
        PopLyricsPrefs.save(context, style)
        HomeLyricsPrefs.saveAppearance(context, text)
        assertTrue(PopLyricSchemes.save(context, "我的动效"))
        val saved = PopLyricSchemes.saved(context).single()
        assertEquals(style, saved.motion)
        assertEquals(text, saved.text)
        PopLyricsPrefs.save(context, style.copy(entranceMs = 200))
        assertEquals(style, PopLyricSchemes.saved(context).single().motion)
        PopLyricSchemes.apply(context, saved)
        assertEquals(style, PopLyricsPrefs.style(context))
        assertEquals(text, HomeLyricsPrefs.appearance(context))
    }

    @Test fun allNewMotionChoicesSurviveSavingAndOldCustomPreferencesArePreserved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().putString("pop_preset", "custom").putInt("pop_entrance_ms", 750)
            .putString("pop_layout", "stairs").commit()
        val old = PopLyricsPrefs.style(context)
        assertEquals(750, old.entranceMs)
        assertEquals("stairs", old.layout)
        assertEquals("left", old.pushDirection)
        for (direction in listOf("left", "right", "up", "down", "horizontal", "vertical", "random")) {
            for (effect in listOf("converge", "disperse", "random")) {
                val configured = old.copy(entrance = effect, transition = effect,
                    pushDirection = direction)
                PopLyricsPrefs.save(context, configured)
                assertEquals(configured, PopLyricsPrefs.style(context))
            }
        }
    }

    @Test fun legacyPresentationIsRemovedWithoutResettingCustomMotionOrSavedSchemes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val style = PopLyricsStyle(layout = "wave", entrance = "converge", transition = "random",
            pushDirection = "vertical", camera = "orbit", entranceMs = 750, paginate = false)
        val text = HomeLyricAppearance(fontSize = 36, fontWeight = 700)
        PopLyricsPrefs.save(context, style)
        HomeLyricsPrefs.saveAppearance(context, text)
        assertTrue(PopLyricSchemes.save(context, "旧方案"))
        val snapshots = JSONArray(prefs.getString("pop_saved_schemes", "[]"))
        snapshots.getJSONObject(0).getJSONObject("motion").put("presentation", "stack")
            .put("stackGapDp", 28).put("stackDirection", "down").put("stackTranslations", false)
        prefs.edit().putString("pop_presentation", "stack").putInt("pop_stack_gap", 28)
            .putString("pop_stack_direction", "down").putBoolean("pop_stack_translations", false)
            .putInt("pop_defaults_revision", 0).putString("pop_saved_schemes", snapshots.toString()).commit()

        assertEquals(style, PopLyricsPrefs.style(context))
        assertFalse(prefs.contains("pop_presentation"))
        assertFalse(prefs.all.keys.any { it.startsWith("pop_stack_") })
        assertEquals(text, HomeLyricsPrefs.appearance(context))
        val saved = PopLyricSchemes.saved(context).single()
        assertEquals("旧方案", saved.name)
        assertEquals(style, saved.motion)
        assertEquals(text, saved.text)
        PopLyricsPrefs.save(context, PopLyricsStyle())
        PopLyricSchemes.apply(context, saved)
        assertEquals(style, PopLyricsPrefs.style(context))
        assertEquals(text, HomeLyricsPrefs.appearance(context))
        assertTrue(PopLyricSchemes.save(context, "旧方案"))
        val motion = JSONArray(prefs.getString("pop_saved_schemes", "[]"))
            .getJSONObject(0).getJSONObject("motion")
        assertFalse(motion.has("presentation"))
        assertFalse(motion.keys().asSequence().any { it.startsWith("stack") })
        assertEquals(saved.id, PopLyricSchemes.saved(context).single().id)
    }

    @Test fun coordinatedMotionAndCompositionRoundTripAndOldSnapshotsGetCompatibleDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().putString("pop_preset", "custom").putInt("pop_entrance_ms", 750).commit()
        val old = PopLyricsPrefs.style(context)
        assertEquals(750, old.entranceMs)
        assertTrue(old.flowTransitions && old.adaptiveEmphasis && old.coordinateMotion)
        assertEquals("balanced", old.motionStrength)
        val style = old.copy(motionStrength = "strong", flowTransitions = false, adaptiveEmphasis = false,
            coordinateMotion = false, stageAnchorPercent = 60, stagePaddingDp = 40, shortPhraseScalePercent = 110,
            translationGapDp = 28, translationMaxLines = 4, stageFillPercent = 100)
        PopLyricsPrefs.save(context, style)
        assertEquals(style, PopLyricsPrefs.style(context))
        assertTrue(PopLyricSchemes.save(context, "协调与排版"))
        assertEquals(style, PopLyricSchemes.saved(context).single().motion)
        val snapshots = JSONArray(prefs.getString("pop_saved_schemes", "[]"))
        val motion = snapshots.getJSONObject(0).getJSONObject("motion")
        for (key in listOf("motionStrength", "flowTransitions", "adaptiveEmphasis", "coordinateMotion",
            "stageAnchorPercent", "stagePaddingDp", "shortPhraseScalePercent", "translationGapDp", "translationMaxLines", "stageFillPercent")) motion.remove(key)
        prefs.edit().putString("pop_saved_schemes", snapshots.toString()).commit()
        val loaded = PopLyricSchemes.saved(context).single()
        assertEquals(old, loaded.motion)
        PopLyricSchemes.apply(context, loaded)
        assertEquals(old, PopLyricsPrefs.style(context))
        val normalized = old.copy(stageAnchorPercent = -1, stagePaddingDp = 999, shortPhraseScalePercent = 999,
            translationGapDp = -1, translationMaxLines = 999, motionStrength = "invalid", stageFillPercent = 999).normalized()
        assertEquals(30, normalized.stageAnchorPercent)
        assertEquals(40, normalized.stagePaddingDp)
        assertEquals(180, normalized.shortPhraseScalePercent)
        assertEquals(4, normalized.translationGapDp)
        assertEquals(4, normalized.translationMaxLines)
        assertEquals("balanced", normalized.motionStrength)
        assertEquals(100, normalized.stageFillPercent)
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
        val style = PopLyricsStyle(breathing = false)
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
        val style = PopLyricsStyle(breathing = false)
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
