package com.luoh.music.lrc

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.luoh.music.lrc.ui.agora.*

@Composable
internal fun LyricsComposeTheme(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val prefs=remember(context) { context.getSharedPreferences(ThemePrefs.PREFS,Context.MODE_PRIVATE) }
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> version++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val mode=remember(version) { when(prefs.getString(ThemePrefs.KEY,ThemePrefs.FOLLOW)) { ThemePrefs.LIGHT->ThemeMode.LIGHT;ThemePrefs.DARK->ThemeMode.DARK;else->ThemeMode.FOLLOW_DEVICE } }
    val systemDark=isSystemInDarkTheme()
    val dark=mode==ThemeMode.DARK || mode==ThemeMode.FOLLOW_DEVICE && systemDark
    val accent=remember(version) { AccentPrefs.customColor(context) }
    val reduceMotion=remember(version) { prefs.getBoolean("reduce_motion",false) }
    SideEffect {
        (context as? Activity)?.let { activity ->
            WindowInsetsControllerCompat(activity.window,activity.window.decorView).apply { isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark }
            if(android.os.Build.VERSION.SDK_INT>=29) { activity.window.isNavigationBarContrastEnforced=false;activity.window.isStatusBarContrastEnforced=false }
        }
    }
    AgoraTheme(themeMode=mode) {
        val scheme=remember(accent,dark) { accent?.let { colorSchemeForSeed(it,isDark=dark) } }
        ProvideAgoraMotionPolicy(reduceMotion) {
            if(scheme!=null) MaterialTheme(colorScheme=scheme,typography=MaterialTheme.typography,content=content) else content()
        }
    }
}
