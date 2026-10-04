package com.luoh.music.lrc

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.slider.Slider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlaySettingsTest {
    private fun preferences() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

    private fun texts(view: View): List<TextView> = if (view is ViewGroup) {
        (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
    } else listOfNotNull(view as? TextView)

    private fun layout(view: View, heightDp: Int = 640) {
        val density = view.resources.displayMetrics.density
        val width = (320 * density).toInt()
        val height = (heightDp * density).toInt()
        repeat(2) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
        view.viewTreeObserver.dispatchOnPreDraw()
    }

    @Test fun percentagesPreserveEveryStoredFontWeight() {
        assertEquals(100, OverlayAppearance.weightToPercent(400))
        for (weight in 300..900 step 100) {
            val percent = OverlayAppearance.weightToPercent(weight)
            assertEquals(weight / 4, percent)
            assertEquals(weight, OverlayAppearance.percentToWeight(percent))
        }
        assertEquals(300, OverlayAppearance.percentToWeight(Int.MIN_VALUE))
        assertEquals(900, OverlayAppearance.percentToWeight(Int.MAX_VALUE))
    }

    @Test fun fontWeightIsNormalizedAndRestoredInTheActualPreview() {
        assertEquals(300, OverlayAppearance.normalizeWeight(-1))
        assertEquals(700, OverlayAppearance.normalizeWeight(651))
        assertEquals(900, OverlayAppearance.normalizeWeight(Int.MAX_VALUE))
        preferences().edit().clear().putInt(LyricsOverlayService.PREF_FONT_WEIGHT, 400).commit()
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val activity = controller.get()
            val slider = activity.findViewById<Slider>(R.id.seek_font_weight)
            assertEquals(75f, slider.valueFrom, 0f)
            assertEquals(225f, slider.valueTo, 0f)
            assertEquals(25f, slider.stepSize, 0f)
            assertEquals(100f, slider.value, 0f)
            assertEquals("100%", activity.findViewById<TextView>(R.id.font_weight_value).text.toString())
            slider.requestFocus()
            slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            assertEquals(500, preferences().getInt(LyricsOverlayService.PREF_FONT_WEIGHT, 0))
            val preview = activity.findViewById<CompactLyricsView>(R.id.overlay_preview)
            assertTrue(texts(preview).isNotEmpty())
            texts(preview).forEach { assertEquals(500, it.typeface.weight) }
        } finally { controller.destroy() }
        val restored = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            assertEquals(125f, restored.get().findViewById<Slider>(R.id.seek_font_weight).value, 0f)
            assertEquals("125%", restored.get().findViewById<TextView>(R.id.font_weight_value).text.toString())
        } finally { restored.destroy() }
    }

    @Test fun synchronizationKeepsItsGuardsWithoutTheHelperLine() {
        preferences().edit().clear().commit()
        val empty = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val activity = empty.get()
            for (id in listOf(R.id.offset_earlier, R.id.offset_later, R.id.offset_reset)) {
                assertFalse(activity.findViewById<View>(id).isEnabled)
            }
            val labels = texts(activity.findViewById(android.R.id.content)).map { it.text.toString() }
            assertTrue(labels.containsAll(listOf("上文", "下文")))
            assertFalse(labels.contains("播放一首歌后，可为这首歌单独校准"))
        } finally { empty.destroy() }

        val identity = "song-and-artist"
        val source = "QQ音乐"
        val key = LyricsOverlayService.lyricOffsetPreferenceKey(identity, source)
        preferences().edit().putString(LyricsOverlayService.PREF_ACTIVE_LYRIC_IDENTITY, identity)
            .putString(LyricsOverlayService.PREF_ACTIVE_LYRIC_SOURCE, source)
            .putString(LyricsOverlayService.PREF_ACTIVE_LYRIC_TITLE, "Song").commit()
        val active = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val activity = active.get()
            assertTrue(activity.findViewById<View>(R.id.offset_earlier).isEnabled)
            assertTrue(activity.findViewById<View>(R.id.offset_later).isEnabled)
            activity.findViewById<View>(R.id.offset_earlier).performClick()
            assertEquals(100, preferences().getInt(key, 0))
            assertTrue(activity.findViewById<View>(R.id.offset_reset).isEnabled)
            activity.findViewById<View>(R.id.offset_reset).performClick()
            assertFalse("Reset removes the per-song override", preferences().contains(key))
            assertEquals(0, preferences().getInt(key, 0))
            assertFalse(activity.findViewById<View>(R.id.offset_reset).isEnabled)
        } finally { active.destroy() }
    }

    @Test fun homeMotionSwitchesPersistAcrossSettingsRecreation() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val first = Robolectric.buildActivity(SettingsActivity::class.java).create().get()
        assertTrue(first.motionSwitch(R.id.home_motion_scroll_easing).isChecked)
        assertTrue(first.motionSwitch(R.id.home_motion_stagger).isChecked)
        assertTrue(first.motionSwitch(R.id.home_motion_scale).isChecked)
        assertTrue(first.motionSwitch(R.id.home_motion_depth_blur).isChecked)
        first.motionSwitch(R.id.home_motion_scale).performClick()
        first.motionSwitch(R.id.home_motion_depth_blur).performClick()
        val restored = Robolectric.buildActivity(SettingsActivity::class.java).create().get()
        assertFalse(restored.motionSwitch(R.id.home_motion_scale).isChecked)
        assertFalse(restored.motionSwitch(R.id.home_motion_depth_blur).isChecked)
        assertTrue(restored.motionSwitch(R.id.home_motion_stagger).isChecked)
    }

    @Test fun legacyMotionModeMigratesToIndependentSwitches() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().putString("motion_mode", "reduced").commit()
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).create().get()
        assertTrue(activity.motionSwitch(R.id.home_motion_scroll_easing).isChecked)
        assertFalse(activity.motionSwitch(R.id.home_motion_stagger).isChecked)
        assertFalse(activity.motionSwitch(R.id.home_motion_scale).isChecked)
        assertFalse(activity.motionSwitch(R.id.home_motion_depth_blur).isChecked)
    }

    @Test fun motionNumericControlsRestoreSavedValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        HomeMotionPrefs.save(
            context,
            MotionStyle(scrollDurationMs = 500, staggerDelayMs = 90, scalePercent = 60, blurPercent = 40)
        )
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).create().get()
        assertEquals(500f, activity.findViewById<Slider>(R.id.home_motion_scroll_duration).value, .001f)
        assertEquals(90f, activity.findViewById<Slider>(R.id.home_motion_stagger_delay).value, .001f)
        assertEquals(60f, activity.findViewById<Slider>(R.id.home_motion_scale_strength).value, .001f)
        assertEquals(40f, activity.findViewById<Slider>(R.id.home_motion_blur_strength).value, .001f)
        assertEquals("500ms", activity.findViewById<TextView>(R.id.home_motion_scroll_value).text.toString())
        assertEquals("90ms", activity.findViewById<TextView>(R.id.home_motion_stagger_value).text.toString())
        assertEquals("60%", activity.findViewById<TextView>(R.id.home_motion_scale_value).text.toString())
        assertEquals("40%", activity.findViewById<TextView>(R.id.home_motion_blur_value).text.toString())
    }

    @Test fun turningOffAnEffectDisablesItsSliderAndPersists() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).create().get()
        val duration = activity.findViewById<Slider>(R.id.home_motion_scroll_duration)
        assertTrue(duration.isEnabled)
        activity.motionSwitch(R.id.home_motion_scroll_easing).performClick()
        assertFalse(activity.motionSwitch(R.id.home_motion_scroll_easing).isChecked)
        assertFalse("关掉效果后对应数值不可调", duration.isEnabled)
        val restored = Robolectric.buildActivity(SettingsActivity::class.java).create().get()
        assertFalse(restored.motionSwitch(R.id.home_motion_scroll_easing).isChecked)
        assertFalse(restored.findViewById<Slider>(R.id.home_motion_scroll_duration).isEnabled)
    }

    @Test fun motionPreviewAndStrengthRangesAreWired() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).create().get()
        assertNotNull(activity.findViewById<HomeMotionPreview>(R.id.home_motion_preview))
        assertEquals(200f, activity.findViewById<Slider>(R.id.home_motion_scale_strength).valueTo, .001f)
        assertEquals(200f, activity.findViewById<Slider>(R.id.home_motion_blur_strength).valueTo, .001f)
        assertEquals(200, MotionStyle.PERCENT_MAX)
        // 预览与主页共用同一套动效实现，套用样式不应抛错。
        activity.findViewById<HomeMotionPreview>(R.id.home_motion_preview)
            .applyStyle(MotionStyle(blurPercent = 200, scalePercent = 200))
    }

    private fun SettingsActivity.motionSwitch(id: Int) =
        findViewById<com.google.android.material.materialswitch.MaterialSwitch>(id)

    @Test fun translationModeChangesThePreviewWithoutChangingItsDocument() {
        preferences().edit().clear().commit()
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val activity = controller.get()
            val preview = activity.findViewById<CompactLyricsView>(R.id.overlay_preview)
            assertTrue(texts(preview).any { it.text.toString() == "Stay with me" })
            assertTrue(texts(preview).any { it.text.toString() == "让歌词陪着你" })
            activity.findViewById<View>(R.id.translation_original).performClick()
            assertFalse(texts(preview).any { it.text.toString() == "让歌词陪着你" })
            activity.findViewById<View>(R.id.translation_translated).performClick()
            assertFalse(texts(preview).any { it.text.toString() == "Stay with me" })
            assertTrue(texts(preview).any { it.text.toString() == "让歌词陪着你" })
        } finally { controller.destroy() }
    }

    @Test fun backgroundsHaveNoExtraGrayContainerInEitherTheme() {
        for (night in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers("w320dp-h640dp${if (night) "-night" else "-notnight"}")
            preferences().edit().clear().commit()
            val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
            try {
                val activity = controller.get()
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                val preview = activity.findViewById<CompactLyricsView>(R.id.overlay_preview)
                assertNull("Preview must not sit on another colored backdrop", (preview.parent as View).background)
                for ((button, alpha) in listOf(R.id.background_mode_transparent to 0,
                    R.id.background_mode_low to 140, R.id.background_mode_high to 255)) {
                    activity.findViewById<View>(button).performClick()
                    assertEquals(alpha, Color.alpha((preview.background as GradientDrawable).color!!.defaultColor))
                    layout(root)
                    val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bitmap))
                    val output = File("build/reports/material-ui/overlay-settings-${if (night) "dark" else "light"}-$alpha.png")
                    output.parentFile.mkdirs()
                    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            } finally { controller.destroy() }
        }
    }

    @Test fun homeThemeChangeKeepsTheLyricViewsAndPlaybackMetadata() {
        // Robolectric does not grant AndroidX's generated signature permission to the test app.
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().visible()
        try {
            val activity = controller.get()
            val home = activity.findViewById<HomeLyricsView>(R.id.lyric_home)
            home.setSnapshot(HomeLyricsView.Snapshot(track = "Theme song", artist = "Artist", durationMs = 10000, positionMs = 3000), true)
            home.setLyrics(LyricDocument(listOf(LyricLine(0, "Keep this lyric")), true))
            val lyric = texts(home).single { it.text.toString() == "Keep this lyric" }
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            layout(root)
            for (mode in listOf(Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_NO)) {
                val config = Configuration(activity.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
                }
                controller.configurationChange(config)
                layout(root)
                assertSame(activity, controller.get())
                assertSame(home, activity.findViewById<HomeLyricsView>(R.id.lyric_home))
                assertSame(lyric, texts(home).single { it.text.toString() == "Keep this lyric" })
                assertEquals("Theme song", activity.findViewById<TextView>(R.id.home_song).text.toString())
            }
        } finally { controller.stop().destroy() }
    }

    @Test fun settingsThemeChangePreservesActivityScrollAndWeight() {
        preferences().edit().clear().putInt(LyricsOverlayService.PREF_FONT_WEIGHT, 800).commit()
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create().start().visible()
        try {
            val activity = controller.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            layout(root)
            val scroll = activity.findViewById<NestedScrollView>(R.id.settings_scroll)
            scroll.scrollTo(0, 300)
            val scrollY = scroll.scrollY
            assertTrue(scrollY > 0)
            for (mode in listOf(Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_NO)) {
                val config = Configuration(activity.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
                }
                controller.configurationChange(config)
                layout(root)
                assertSame(activity, controller.get())
                assertEquals(mode, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                assertEquals(scrollY, activity.findViewById<NestedScrollView>(R.id.settings_scroll).scrollY)
                assertEquals(200f, activity.findViewById<Slider>(R.id.seek_font_weight).value, 0f)
                assertEquals(ContextCompat.getColor(activity, R.color.text_secondary),
                    activity.findViewById<TextView>(R.id.font_size_value).currentTextColor)
                assertEquals(ContextCompat.getColor(activity, R.color.control_active),
                    activity.findViewById<Slider>(R.id.seek_font_size).trackActiveTintList.defaultColor)
                assertEquals(1f, root.alpha, 0f)
                assertEquals(ContextCompat.getColor(activity, R.color.status_bar), activity.window.statusBarColor)
            }
        } finally { controller.stop().destroy() }
    }
}
