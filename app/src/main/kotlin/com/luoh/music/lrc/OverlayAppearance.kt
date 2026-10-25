package com.luoh.music.lrc

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import androidx.core.content.res.ResourcesCompat

/** One appearance definition for the desktop window and its settings preview. */
object OverlayAppearance {
    const val MIN_FONT_WEIGHT = 300
    const val MAX_FONT_WEIGHT = 900
    const val DEFAULT_FONT_WEIGHT = 400
    const val MIN_FONT_WEIGHT_PERCENT = 75
    const val MAX_FONT_WEIGHT_PERCENT = 225

    /** 悬浮窗可用的字体族；仓库随包附带 JetBrains Mono 与 MiOutfit。 */
    const val FONT_FAMILY_SYSTEM = "system"
    const val FONT_FAMILY_MONO = "mono"
    const val FONT_FAMILY_MIOUTFIT = "mioutfit"
    val FONT_FAMILIES = listOf(
        FONT_FAMILY_SYSTEM to "系统默认",
        FONT_FAMILY_MONO to "JetBrains Mono",
        FONT_FAMILY_MIOUTFIT to "MiOutfit"
    )

    fun normalizeFontFamily(value: String?): String =
        if (value == FONT_FAMILY_MONO || value == FONT_FAMILY_MIOUTFIT) value else FONT_FAMILY_SYSTEM

    private val typefaces = mutableMapOf<String, Typeface>()
    private var baseMioutfit: Typeface? = null
    private var baseMonoRegular: Typeface? = null
    private var baseMonoBold: Typeface? = null

    fun weightToPercent(value: Int): Int = normalizeWeight(value) * 100 / DEFAULT_FONT_WEIGHT

    fun percentToWeight(value: Int): Int = normalizeWeight(
        value.coerceIn(MIN_FONT_WEIGHT_PERCENT, MAX_FONT_WEIGHT_PERCENT) * DEFAULT_FONT_WEIGHT / 100
    )

    fun normalizeWeight(value: Int): Int =
        ((value.coerceIn(MIN_FONT_WEIGHT, MAX_FONT_WEIGHT) + 50) / 100) * 100

    fun typeface(value: Int): Typeface = typeface(null, FONT_FAMILY_SYSTEM, value)

    /**
     * 解析字体族 + 字重的最终 Typeface。bundled 字体经 ResourcesCompat 加载后按
     * key 缓存；MiOutfit 为可变字体，API 28+ 上用 Typeface.create 派生指定字重。
     */
    @Synchronized
    fun typeface(context: Context?, family: String, weight: Int): Typeface {
        val normalizedWeight = normalizeWeight(weight)
        val normalizedFamily = normalizeFontFamily(family)
        if (normalizedFamily == FONT_FAMILY_SYSTEM) return systemTypeface(normalizedWeight)
        val ctx = context ?: return systemTypeface(normalizedWeight)
        val key = "$normalizedFamily:$normalizedWeight"
        typefaces[key]?.let { return it }
        val resolved = when (normalizedFamily) {
            FONT_FAMILY_MONO -> {
                val base = baseMonoRegular ?: ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_regular)
                    ?.also { baseMonoRegular = it }
                val bold = baseMonoBold ?: ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_bold)
                    ?.also { baseMonoBold = it }
                when {
                    base == null && bold == null -> return systemTypeface(normalizedWeight)
                    normalizedWeight >= 600 && bold != null -> bold
                    base != null -> base
                    else -> bold!!
                }
            }
            else -> {
                val base = baseMioutfit ?: ResourcesCompat.getFont(ctx, R.font.mioutfit_variable)
                    ?.also { baseMioutfit = it } ?: return systemTypeface(normalizedWeight)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Typeface.create(base, normalizedWeight, false)
                } else base
            }
        }
        typefaces[key] = resolved
        return resolved
    }

    private fun systemTypeface(weight: Int): Typeface =
        typefaces.getOrPut("system:$weight") {
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
