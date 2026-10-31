package com.luoh.music.lrc

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

data class OverlayTextStyle(
    val alignment: String = OverlayAppearance.LEFT,
    val gradientEnd: Int? = null,
    val gradientVertical: Boolean = false,
)

/** One appearance definition for the desktop window and its settings preview. */
object OverlayAppearance {
    const val LEFT = "left"
    const val CENTER = "center"
    const val RIGHT = "right"
    const val PREF_ALIGNMENT = "overlay_alignment_v1"
    const val PREF_GRADIENT = "overlay_gradient_v1"
    const val PREF_GRADIENT_END = "overlay_gradient_end_v1"
    const val PREF_GRADIENT_VERTICAL = "overlay_gradient_vertical_v1"
    const val PREF_BACKGROUND_OPACITY = "overlay_background_opacity_v1"
    const val PREF_CORNER_RADIUS = "overlay_corner_radius_v1"
    const val PREF_WIDTH_PERCENT = "overlay_width_percent_v1"
    const val DEFAULT_GRADIENT_END = "#9FD8FF"
    const val DEFAULT_BACKGROUND_OPACITY = 55
    const val DEFAULT_CORNER_RADIUS = 18
    const val MIN_FONT_WEIGHT = 300
    const val MAX_FONT_WEIGHT = 900
    const val DEFAULT_FONT_WEIGHT = 400
    const val MIN_FONT_WEIGHT_PERCENT = 75
    const val MAX_FONT_WEIGHT_PERCENT = 225
    private val typefaces = mutableMapOf<Int, Typeface>()

    fun weightToPercent(value: Int): Int = normalizeWeight(value) * 100 / DEFAULT_FONT_WEIGHT

    fun percentToWeight(value: Int): Int = normalizeWeight(
        value.coerceIn(MIN_FONT_WEIGHT_PERCENT, MAX_FONT_WEIGHT_PERCENT) * DEFAULT_FONT_WEIGHT / 100
    )

    fun normalizeWeight(value: Int): Int =
        ((value.coerceIn(MIN_FONT_WEIGHT, MAX_FONT_WEIGHT) + 50) / 100) * 100

    fun typeface(value: Int): Typeface {
        val weight = normalizeWeight(value)
        return typefaces.getOrPut(weight) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(Typeface.SANS_SERIF, weight, false)
            } else {
                val family = when {
                    weight <= 300 -> "sans-serif-light"
                    weight <= 400 -> "sans-serif"
                    weight <= 600 -> "sans-serif-medium"
                    weight <= 700 -> "sans-serif"
                    else -> "sans-serif-black"
                }
                Typeface.create(family, if (weight == 700) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    fun alignment(value: String?): String = when (value) {
        CENTER -> CENTER
        RIGHT -> RIGHT
        else -> LEFT
    }

    fun parseColor(value: String?, fallback: Int = Color.WHITE): Int =
        runCatching { Color.parseColor(value.orEmpty()) }.getOrDefault(fallback) or (0xFF shl 24)

    fun textStyle(prefs: SharedPreferences) = OverlayTextStyle(
        alignment(prefs.getString(PREF_ALIGNMENT, LEFT)),
        if (prefs.getBoolean(PREF_GRADIENT, false)) parseColor(prefs.getString(PREF_GRADIENT_END, DEFAULT_GRADIENT_END)) else null,
        prefs.getBoolean(PREF_GRADIENT_VERTICAL, false),
    )

    fun backgroundColor(mode: String?, lyricColor: Int = Color.WHITE, opacityPercent: Int = DEFAULT_BACKGROUND_OPACITY): Int {
        val alpha = when (mode) {
            LyricsOverlayService.BACKGROUND_LOW, LyricsOverlayService.BACKGROUND_MEDIUM ->
                (opacityPercent.coerceIn(10, 90) * 255f / 100).roundToInt()
            LyricsOverlayService.BACKGROUND_HIGH -> 255
            else -> 0
        }
        val base = if (ColorUtils.calculateLuminance(lyricColor) > .35) Color.rgb(17, 19, 24) else Color.rgb(247, 248, 252)
        return ColorUtils.setAlphaComponent(base, alpha)
    }

    fun backgroundBorderColor(lyricColor: Int): Int = ColorUtils.setAlphaComponent(
        if (ColorUtils.calculateLuminance(lyricColor) > .35) Color.WHITE else Color.BLACK, 28)

    fun background(context: Context, mode: String?, lyricColor: Int = Color.WHITE,
                   opacityPercent: Int = DEFAULT_BACKGROUND_OPACITY, radiusDp: Int = DEFAULT_CORNER_RADIUS) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp.coerceIn(0, 32) * context.resources.displayMetrics.density
        val color = backgroundColor(mode, lyricColor, opacityPercent)
        setColor(color)
        if (Color.alpha(color) != 0) {
            setStroke(context.resources.displayMetrics.density.roundToInt().coerceAtLeast(1),
                backgroundBorderColor(lyricColor))
        }
    }
}
