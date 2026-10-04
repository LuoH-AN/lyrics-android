package com.luoh.music.lrc

import android.content.Context

/** 主页歌词动效偏好：完整（错位/缩放/模糊）/ 精简（仅滚动与渐隐）/ 关闭（直接跳转）。 */
object HomeMotionPrefs {
    const val PREFS = "home_motion_v1"
    const val KEY = "motion_mode"
    const val FULL = "full"
    const val REDUCED = "reduced"
    const val OFF = "off"

    fun normalize(mode: String?): String = when (mode) {
        REDUCED -> REDUCED
        OFF -> OFF
        else -> FULL
    }

    fun mode(context: Context): String =
        normalize(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
}
