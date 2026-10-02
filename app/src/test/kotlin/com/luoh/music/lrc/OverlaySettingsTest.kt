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

    @Test fun fontWeightIsNormalizedAndRestoredInTheActualPreview() {
        assertEquals(300, OverlayAppearance.normalizeWeight(-1))
        assertEquals(700, OverlayAppearance.normalizeWeight(651))
        assertEquals(900, OverlayAppearance.normalizeWeight(Int.MAX_VALUE))
        preferences().edit().clear().putInt(LyricsOverlayService.PREF_FONT_WEIGHT, 400).commit()
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val activity = controller.get()
            val slider = activity.findViewById<Slider>(R.id.seek_font_weight)
            slider.requestFocus()
            slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            assertEquals(500, preferences().getInt(LyricsOverlayService.PREF_FONT_WEIGHT, 0))
            val preview = activity.findViewById<CompactLyricsView>(R.id.overlay_preview)
            assertTrue(texts(preview).isNotEmpty())
            texts(preview).forEach { assertEquals(500, it.typeface.weight) }
        } finally { controller.destroy() }
        val restored = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            assertEquals(500f, restored.get().findViewById<Slider>(R.id.seek_font_weight).value, 0f)
        } finally { restored.destroy() }
    }

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
                assertEquals(800f, activity.findViewById<Slider>(R.id.seek_font_weight).value, 0f)
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
