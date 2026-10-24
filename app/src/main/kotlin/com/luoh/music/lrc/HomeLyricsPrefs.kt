package com.luoh.music.lrc

import android.content.Context

/** 主页歌词对齐方式，默认沿用原来的左对齐。 */
object HomeLyricsPrefs {
    const val PREFS = "home_lyrics_v1"
    const val KEY_ALIGNMENT = "alignment"
    const val KEY_ALBUM_BACKGROUND = "album_background"
    const val LEFT = "left"
    const val CENTER = "center"
    const val RIGHT = "right"

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
