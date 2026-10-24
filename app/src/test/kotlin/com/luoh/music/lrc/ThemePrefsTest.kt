package com.luoh.music.lrc

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.ui.agora.ColorSchemePreset
import com.luoh.music.lrc.ui.agora.SchemeStyle
import com.luoh.music.lrc.ui.agora.ThemeMode
import com.luoh.music.lrc.ui.agora.colorSchemeForPreset
import com.luoh.music.lrc.ui.agora.colorSchemeForSeed
import com.luoh.music.lrc.ui.agora.withAmoledBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemePrefsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE)

    @Before fun clearPreferences() { prefs.edit().clear().commit() }

    @Test fun defaultsAndUnknownChoicesUseSystemOceanAndNeutral() {
        val expected = AppThemePreferences(ThemeMode.FOLLOW_DEVICE, false, false,
            ColorSchemePreset.OCEAN, SchemeStyle.NEUTRAL)
        assertEquals(expected, ThemePrefs.read(prefs))
        prefs.edit().putString(ThemePrefs.KEY, "unknown")
            .putString(ThemePrefs.KEY_COLOR_SCHEME, "unknown")
            .putString(ThemePrefs.KEY_SCHEME_STYLE, "unknown").commit()
        assertEquals(expected, ThemePrefs.read(prefs))
    }

    @Test fun existingThemeAndCustomColorSurviveAddingNewPreferences() {
        prefs.edit().putString(ThemePrefs.KEY, ThemePrefs.DARK)
            .putString(AccentPrefs.KEY, "#AD1457").commit()
        assertEquals(ThemeMode.DARK, ThemePrefs.read(prefs).mode)
        assertEquals(ColorSchemePreset.OCEAN, ThemePrefs.read(prefs).preset)
        assertEquals(android.graphics.Color.parseColor("#AD1457"), AccentPrefs.customColor(context))
        prefs.edit().putBoolean(ThemePrefs.KEY_AMOLED, true)
            .putBoolean(ThemePrefs.KEY_DYNAMIC_COLOR, true)
            .putString(ThemePrefs.KEY_COLOR_SCHEME, ColorSchemePreset.ROSE.name)
            .putString(ThemePrefs.KEY_SCHEME_STYLE, SchemeStyle.EXPRESSIVE.name).commit()
        val reloaded = context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE)
        assertEquals(AppThemePreferences(ThemeMode.DARK, true, true,
            ColorSchemePreset.ROSE, SchemeStyle.EXPRESSIVE), ThemePrefs.read(reloaded))
        assertEquals("#AD1457", reloaded.getString(AccentPrefs.KEY, null))
        AccentPrefs.clear(context)
        assertNull(AccentPrefs.customColor(context))
        assertTrue(ThemePrefs.read(reloaded).amoled)
    }

    @Test fun everyPresetAndStyleHasReadablePrimaryTextInBothThemes() {
        for (dark in listOf(false, true)) {
            for (preset in ColorSchemePreset.entries) {
                for (style in SchemeStyle.entries) {
                    val scheme = colorSchemeForPreset(preset, style, dark)
                    assertTrue("$preset/$style dark=$dark",
                        ColorUtils.calculateContrast(scheme.onPrimary.toArgb(), scheme.primary.toArgb()) >= 4.5)
                    val amoled = scheme.withAmoledBackground(dark, true)
                    val endpoint = if (dark) Color.Black else Color.White
                    assertEquals(endpoint, amoled.background)
                    assertEquals(endpoint, amoled.surface)
                    assertEquals(scheme.primary, amoled.primary)
                    assertEquals(scheme.surfaceContainer, amoled.surfaceContainer)
                    assertEquals(scheme, scheme.withAmoledBackground(dark, false))
                    assertFalse(amoled.onSurface == amoled.surface)
                }
            }
        }
    }

    @Test fun selectedStyleAlsoChangesCustomSeedColors() {
        val seed = android.graphics.Color.parseColor("#0277BD")
        val neutral = colorSchemeForSeed(seed, style = SchemeStyle.NEUTRAL)
        val vibrant = colorSchemeForSeed(seed, style = SchemeStyle.VIBRANT)
        assertNotEquals(neutral.primary, vibrant.primary)
        assertEquals(colorSchemeForPreset(ColorSchemePreset.OCEAN, SchemeStyle.NEUTRAL).primary,
            neutral.primary)
    }

    @Test fun grayscaleSeedsNeverAcquireBlueAndDarkGrayStaysExactInLightTheme() {
        val darkGray = android.graphics.Color.parseColor("#111111")
        for (dark in listOf(false, true)) for (style in SchemeStyle.entries) {
            val scheme = colorSchemeForSeed(darkGray, dark, style)
            for (color in listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.background,
                scheme.surface, scheme.surfaceContainer, scheme.outline)) {
                val argb = color.toArgb()
                assertEquals(android.graphics.Color.red(argb), android.graphics.Color.green(argb))
                assertEquals(android.graphics.Color.red(argb), android.graphics.Color.blue(argb))
            }
            assertTrue(ColorUtils.calculateContrast(scheme.primary.toArgb(), scheme.background.toArgb()) >= 4.5)
        }
        assertEquals(Color(darkGray), colorSchemeForSeed(darkGray, style = SchemeStyle.NEUTRAL).primary)
    }
}
