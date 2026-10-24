package com.luoh.music.lrc

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppearancePreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun percentageControlsPreserveExistingDesktopFontWeights() {
        assertEquals(100, OverlayAppearance.weightToPercent(400))
        for (weight in 300..900 step 100) {
            assertEquals(weight, OverlayAppearance.percentToWeight(OverlayAppearance.weightToPercent(weight)))
        }
        assertEquals(300, OverlayAppearance.percentToWeight(Int.MIN_VALUE))
        assertEquals(900, OverlayAppearance.percentToWeight(Int.MAX_VALUE))
        assertEquals(700, OverlayAppearance.normalizeWeight(651))
    }

    @Test fun desktopBackgroundModesKeepLegacyMediumOpacity() {
        assertEquals(0, Color.alpha(OverlayAppearance.backgroundColor(null)))
        assertEquals(140, Color.alpha(OverlayAppearance.backgroundColor(LyricsOverlayService.BACKGROUND_LOW)))
        assertEquals(140, Color.alpha(OverlayAppearance.backgroundColor(LyricsOverlayService.BACKGROUND_MEDIUM)))
        assertEquals(255, Color.alpha(OverlayAppearance.backgroundColor(LyricsOverlayService.BACKGROUND_HIGH)))
    }

    @Test fun legacyMotionModeMigratesOnceAndIndependentChangesPersist() {
        val prefs = context.getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().putString("motion_mode", "reduced").commit()
        val migrated = HomeMotionPrefs.style(context)
        assertTrue(migrated.easedScroll)
        assertFalse(migrated.stagger)
        assertFalse(migrated.scale)
        assertFalse(migrated.depthBlur)
        assertTrue(prefs.contains(HomeMotionPrefs.KEY_EASED_SCROLL))

        val edited = migrated.copy(stagger = true, scrollDurationMs = 500, staggerDelayMs = 90,
            scalePercent = 60, blurPercent = 40)
        HomeMotionPrefs.save(context, edited)
        prefs.edit().putString("motion_mode", "off").commit()
        assertEquals(edited, HomeMotionPrefs.style(context))
    }

    @Test fun storedMotionValuesAreClampedToTheSupportedControlRanges() {
        context.getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE).edit().clear()
            .putBoolean(HomeMotionPrefs.KEY_EASED_SCROLL, true)
            .putInt(HomeMotionPrefs.KEY_SCROLL_DURATION, 1551)
            .putInt(HomeMotionPrefs.KEY_STAGGER_DELAY, -1)
            .putInt(HomeMotionPrefs.KEY_SCALE_PERCENT, 203)
            .putInt(HomeMotionPrefs.KEY_BLUR_PERCENT, 43).commit()
        val style = HomeMotionPrefs.style(context)
        assertEquals(1500, style.scrollDurationMs)
        assertEquals(0, style.staggerDelayMs)
        assertEquals(200, style.scalePercent)
        assertEquals(40, style.blurPercent)
    }
}
