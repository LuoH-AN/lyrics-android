package com.luoh.music.lrc

import android.content.Context

/** 主页歌词动效样式：每个效果独立开关，并可单独调数值。 */
data class MotionStyle(
    val easedScroll: Boolean = true,
    val scrollDurationMs: Int = DURATION_DEFAULT,
    val stagger: Boolean = true,
    val staggerDelayMs: Int = STAGGER_DEFAULT,
    val scale: Boolean = true,
    val scalePercent: Int = PERCENT_DEFAULT,
    val depthBlur: Boolean = true,
    val blurPercent: Int = PERCENT_DEFAULT
) {
    companion object {
        const val DURATION_MIN = 300
        const val DURATION_MAX = 1500
        const val DURATION_STEP = 50
        const val DURATION_DEFAULT = 900

        const val STAGGER_MIN = 0
        const val STAGGER_MAX = 120
        const val STAGGER_STEP = 10
        const val STAGGER_DEFAULT = 40

        const val PERCENT_MIN = 0
        const val PERCENT_MAX = 200
        const val PERCENT_STEP = 5
        const val PERCENT_DEFAULT = 100

        fun snapDuration(value: Int) = snap(value, DURATION_MIN, DURATION_MAX, DURATION_STEP)
        fun snapStagger(value: Int) = snap(value, STAGGER_MIN, STAGGER_MAX, STAGGER_STEP)
        fun snapPercent(value: Int) = snap(value, PERCENT_MIN, PERCENT_MAX, PERCENT_STEP)

        private fun snap(value: Int, min: Int, max: Int, step: Int): Int =
            (min + (value - min).coerceAtLeast(0) / step * step).coerceIn(min, max)
    }
}

/** 主页歌词动效偏好：缓动滚动 / 逐行错位 / 行间缩放 / 远景模糊，各自独立开关与数值。 */
object HomeMotionPrefs {
    const val PREFS = "home_motion_v1"
    const val KEY_EASED_SCROLL = "eased_scroll"
    const val KEY_SCROLL_DURATION = "scroll_duration_ms"
    const val KEY_STAGGER = "stagger"
    const val KEY_STAGGER_DELAY = "stagger_delay_ms"
    const val KEY_SCALE = "scale"
    const val KEY_SCALE_PERCENT = "scale_percent"
    const val KEY_DEPTH_BLUR = "depth_blur"
    const val KEY_BLUR_PERCENT = "blur_percent"

    // 旧版三档模式，仅用于把已存的设置一次性迁移到独立开关。
    private const val LEGACY_KEY = "motion_mode"
    private const val LEGACY_FULL = "full"
    private const val LEGACY_REDUCED = "reduced"
    private const val LEGACY_OFF = "off"

    fun style(context: Context): MotionStyle {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_EASED_SCROLL)) {
            val migrated = fromLegacy(prefs.getString(LEGACY_KEY, null))
            save(context, migrated)
            return migrated
        }
        return MotionStyle(
            easedScroll = prefs.getBoolean(KEY_EASED_SCROLL, true),
            scrollDurationMs = MotionStyle.snapDuration(
                prefs.getInt(KEY_SCROLL_DURATION, MotionStyle.DURATION_DEFAULT)
            ),
            stagger = prefs.getBoolean(KEY_STAGGER, true),
            staggerDelayMs = MotionStyle.snapStagger(
                prefs.getInt(KEY_STAGGER_DELAY, MotionStyle.STAGGER_DEFAULT)
            ),
            scale = prefs.getBoolean(KEY_SCALE, true),
            scalePercent = MotionStyle.snapPercent(
                prefs.getInt(KEY_SCALE_PERCENT, MotionStyle.PERCENT_DEFAULT)
            ),
            depthBlur = prefs.getBoolean(KEY_DEPTH_BLUR, true),
            blurPercent = MotionStyle.snapPercent(
                prefs.getInt(KEY_BLUR_PERCENT, MotionStyle.PERCENT_DEFAULT)
            )
        )
    }

    fun save(context: Context, style: MotionStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_EASED_SCROLL, style.easedScroll)
            .putInt(KEY_SCROLL_DURATION, style.scrollDurationMs)
            .putBoolean(KEY_STAGGER, style.stagger)
            .putInt(KEY_STAGGER_DELAY, style.staggerDelayMs)
            .putBoolean(KEY_SCALE, style.scale)
            .putInt(KEY_SCALE_PERCENT, style.scalePercent)
            .putBoolean(KEY_DEPTH_BLUR, style.depthBlur)
            .putInt(KEY_BLUR_PERCENT, style.blurPercent)
            .apply()
    }

    private fun fromLegacy(mode: String?): MotionStyle = when (mode) {
        LEGACY_REDUCED -> MotionStyle(stagger = false, scale = false, depthBlur = false)
        LEGACY_OFF -> MotionStyle(easedScroll = false, stagger = false, scale = false, depthBlur = false)
        LEGACY_FULL -> MotionStyle()
        else -> MotionStyle()
    }
}
