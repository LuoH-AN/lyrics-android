package com.luoh.music.lrc

import android.content.Context
import androidx.core.content.ContextCompat

/**
 * The app's design scale. Screens ask for a role here instead of naming a
 * colour, a corner radius or a duration themselves.
 */
object DesignTokens {
    // ---- Radius scale (FlClash's AppCorner) ---------------------------------
    const val RADIUS_NONE = 0f
    const val RADIUS_XS = 4f
    const val RADIUS_SM = 8f
    const val RADIUS_MD = 16f
    const val RADIUS_LG = 20f
    const val RADIUS_XL = 24f
    const val RADIUS_XXL = 28f
    const val RADIUS_ALERT = 34f
    const val RADIUS_FULL = 1000f

    /** Legacy names kept for existing call sites. */
    const val RADIUS_SMALL = RADIUS_SM
    const val RADIUS_CONTROL = RADIUS_MD
    const val RADIUS_CARD = RADIUS_XL
    const val RADIUS_SHEET = RADIUS_XXL

    // ---- Sizes --------------------------------------------------------------
    const val LIST_ROW_MIN_HEIGHT = 56f
    const val LIST_ROW_VERTICAL_PADDING = 10f
    const val PAGE_TOOLBAR_HEIGHT = 72f
    const val SHEET_TOOLBAR_HEIGHT = 48f
    const val SHEET_APP_BAR_HEIGHT = 68f
    const val GLYPH_SIZE = 24f
    const val GLYPH_STROKE = 1.8f
    const val HIT_TARGET = 48f

    // ---- Motion -------------------------------------------------------------
    object Motion {
        const val PAGE_TRANSITION_MS = 180L
        const val DESKTOP_FADE_MS = 200L
        const val DIALOG_MS = 180L
        const val PRESS_MS = 80L
        const val RELEASE_MS = 120L
        const val SEGMENT_INDICATOR_MS = 250L
        const val SEGMENT_TEXT_MS = 200L
        const val GLYPH_FILL_MS = 350L
        const val SKELETON_MS = 1600L
        const val EMPTY_ENTER_MS = 900L
        const val EMPTY_EXIT_MS = 150L
        const val THEME_FADE_MS = 200L
    }

    // ---- Surfaces -----------------------------------------------------------
    fun page(context: Context): Int = AppTheme.scheme(context).page

    /** A card or a grouped run of rows, one step above a page. */
    fun card(context: Context): Int = AppTheme.scheme(context).card

    /** A block inside a card: segment wells, unselected pills, input fills. */
    fun block(context: Context): Int = AppTheme.scheme(context).block

    /** A small mark inside a block: badges, tracks, glyph wells. */
    fun inset(context: Context): Int = AppTheme.scheme(context).inset

    /** Floating layers: light card, dark block. */
    fun floating(context: Context): Int = AppTheme.scheme(context).floating

    /** The hairline that divides one surface, never an outline. */
    fun hairline(context: Context): Int = AppTheme.scheme(context).hairline

    // ---- Colour roles -------------------------------------------------------
    fun accent(context: Context): Int = AppTheme.scheme(context).primary

    fun onAccent(context: Context): Int = AppTheme.scheme(context).onPrimary

    fun accentContainer(context: Context): Int = AppTheme.scheme(context).primaryContainer

    fun onAccentContainer(context: Context): Int = AppTheme.scheme(context).onPrimaryContainer

    fun textPrimary(context: Context): Int = AppTheme.scheme(context).onSurface

    fun textSecondary(context: Context): Int = AppTheme.scheme(context).onSurfaceVariant

    fun textTertiary(context: Context): Int = AppTheme.scheme(context).outline

    fun danger(context: Context): Int = AppTheme.scheme(context).error

    fun success(context: Context): Int = AppTheme.scheme(context).success

    fun warning(context: Context): Int = AppTheme.scheme(context).warning

    fun scheme(context: Context): AppScheme = AppTheme.scheme(context)

    // ---- Helpers ------------------------------------------------------------
    fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + .5f).toInt()

    /** The largest radius token that stays under a third of the shortest side. */
    fun fitRadius(shortestSideDp: Float): Float = when {
        shortestSideDp < 24f -> RADIUS_XS
        shortestSideDp < 48f -> RADIUS_SM
        shortestSideDp < 96f -> RADIUS_MD
        shortestSideDp < 180f -> RADIUS_LG
        else -> RADIUS_XL
    }

    fun color(context: Context, res: Int): Int = ContextCompat.getColor(context, res)
}
