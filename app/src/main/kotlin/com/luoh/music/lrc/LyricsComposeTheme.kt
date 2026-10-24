package com.luoh.music.lrc

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.luoh.music.lrc.ui.agora.AgoraTheme
import com.luoh.music.lrc.ui.agora.ProvideAgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.ThemeMode

@Composable
internal fun LyricsComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE) }
    var version by remember(prefs) { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val appearance = remember(prefs, version) { ThemePrefs.read(prefs) }
    val systemDark = isSystemInDarkTheme()
    val dark = appearance.mode == ThemeMode.DARK ||
        appearance.mode == ThemeMode.FOLLOW_DEVICE && systemDark
    val accent = remember(context, version) { AccentPrefs.customColor(context) }
    val reduceMotion = remember(prefs, version) { prefs.getBoolean("reduce_motion", false) }
    SideEffect {
        (context as? Activity)?.let { activity ->
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= 29) {
                activity.window.isNavigationBarContrastEnforced = false
                activity.window.isStatusBarContrastEnforced = false
            }
        }
    }
    AgoraTheme(
        themeMode = appearance.mode,
        colorSchemePreset = appearance.preset,
        schemeStyle = appearance.style,
        dynamicColor = appearance.dynamicColor,
        amoledEnabled = appearance.amoled,
        seedColor = accent,
    ) {
        ProvideAgoraMotionPolicy(reduceMotion, content)
    }
}
