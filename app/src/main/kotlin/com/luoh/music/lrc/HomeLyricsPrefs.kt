package com.luoh.music.lrc

import android.content.Context

data class HomeLyricAppearance(
    val fontSize: Int = 28,
    val translationSize: Int = 18,
    val fontWeight: Int = 500,
    val lineSpacing: Int = 12,
) {
    fun normalized() = copy(fontSize = fontSize.coerceIn(16, 48),
        translationSize = translationSize.coerceIn(12, 32),
        fontWeight = fontWeight.coerceIn(300, 800) / 100 * 100,
        lineSpacing = lineSpacing.coerceIn(0, 28))
}

/** 主页歌词对齐方式，默认沿用原来的左对齐。 */
object HomeLyricsPrefs {
    const val PREFS = "home_lyrics_v1"
    const val KEY_ALIGNMENT = "alignment"
    const val KEY_ALBUM_BACKGROUND = "album_background"
    const val KEY_FONT_SIZE = "font_size"
    const val KEY_TRANSLATION_SIZE = "translation_size"
    const val KEY_FONT_WEIGHT = "font_weight"
    const val KEY_LINE_SPACING = "line_spacing"
    const val LEFT = "left"
    const val CENTER = "center"
    const val RIGHT = "right"

    fun appearance(context: Context): HomeLyricAppearance {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return HomeLyricAppearance(prefs.getInt(KEY_FONT_SIZE, 28), prefs.getInt(KEY_TRANSLATION_SIZE, 18),
            prefs.getInt(KEY_FONT_WEIGHT, 500), prefs.getInt(KEY_LINE_SPACING, 12)).normalized()
    }

    fun saveAppearance(context: Context, value: HomeLyricAppearance) {
        val style = value.normalized()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_FONT_SIZE, style.fontSize).putInt(KEY_TRANSLATION_SIZE, style.translationSize)
            .putInt(KEY_FONT_WEIGHT, style.fontWeight).putInt(KEY_LINE_SPACING, style.lineSpacing).apply()
    }

    fun albumBackground(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ALBUM_BACKGROUND, true)

    fun saveAlbumBackground(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ALBUM_BACKGROUND, enabled).apply()
    }

    fun alignment(context: Context): String {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ALIGNMENT, LEFT)
        return when (value) {
            CENTER, RIGHT -> value ?: LEFT
            else -> LEFT
        }
    }

    fun save(context: Context, value: String) {
        val normalized = when (value) {
            CENTER, RIGHT -> value
            else -> LEFT
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ALIGNMENT, normalized)
            .apply()
    }
}
