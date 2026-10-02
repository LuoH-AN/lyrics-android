package com.luoh.music.lrc

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build

/** One appearance definition for the desktop window and its settings preview. */
object OverlayAppearance {
    const val MIN_FONT_WEIGHT = 300
    const val MAX_FONT_WEIGHT = 900
    const val DEFAULT_FONT_WEIGHT = 400
    private val typefaces = mutableMapOf<Int, Typeface>()

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

    fun backgroundColor(mode: String?): Int = when (mode) {
        LyricsOverlayService.BACKGROUND_LOW, LyricsOverlayService.BACKGROUND_MEDIUM -> Color.argb(140, 12, 12, 14)
        LyricsOverlayService.BACKGROUND_HIGH -> Color.rgb(12, 12, 14)
        else -> Color.TRANSPARENT
    }

    fun background(context: Context, mode: String?) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 22f * context.resources.displayMetrics.density
        setColor(backgroundColor(mode))
    }
}
