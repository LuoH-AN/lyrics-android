package com.luoh.music.lrc

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Perceptual colour math for the app's seed-based palette.
 *
 * FlClash derives every role from a seed with Material's HCT. HCT lives in
 * Material's restricted package, so this uses OKLab/OKLCH instead: the same
 * idea — a lightness axis ("tone") whose steps read evenly — with tones
 * expressed as 0..100. Surfaces keep the seed's hue with a hard chroma cap,
 * which is what stops a yellow seed from turning the page yellow.
 */
object AppColor {
    /** Perceived lightness, 0..100. */
    fun tone(color: Int): Double = oklch(color)[0] * 100.0

    /** Perceptual chroma of the colour. */
    fun chroma(color: Int): Double = oklch(color)[1]

    /** Hue in degrees, 0..360. */
    fun hue(color: Int): Double = oklch(color)[2]

    /** Moves a colour along the lightness axis, keeping hue and chroma. */
    fun withTone(color: Int, tone: Double): Int {
        val values = oklch(color)
        return fromOklch(tone.coerceIn(0.0, 100.0) / 100.0, values[1], values[2])
    }

    /** Moves a colour along the lightness axis with a chroma cap. */
    fun withTone(color: Int, tone: Double, chromaCap: Double): Int {
        val values = oklch(color)
        return fromOklch(tone.coerceIn(0.0, 100.0) / 100.0, values[1].coerceAtMost(chromaCap), values[2])
    }

    /** Clamps chroma without changing lightness or hue. */
    fun withChromaCap(color: Int, cap: Double): Int {
        val values = oklch(color)
        return fromOklch(values[0], values[1].coerceAtMost(cap), values[2])
    }

    /** Builds a colour from a tone (0..100), chroma and hue in degrees. */
    fun fromTone(tone: Double, chroma: Double, hueDegrees: Double): Int =
        fromOklch(tone.coerceIn(0.0, 100.0) / 100.0, chroma.coerceAtLeast(0.0), hueDegrees)

    /** Multiplies the colour's alpha, leaving RGB untouched. */
    fun alpha(color: Int, fraction: Float): Int {
        val value = (Color.alpha(color) * fraction).roundToInt().coerceIn(0, 255)
        return ColorUtils.setAlphaComponent(color, value)
    }

    /** The readable foreground for a filled background. */
    fun contrastOn(background: Int): Int =
        if (ColorUtils.calculateContrast(Color.WHITE, background) >= 4.5) Color.WHITE else Color.BLACK

    fun isDark(color: Int): Boolean = ColorUtils.calculateLuminance(color) < 0.5

    private fun oklch(color: Int): DoubleArray {
        val r = linear(Color.red(color) / 255.0)
        val g = linear(Color.green(color) / 255.0)
        val b = linear(Color.blue(color) / 255.0)

        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

        val lRoot = cbrt(l)
        val mRoot = cbrt(m)
        val sRoot = cbrt(s)

        val lightness = 0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot
        val a = 1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot
        val bAxis = 0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot

        val chroma = sqrt(a * a + bAxis * bAxis)
        var hue = atan2(bAxis, a) * 180.0 / PI
        if (hue < 0) hue += 360.0
        return doubleArrayOf(lightness, chroma, hue)
    }

    private fun fromOklch(lightness: Double, chroma: Double, hueDegrees: Double): Int {
        var currentChroma = chroma.coerceAtLeast(0.0)
        val l = lightness.coerceIn(0.0, 1.0)
        val radians = hueDegrees * PI / 180.0
        repeat(30) {
            val rgb = rawRgb(l, currentChroma, radians)
            if (rgb.all { it >= -0.0001 && it <= 1.0001 }) return pack(rgb)
            currentChroma *= 0.9
        }
        return pack(rawRgb(l, 0.0, radians).map { it.coerceIn(0.0, 1.0) }.toDoubleArray())
    }

    private fun rawRgb(lightness: Double, chroma: Double, hue: Double): DoubleArray {
        val a = chroma * cos(hue)
        val b = chroma * sin(hue)

        val lRoot = lightness + 0.3963377774 * a + 0.2158037573 * b
        val mRoot = lightness - 0.1055613458 * a - 0.0638541728 * b
        val sRoot = lightness - 0.0894841775 * a - 1.2914855480 * b

        val l = lRoot * lRoot * lRoot
        val m = mRoot * mRoot * mRoot
        val s = sRoot * sRoot * sRoot

        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        )
    }

    private fun pack(rgb: DoubleArray): Int = Color.rgb(
        channel(rgb[0]),
        channel(rgb[1]),
        channel(rgb[2])
    )

    private fun channel(linearValue: Double): Int {
        val value = linearValue.coerceIn(0.0, 1.0)
        val encoded = if (value <= 0.0031308) 12.92 * value
        else 1.055 * value.pow(1.0 / 2.4) - 0.055
        return (encoded * 255.0).roundToInt().coerceIn(0, 255)
    }

    private fun linear(value: Double): Double =
        if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}
