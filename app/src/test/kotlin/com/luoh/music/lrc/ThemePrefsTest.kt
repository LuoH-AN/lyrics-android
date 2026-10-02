package com.luoh.music.lrc

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemePrefsTest {
    @After fun resetTheme() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @Test fun normalizedModesAreAppliedOnlyWhenTheyChange() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        assertFalse(ThemePrefs.apply(null))
        assertFalse(ThemePrefs.apply("unknown"))
        assertFalse(ThemePrefs.apply(ThemePrefs.FOLLOW))
        assertTrue(ThemePrefs.apply(ThemePrefs.LIGHT))
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
        assertFalse(ThemePrefs.apply(ThemePrefs.LIGHT))
        assertTrue(ThemePrefs.apply(ThemePrefs.DARK))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        assertFalse(ThemePrefs.apply(ThemePrefs.DARK))
        assertTrue(ThemePrefs.apply(ThemePrefs.FOLLOW))
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
    }

    @Test @Config(qualifiers = "notnight")
    fun lightSystemBarsUseDarkIcons() = assertSystemBars(lightIcons = false)

    @Test @Config(qualifiers = "night")
    fun darkSystemBarsUseLightIcons() = assertSystemBars(lightIcons = true)

    private fun assertSystemBars(lightIcons: Boolean) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            ThemePrefs.updateSystemBars(activity)
            assertEquals(ContextCompat.getColor(activity, R.color.status_bar), activity.window.statusBarColor)
            assertEquals(ContextCompat.getColor(activity, R.color.nav_bar), activity.window.navigationBarColor)
            val bars = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            assertEquals(!lightIcons, bars.isAppearanceLightStatusBars)
            assertEquals(!lightIcons, bars.isAppearanceLightNavigationBars)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun homeAndSettingsHandleThemeChangesWithoutActivityRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (type in listOf(MainActivity::class.java, SettingsActivity::class.java)) {
            val info = context.packageManager.getActivityInfo(ComponentName(context, type), 0)
            assertTrue("${type.simpleName} must handle uiMode", info.configChanges and ActivityInfo.CONFIG_UI_MODE != 0)
        }
    }
}
