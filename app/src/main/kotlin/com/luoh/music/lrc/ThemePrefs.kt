package com.luoh.music.lrc

import android.content.SharedPreferences
import com.luoh.music.lrc.ui.agora.ColorSchemePreset
import com.luoh.music.lrc.ui.agora.SchemeStyle
import com.luoh.music.lrc.ui.agora.ThemeMode

/** Persistent theme selection consumed reactively by the Compose root. */
object ThemePrefs {
    const val PREFS = "app_theme_v1"
    const val KEY = "theme_mode"
    const val FOLLOW = "follow"
    const val LIGHT = "light"
    const val DARK = "dark"
    const val KEY_AMOLED = "amoled_enabled"
    const val KEY_DYNAMIC_COLOR = "dynamic_color"
    const val KEY_COLOR_SCHEME = "color_scheme_preset"
    const val KEY_SCHEME_STYLE = "scheme_style"

    internal fun read(prefs: SharedPreferences) = AppThemePreferences(
        mode = when (prefs.getString(KEY, FOLLOW)) {
            LIGHT -> ThemeMode.LIGHT
            DARK -> ThemeMode.DARK
            else -> ThemeMode.FOLLOW_DEVICE
        },
        amoled = prefs.getBoolean(KEY_AMOLED, false),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
        preset = ColorSchemePreset.entries.firstOrNull {
            it.name == prefs.getString(KEY_COLOR_SCHEME, null)
        } ?: ColorSchemePreset.OCEAN,
        style = SchemeStyle.entries.firstOrNull {
            it.name == prefs.getString(KEY_SCHEME_STYLE, null)
        } ?: SchemeStyle.NEUTRAL,
    )
}

internal data class AppThemePreferences(
    val mode: ThemeMode,
    val amoled: Boolean,
    val dynamicColor: Boolean,
    val preset: ColorSchemePreset,
    val style: SchemeStyle,
)
