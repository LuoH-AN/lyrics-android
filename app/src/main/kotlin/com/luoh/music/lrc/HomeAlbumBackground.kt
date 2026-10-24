package com.luoh.music.lrc

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.scale
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.materialkolor.hct.Hct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

internal data class HomeAlbumPalette(val primary: Int, val secondary: Int,
    val tertiary: Int = primary, val fourth: Int = secondary)

/** Read a small software thumbnail once per cover, never on the UI thread. */
internal fun extractHomeAlbumPalette(cover: Bitmap?): HomeAlbumPalette? {
    if (cover == null || cover.isRecycled) return null
    var thumbnail: Bitmap? = null
    var software: Bitmap? = null
    return try {
        val scaled = Bitmap.createScaledBitmap(cover, 48, 48, true)
        thumbnail = scaled
        val readable = if (scaled.config == Bitmap.Config.HARDWARE) {
            scaled.copy(Bitmap.Config.ARGB_8888, false)?.also { software = it } ?: return null
        } else scaled
        val pixels = IntArray(readable.width * readable.height)
        readable.getPixels(pixels, 0, readable.width, 0, 0, readable.width, readable.height)
        // Hue buckets preserve actual cover colors; transparent pixels and white borders
        // must not wash out a colorful album. Neutral artwork keeps its neutral palette.
        val counts = IntArray(13)
        val reds = LongArray(13)
        val greens = LongArray(13)
        val blues = LongArray(13)
        val hsv = FloatArray(3)
        for (pixel in pixels) {
            if (AndroidColor.alpha(pixel) < 128) continue
            AndroidColor.colorToHSV(pixel, hsv)
            val bucket = if (hsv[1] < .12f || hsv[2] < .08f) 12 else (hsv[0] / 30f).toInt().coerceIn(0, 11)
            counts[bucket]++
            reds[bucket] += AndroidColor.red(pixel)
            greens[bucket] += AndroidColor.green(pixel)
            blues[bucket] += AndroidColor.blue(pixel)
        }
        val total = counts.sum()
        if (total == 0) return null
        val chromatic = (0..11).filter { counts[it] >= max(1, total / 20) }.sortedByDescending { counts[it] }
        val dominant = chromatic.firstOrNull() ?: counts.indices.maxByOrNull { counts[it] } ?: return null
        fun color(bucket: Int): Int = AndroidColor.rgb(
            (reds[bucket] / counts[bucket]).toInt(),
            (greens[bucket] / counts[bucket]).toInt(),
            (blues[bucket] / counts[bucket]).toInt(),
        )
        val primary = color(dominant)
        val secondary = chromatic.firstOrNull { bucket ->
            val distance = abs(bucket - dominant)
            min(distance, 12 - distance) >= 2
        }?.let(::color) ?: primary
        HomeAlbumPalette(primary, secondary,
            chromatic.getOrNull(2)?.let(::color) ?: secondary,
            chromatic.getOrNull(3)?.let(::color) ?: primary)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } finally {
        software?.takeIf { it !== cover }?.recycle()
        thumbnail?.takeIf { it !== cover && it !== software }?.recycle()
    }
}

internal fun homeAlbumTone(argb: Int, tone: Double, maxChroma: Double): Color {
    val hct = Hct.fromInt(argb)
    val chroma = if (hct.chroma < 4.0) 0.0 else min(hct.chroma, maxChroma)
    return Color(Hct.from(hct.hue, chroma, tone).toInt())
}

@Composable
internal fun HomeAlbumBackground(state: HomePresentationState, modifier: Modifier = Modifier) {
    val cover = state.snapshot.cover
    val palette by produceState<HomeAlbumPalette?>(null, cover) {
        value = withContext(Dispatchers.Default) { extractHomeAlbumPalette(cover) }
    }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < .5f
    val colors = palette
    val primary = colors?.primary ?: scheme.primary.toArgb()
    val secondary = colors?.secondary ?: scheme.tertiary.toArgb()
    val base by animateColorAsState(
        if (colors == null) scheme.background else homeAlbumTone(primary, if (dark) 14.0 else 90.0, 24.0),
        tween(900), label = "albumBackgroundBase",
    )
    val first by animateColorAsState(
        homeAlbumTone(primary, if (dark) 34.0 else 70.0, 64.0),
        tween(900), label = "albumBackgroundPrimary",
    )
    val second by animateColorAsState(
        homeAlbumTone(secondary, if (dark) 28.0 else 78.0, 60.0),
        tween(900), label = "albumBackgroundSecondary",
    )
    val third by animateColorAsState(
        homeAlbumTone(colors?.tertiary ?: secondary, if (dark) 22.0 else 82.0, 52.0),
        tween(900), label = "albumBackgroundThird",
    )
    val fourth by animateColorAsState(
        homeAlbumTone(colors?.fourth ?: primary, if (dark) 30.0 else 74.0, 58.0),
        tween(900), label = "albumBackgroundFourth",
    )
    val motion = remember { Animatable(0f) }
    val animate = state.presentationActive && state.playing && LocalAgoraMotionPolicy.current.allowContinuousMotion
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        val cycle = (2 * PI).toFloat()
        while (true) {
            motion.animateTo(cycle, tween(
                durationMillis = (40_000 * (1f - motion.value / cycle)).toInt().coerceAtLeast(1),
                easing = LinearEasing,
            ))
            motion.snapTo(0f)
        }
    }
    // Only this drawing layer reads the continuous animation clock.
    Canvas(modifier) {
        val phase = motion.value
        drawRect(base)
        // Broad, overlapping elliptical color fields deform and orbit independently.
        // The gradients provide the blur without full-resolution bitmap filters.
        fun field(color: Color, x: Float, y: Float, radius: Float, stretch: Float, opacity: Float) {
            val center = Offset(size.width * x, size.height * y)
            scale(1f, stretch, pivot = center) {
                drawCircle(Brush.radialGradient(
                    0f to color.copy(alpha = opacity), .35f to color.copy(alpha = opacity * .8f),
                    1f to color.copy(alpha = 0f), center = center, radius = radius,
                ), radius, center)
            }
        }
        val radius = max(size.width, size.height) * .65f
        field(first, .28f + .28f * sin(phase), .25f + .18f * cos(phase), radius, 1.2f + .15f * sin(phase), .94f)
        field(second, .75f + .24f * cos(phase), .65f + .23f * sin(phase), radius * .9f, .9f + .15f * cos(phase), .9f)
        field(third, .2f + .28f * cos(phase * 2f), .8f + .15f * sin(phase), radius * .75f, 1.3f, .8f)
        field(fourth, .65f + .25f * sin(phase), .15f + .28f * cos(phase * 2f), radius * .75f, .85f, .82f)
        // A uniform veil keeps lyrics readable while preserving cover saturation.
        drawRect(scheme.background.copy(alpha = if (dark) .12f else .1f))
    }
}
