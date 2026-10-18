package com.luoh.music.lrc

import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.widget.TextView

/**
 * Apple's Dynamic Type scale at the default size. Every text role in the app
 * comes from here; screens never name a raw sp value.
 *
 * Two weights only: regular, and the role's emphasized weight. Letter spacing
 * stays at zero, as it does in the platform this vocabulary is borrowed from.
 */
data class TextToken(
    val sizeSp: Float,
    val leadingSp: Float,
    val weight: Int,
    val emphasizedWeight: Int
)

object AppTextStyle {
    val display = TextToken(40f, 44f, 400, 700)
    val largeTitle = TextToken(34f, 41f, 400, 700)
    val title1 = TextToken(28f, 34f, 400, 700)
    val pageTitle = TextToken(24f, 30f, 600, 700)
    val title2 = TextToken(22f, 28f, 400, 700)
    val title3 = TextToken(20f, 25f, 400, 600)
    val headline = TextToken(17f, 22f, 600, 600)
    val body = TextToken(17f, 22f, 400, 600)
    val callout = TextToken(16f, 21f, 400, 600)
    val subheadline = TextToken(15f, 20f, 400, 600)
    val footnote = TextToken(13f, 18f, 400, 600)
    val caption1 = TextToken(12f, 16f, 400, 600)
    val caption2 = TextToken(11f, 13f, 400, 600)
}

/** Applies one scale role, including its leading, to a text view. */
fun TextView.applyTextStyle(
    token: TextToken,
    emphasized: Boolean = false,
    color: Int? = null
) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, token.sizeSp)
    typeface = appTypeface(if (emphasized) token.emphasizedWeight else token.weight)
    includeFontPadding = false
    letterSpacing = 0f
    val leadingExtraDp = (token.leadingSp - token.sizeSp).coerceAtLeast(0f)
    setLineSpacing(
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            leadingExtraDp,
            resources.displayMetrics
        ),
        1f
    )
    if (color != null) setTextColor(color)
}

/** The closest platform face for one of the two weights. */
fun appTypeface(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        when {
            weight >= 700 -> Typeface.create("sans-serif", Typeface.BOLD)
            weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.SANS_SERIF
        }
    }

/** Maps historic call sites that passed a size to the nearest scale role. */
fun appTextToken(sizeSp: Float): TextToken = when {
    sizeSp >= 32f -> AppTextStyle.largeTitle
    sizeSp >= 24f -> AppTextStyle.pageTitle
    sizeSp >= 19f -> AppTextStyle.title3
    sizeSp >= 16f -> AppTextStyle.body
    sizeSp >= 14f -> AppTextStyle.subheadline
    sizeSp >= 13f -> AppTextStyle.footnote
    sizeSp >= 12f -> AppTextStyle.caption1
    else -> AppTextStyle.caption2
}
