package com.luoh.music.lrc

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A small, preblurred copy also works on Android 8–11, where Compose blur is unavailable. */
internal fun prepareHomeAlbumBackground(cover: Bitmap?): Bitmap? {
    if (cover == null || cover.isRecycled) return null
    var thumbnail: Bitmap? = null
    var software: Bitmap? = null
    return try {
        val ratio = min(1f, 160f / max(cover.width, cover.height))
        val width = (cover.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (cover.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(cover, width, height, true)
        thumbnail = scaled
        val readable = if (scaled.config == Bitmap.Config.HARDWARE) {
            scaled.copy(Bitmap.Config.ARGB_8888, false)?.also { software = it } ?: return null
        } else scaled
        val pixels = IntArray(width * height)
        readable.getPixels(pixels, 0, width, 0, 0, width, height)
        val scratch = IntArray(pixels.size)
        val radius = (min(width, height) / 16).coerceIn(1, 10)
        // Three box passes approximate a Gaussian without filtering a full-screen bitmap every frame.
        repeat(3) {
            blurAlbumAxis(pixels, scratch, width, height, radius, horizontal = true)
            blurAlbumAxis(scratch, pixels, width, height, radius, horizontal = false)
        }
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } finally {
        software?.takeIf { it !== cover }?.recycle()
        thumbnail?.takeIf { it !== cover && it !== software }?.recycle()
    }
}

private fun blurAlbumAxis(source: IntArray, target: IntArray, width: Int, height: Int,
    radius: Int, horizontal: Boolean) {
    val rows = if (horizontal) height else width
    val length = if (horizontal) width else height
    val stride = if (horizontal) 1 else width
    val count = radius * 2 + 1
    for (row in 0 until rows) {
        val base = if (horizontal) row * width else row
        fun pixel(index: Int) = source[base + index.coerceIn(0, length - 1) * stride]
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (index in -radius..radius) {
            val color = pixel(index)
            alpha += color ushr 24
            red += color ushr 16 and 255
            green += color ushr 8 and 255
            blue += color and 255
        }
        for (index in 0 until length) {
            target[base + index * stride] = ((alpha / count) shl 24) or ((red / count) shl 16) or
                ((green / count) shl 8) or (blue / count)
            val outgoing = pixel(index - radius)
            val incoming = pixel(index + radius + 1)
            alpha += (incoming ushr 24) - (outgoing ushr 24)
            red += (incoming ushr 16 and 255) - (outgoing ushr 16 and 255)
            green += (incoming ushr 8 and 255) - (outgoing ushr 8 and 255)
            blue += (incoming and 255) - (outgoing and 255)
        }
    }
}

@Composable
internal fun HomeAlbumBackground(state: HomePresentationState, modifier: Modifier = Modifier) {
    val cover = state.snapshot.cover
    val hasCover = cover != null && !cover.isRecycled
    val background by produceState<Bitmap?>(null, cover) {
        value = withContext(Dispatchers.Default) { prepareHomeAlbumBackground(cover) }
    }
    val motion = remember { Animatable(0f) }
    val animate = hasCover && state.presentationActive && state.playing &&
        LocalAgoraMotionPolicy.current.allowContinuousMotion
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
    val allowFade = hasCover && state.presentationActive && LocalAgoraMotionPolicy.current.allowSpatialTransitions
    Box(modifier.clipToBounds().background(if (hasCover) Color(0xFF101014) else MaterialTheme.colorScheme.background)) {
        Crossfade(if (hasCover) background else null, animationSpec = tween(if (allowFade) 700 else 0),
            modifier = Modifier.fillMaxSize(), label = "albumBackgroundCover") { bitmap ->
            if (bitmap != null && !bitmap.isRecycled) {
                Image(remember(bitmap) { bitmap.asImageBitmap() }, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val phase = motion.value
                        scaleX = 1.1f + .025f * sin(phase)
                        scaleY = scaleX
                        translationX = size.width * .015f * cos(phase)
                        translationY = size.height * .015f * sin(phase)
                    })
            }
        }
        if (hasCover) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = .56f),
            .5f to Color.Black.copy(alpha = .64f),
            1f to Color.Black.copy(alpha = .78f),
        )))
    }
}
