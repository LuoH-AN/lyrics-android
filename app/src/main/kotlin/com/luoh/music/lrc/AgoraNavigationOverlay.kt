package com.luoh.music.lrc

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** SettingsOverlayHost.kt motion, using the original Compose engine over retained native content. */
internal class AgoraNavigationOverlay(context: Context, onDismiss: () -> Unit) : FrameLayout(context) {
    val host = AgoraPageHost(context)
    private val scrim = View(context).apply {
        setBackgroundColor(Color.BLACK)
        alpha = 0f
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setOnClickListener { onDismiss() }
    }
    private val surface = AgoraPageSurface(context).apply { addView(host, LayoutParams(-1, -1)) }
    private val offset = Animatable(0f)
    private val pageAlpha = Animatable(1f)
    private val scale = Animatable(1f)
    private val scrimAlpha = Animatable(0f)
    private val runtime = AgoraTransitionRuntime(context)
    var closing = false
        private set

    init { addView(scrim, LayoutParams(-1, -1)); addView(surface, LayoutParams(-1, -1)); clipChildren = true }

    fun enter(animate: Boolean = true) {
        closing = false
        surface.consumesInput = false
        val spatial = animate && allowAgoraSpatialMotion(context)
        runtime.start {
            offset.snapTo(if (spatial) .25f else 0f)
            scale.snapTo(if (spatial) .92f else 1f)
            pageAlpha.snapTo(if (animate) 0f else 1f)
            scrimAlpha.snapTo(if (animate) 0f else .45f)
            render()
            if (!animate) return@start
            awaitAgoraDraw()
            if (spatial) {
                listOf(
                    launch { animateAgora(scrimAlpha, .45f, tween(300, delayMillis = 50), ::render) },
                    launch { animateAgora(offset, 0f, agoraPageSpring(), ::render) },
                    launch { animateAgora(pageAlpha, 1f, tween(300), ::render) },
                    launch { animateAgora(scale, 1f, agoraPageSpring(), ::render) }
                ).joinAll()
            } else {
                listOf(
                    launch { animateAgora(scrimAlpha, .45f, tween(300), ::render) },
                    launch { animateAgora(pageAlpha, 1f, tween(300), ::render) }
                ).joinAll()
            }
        }
    }

    fun exit(complete: () -> Unit) {
        if (closing) return
        closing = true
        surface.consumesInput = true
        val spatial = allowAgoraSpatialMotion(context)
        runtime.start {
            if (spatial) {
                listOf(
                    launch { animateAgora(scrimAlpha, 0f, tween(400, easing = FastOutSlowInEasing), ::render) },
                    launch { animateAgora(offset, 1f, tween(400, easing = FastOutSlowInEasing), ::render) },
                    launch { animateAgora(pageAlpha, 0f, tween(400, easing = FastOutSlowInEasing), ::render) },
                    launch { animateAgora(scale, .94f, tween(400, easing = FastOutSlowInEasing), ::render) }
                ).joinAll()
            } else {
                offset.snapTo(0f); scale.snapTo(1f)
                render()
                listOf(
                    launch { animateAgora(scrimAlpha, 0f, tween(300), ::render) },
                    launch { animateAgora(pageAlpha, 0f, tween(300), ::render) }
                ).joinAll()
            }
            complete()
        }
    }
    private fun render() {
        surface.translationX = (width * offset.value).roundToInt().toFloat()
        surface.alpha = pageAlpha.value.coerceIn(0f, 1f)
        surface.scaleX = scale.value
        surface.scaleY = scale.value
        scrim.alpha = scrimAlpha.value.coerceIn(0f, .45f)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); render() }
    fun dispose() { runtime.dispose(); host.dispose() }
}
