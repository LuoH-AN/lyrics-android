package com.luoh.music.lrc

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat

/** 主题偏好：跟随系统 / 亮 / 暗。 */
object ThemePrefs {
    const val PREFS = "app_theme_v1"
    const val KEY = "theme_mode"
    const val FOLLOW = "follow"
    const val LIGHT = "light"
    const val DARK = "dark"

    fun apply(mode: String?): Boolean {
        val nightMode = when (mode) {
            LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() == nightMode) return false
        AppCompatDelegate.setDefaultNightMode(nightMode)
        return true
    }

    fun updateSystemBars(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            activity.window.isStatusBarContrastEnforced = false
            activity.window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }
}
