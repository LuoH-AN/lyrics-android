package com.luoh.music.lrc

import android.graphics.Color
import kotlin.math.min

/**
 * The complete set of colour roles the screens are allowed to name.
 *
 * Four surface levels only ever step one level at a time. The seed (the user's
 * accent) supplies hue to surfaces at a very low chroma, so a custom accent
 * tints the page without turning it into a coloured slab.
 */
class AppScheme(
    val dark: Boolean,
    val seed: Int?,
    val page: Int,
    val card: Int,
    val block: Int,
    val inset: Int,
    val floating: Int,
    val chrome: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val hairline: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val track: Int,
    val error: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val success: Int,
    val successContainer: Int,
    val onSuccessContainer: Int,
    val warning: Int,
    val warningContainer: Int,
    val onWarningContainer: Int,
    val destructive: Int,
    val modalScrim: Int,
    val alertScrim: Int
) {

    /** Divider used inside a card, one step darker than the page hairline. */
    fun divider(): Int = hairline

    companion object {
        private const val SURFACE_CHROMA = 3.0
        private const val DARK_SURFACE_CHROMA = 4.0
        private const val PAPER_CHROMA = 1.5
        private const val CONTAINER_CHROMA = 48.0

        private const val LIGHT_PRIMARY = 0x18181B
        private const val DARK_PRIMARY = 0xF5F5F7

        fun build(seed: Int?, dark: Boolean): AppScheme {
            val page = surface(seed, if (dark) 15.5 else 96.0, dark)
            val card = surface(seed, if (dark) 22.0 else 99.0, dark)
            val block = surface(seed, if (dark) 26.0 else 93.5, dark)
            val inset = surface(seed, if (dark) 31.0 else 90.5, dark)
            val onSurface = surface(seed, if (dark) 97.0 else 20.0, dark, 1.0)
            val onSurfaceVariant = surface(seed, if (dark) 73.0 else 52.8, dark, 2.0)
            val outline = surface(seed, if (dark) 33.5 else 90.5, dark, 2.5)
            val outlineVariant = surface(seed, if (dark) 31.0 else 93.5, dark, 2.0)

            val primary = primaryOf(seed, dark)
            val onPrimary = AppColor.contrastOn(primary)
            val containerHue = seed?.let(AppColor::hue) ?: 0.0
            val containerChroma = min(seed?.let(AppColor::chroma) ?: 0.0, CONTAINER_CHROMA)
            val primaryContainer = AppColor.fromTone(
                if (dark) 30.0 else 90.0,
                containerChroma,
                containerHue
            )
            val onPrimaryContainer = AppColor.fromTone(
                if (dark) 90.0 else 30.0,
                min(containerChroma, 24.0),
                containerHue
            )

            val error = if (dark) 0xFFFF6B6B.toInt() else 0xFFD92D20.toInt()
            val errorContainer = tonal(0xFFD92D20.toInt(), if (dark) 30.0 else 90.0, 40.0)
            val onErrorContainer = tonal(0xFFD92D20.toInt(), if (dark) 90.0 else 30.0, 24.0)

            val success = if (dark) 0xFF30D158.toInt() else 0xFF248A3D.toInt()
            val successContainer = tonal(success, if (dark) 30.0 else 90.0, 32.0)
            val onSuccessContainer = tonal(success, if (dark) 90.0 else 30.0, 20.0)

            val warning = if (dark) 0xFFFF9F0A.toInt() else 0xFFB25000.toInt()
            val warningContainer = tonal(warning, if (dark) 30.0 else 90.0, 36.0)
            val onWarningContainer = tonal(warning, if (dark) 90.0 else 30.0, 22.0)

            return AppScheme(
                dark = dark,
                seed = seed,
                page = page,
                card = card,
                block = block,
                inset = inset,
                floating = if (dark) block else card,
                chrome = if (dark) surface(seed, 18.0, true) else inset,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                outlineVariant = outlineVariant,
                hairline = AppColor.alpha(onSurface, 0.14f),
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondaryContainer = block,
                onSecondaryContainer = onSurface,
                track = inset,
                error = error,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer,
                success = success,
                successContainer = successContainer,
                onSuccessContainer = onSuccessContainer,
                warning = warning,
                warningContainer = warningContainer,
                onWarningContainer = onWarningContainer,
                destructive = if (dark) 0xFFFF4245.toInt() else 0xFFFF383C.toInt(),
                modalScrim = Color.argb(82, 0, 0, 0),
                alertScrim = Color.argb(if (dark) 122 else 51, 0, 0, 0)
            )
        }

        private fun primaryOf(seed: Int?, dark: Boolean): Int {
            if (seed == null) return if (dark) DARK_PRIMARY.toInt() else LIGHT_PRIMARY.toInt()
            val tone = AppColor.tone(seed)
            return if (dark) {
                AppColor.withTone(seed, tone.coerceIn(50.0, 75.0))
            } else {
                AppColor.withTone(seed, tone.coerceIn(35.0, 60.0))
            }
        }

        private fun surface(seed: Int?, tone: Double, dark: Boolean, chromaCap: Double = 0.0): Int {
            if (seed == null) return AppColor.fromTone(tone, 0.0, 0.0)
            val cap = if (chromaCap > 0.0) chromaCap
            else if (dark) DARK_SURFACE_CHROMA else SURFACE_CHROMA
            val chroma = min(AppColor.chroma(seed), min(cap, if (tone >= 96.0) PAPER_CHROMA else cap))
            return AppColor.fromTone(tone, chroma, AppColor.hue(seed))
        }

        private fun tonal(source: Int, tone: Double, chromaCap: Double): Int =
            AppColor.fromTone(tone, min(AppColor.chroma(source), chromaCap), AppColor.hue(source))
    }
}
