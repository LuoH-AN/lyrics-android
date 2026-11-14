package com.luoh.music.lrc

import android.content.Context

data class HomeLyricAppearance(
    val fontSize: Int = 28,
    val translationSize: Int = 17,
    val fontWeight: Int = 500,
    val lineSpacing: Int = 10,
) {
    fun normalized() = copy(fontSize = fontSize.coerceIn(16, 48),
        translationSize = translationSize.coerceIn(12, 32),
        fontWeight = fontWeight.coerceIn(300, 800) / 100 * 100,
        lineSpacing = lineSpacing.coerceIn(0, 28))
}

/** Direct reading defaults; saved appearance and mode choices take precedence. */
object HomeLyricsPrefs {
    const val PREFS = "home_lyrics_v1"
    const val KEY_ALIGNMENT = "alignment"
    const val KEY_ALBUM_BACKGROUND = "album_background"
    const val KEY_FONT_SIZE = "font_size"
    const val KEY_TRANSLATION_SIZE = "translation_size"
    const val KEY_FONT_WEIGHT = "font_weight"
    const val KEY_LINE_SPACING = "line_spacing"
    const val KEY_DISPLAY_MODE = "display_mode"
    const val SCROLL = "scroll"
    const val POP = "pop"
    const val LEFT = "left"
    const val CENTER = "center"
    const val RIGHT = "right"

    fun displayMode(context: Context): String =
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DISPLAY_MODE, POP) == POP) POP else SCROLL

    fun saveDisplayMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DISPLAY_MODE, if (mode == POP) POP else SCROLL).apply()
    }

    fun appearance(context: Context): HomeLyricAppearance {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = HomeLyricAppearance()
        return HomeLyricAppearance(prefs.getInt(KEY_FONT_SIZE, defaults.fontSize),
            prefs.getInt(KEY_TRANSLATION_SIZE, defaults.translationSize),
            prefs.getInt(KEY_FONT_WEIGHT, defaults.fontWeight),
            prefs.getInt(KEY_LINE_SPACING, defaults.lineSpacing)).normalized()
    }

    fun saveAppearance(context: Context, value: HomeLyricAppearance) {
        val style = value.normalized()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_FONT_SIZE, style.fontSize).putInt(KEY_TRANSLATION_SIZE, style.translationSize)
            .putInt(KEY_FONT_WEIGHT, style.fontWeight).putInt(KEY_LINE_SPACING, style.lineSpacing).apply()
    }

    fun albumBackground(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt("background_revision", 0) < 1) {
            prefs.edit().putBoolean(KEY_ALBUM_BACKGROUND, false).putInt("background_revision", 1).apply()
            return false
        }
        return prefs.getBoolean(KEY_ALBUM_BACKGROUND, false)
    }

    fun saveAlbumBackground(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ALBUM_BACKGROUND, enabled).putInt("background_revision", 1).apply()
    }

    fun alignment(context: Context): String {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ALIGNMENT, CENTER)
        return when (value) {
            LEFT, CENTER, RIGHT -> value ?: CENTER
            else -> CENTER
        }
    }

    fun save(context: Context, value: String) {
        val normalized = when (value) {
            LEFT, CENTER, RIGHT -> value
            else -> CENTER
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ALIGNMENT, normalized)
            .apply()
    }
}
