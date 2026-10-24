package com.luoh.music.lrc

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.materialkolor.hct.Hct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class HomeAlbumPalette(val primary: Int, val secondary: Int)

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
        HomeAlbumPalette(primary, secondary)
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
    return Color(Hct.from(hct.hue, min(hct.chroma, maxChroma), tone).toInt())
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
        if (colors == null) scheme.background else homeAlbumTone(primary, if (dark) 10.0 else 96.0, 14.0),
        tween(900), label = "albumBackgroundBase",
    )
    val first by animateColorAsState(
        homeAlbumTone(primary, if (dark) 26.0 else 85.0, 46.0),
        tween(900), label = "albumBackgroundPrimary",
    )
    val second by animateColorAsState(
        homeAlbumTone(secondary, if (dark) 22.0 else 89.0, 38.0),
        tween(900), label = "albumBackgroundSecondary",
    )
    val motion = remember { Animatable(0f) }
    val animate = state.presentationActive && state.playing && LocalAgoraMotionPolicy.current.allowContinuousMotion
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var target = if (motion.value < .5f) 1f else 0f
        while (true) {
            motion.animateTo(target, tween(
                durationMillis = (18_000 * abs(target - motion.value)).toInt().coerceAtLeast(1),
                easing = FastOutSlowInEasing,
            ))
            target = 1f - target
        }
    }
    // Only this drawing layer reads the continuous animation clock.
    Canvas(modifier) {
        val phase = motion.value
        val radius = max(size.width, size.height) * .9f
        drawRect(base)
        drawRect(Brush.radialGradient(
            listOf(first.copy(alpha = .8f), first.copy(alpha = 0f)),
            center = Offset(size.width * (.1f + phase * .65f), size.height * (.15f + phase * .2f)),
            radius = radius,
        ))
        drawRect(Brush.radialGradient(
            listOf(second.copy(alpha = .7f), second.copy(alpha = 0f)),
            center = Offset(size.width * (.95f - phase * .6f), size.height * (.85f - phase * .2f)),
            radius = radius * .85f,
        ))
    }
}
