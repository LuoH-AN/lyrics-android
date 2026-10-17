package com.luoh.music.lrc

import android.content.Context
import androidx.core.content.ContextCompat

/**
 * The small design scale this app draws from: four surface levels, a radius
 * scale picked by the shortest side, and the same accent the rest of the app
 * uses. Screens should ask for a role here instead of naming a color or a
 * corner radius themselves.
 */
object DesignTokens {
    // FlClash's scale: 8 / 14 / 24 / 28 / 34 / pill.
    const val RADIUS_SMALL = 8f
    const val RADIUS_CONTROL = 14f
    const val RADIUS_CARD = 24f
    const val RADIUS_SHEET = 28f
    const val RADIUS_ALERT = 34f
    const val RADIUS_PILL = 1000f

    /** A page, the lowest level: the scrolling background. */
    fun page(context: Context): Int = color(context, R.color.app_bg)

    /** A card or a grouped run of rows, one step above a page. */
    fun card(context: Context): Int = color(context, R.color.app_surface)

    /** A block inside a card: segment wells, unselected pills, input fills. */
    fun block(context: Context): Int = color(context, R.color.app_surface_container)

    /** A small mark inside a block: badges, tracks, glyph wells. */
    fun inset(context: Context): Int = color(context, R.color.app_surface_inset)

    /** The hairline that divides one surface, never an outline. */
    fun hairline(context: Context): Int = color(context, R.color.app_line_soft)

    fun accent(context: Context): Int = AccentPrefs.accent(context)

    fun onAccent(context: Context): Int = AccentPrefs.onAccent(context)

    fun dp(context: Context, value: Float): Float = value * context.resources.displayMetrics.density

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + .5f).toInt()

    private fun color(context: Context, res: Int): Int = ContextCompat.getColor(context, res)
}
