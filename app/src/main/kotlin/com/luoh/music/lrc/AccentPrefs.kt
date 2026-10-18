package com.luoh.music.lrc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton

/**
 * App accent color. The default is the original monochrome accent (black in light,
 * white in dark); users may choose a custom color in 设置 › 应用外观 › 主调颜色.
 */
object AccentPrefs {
    const val KEY = "accent_color"
    private const val WHITE_CONTRAST_THRESHOLD = 4.5
    private const val LARGE_TEXT_CONTRAST_THRESHOLD = 3.0

    private fun prefs(context: Context) =
        context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE)

    fun customColor(context: Context): Int? = prefs(context).getString(KEY, null)
        ?.let { runCatching { Color.parseColor(it) }.getOrNull() }

    fun set(context: Context, hex: String) {
        val normalized = hex.trim().takeIf { Regex("^#?[0-9a-fA-F]{6}$").matches(it) }
            ?.let { if (it.startsWith("#")) it else "#$it" }
            ?: return
        prefs(context).edit().putString(KEY, normalized.uppercase()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    /** Main accent: the seed resolved into the scheme's primary tone. */
    fun accent(context: Context): Int = AppTheme.scheme(context).primary

    /** Text/icon color drawn on top of [accent]. */
    fun onAccent(context: Context): Int = AppTheme.scheme(context).onPrimary

    /** Accent used by the home current lyric line; falls back when a custom color would be unreadable. */
    fun lyricAccent(context: Context): Int {
        val custom = customColor(context)
            ?: return AppTheme.scheme(context).primary
        val background = DesignTokens.page(context)
        return if (ColorUtils.calculateContrast(custom, background) >= LARGE_TEXT_CONTRAST_THRESHOLD) custom
        else DesignTokens.textPrimary(context)
    }

    /** Accent used by small text (12sp); returns [defaultRes] when no readable custom color is set. */
    fun textAccent(context: Context, defaultRes: Int): Int {
        val custom = customColor(context)
            ?: return ContextCompat.getColor(context, defaultRes)
        val background = DesignTokens.card(context)
        return if (ColorUtils.calculateContrast(custom, background) >= WHITE_CONTRAST_THRESHOLD) custom
        else ContextCompat.getColor(context, defaultRes)
    }

    /** Accent used on the floating window's dark chrome pill. */
    fun overlayAccent(context: Context): Int {
        val custom = customColor(context) ?: return Color.WHITE
        val pill = Color.rgb(12, 12, 14)
        return if (ColorUtils.calculateContrast(custom, pill) >= LARGE_TEXT_CONTRAST_THRESHOLD) custom
        else Color.WHITE
    }

    fun styleSegment(context: Context, button: MaterialButton) {
        val segmented = button.parent as? SegmentedControl
        if (segmented != null) {
            segmented.setAccentColor(accent(context))
            return
        }
        val states = arrayOf(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_enabled),
            intArrayOf()
        )
        button.backgroundTintList = ColorStateList(states, intArrayOf(
            accent(context),
            DesignTokens.block(context),
            ContextCompat.getColor(context, R.color.app_line_soft)
        ))
        button.setTextColor(ColorStateList(states, intArrayOf(
            onAccent(context),
            DesignTokens.textPrimary(context),
            DesignTokens.textTertiary(context)
        )))
    }
}
