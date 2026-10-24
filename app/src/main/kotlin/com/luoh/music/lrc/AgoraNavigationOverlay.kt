package com.luoh.music.lrc

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnPreDraw
import kotlin.math.roundToInt

/** Port of Agora SettingsOverlayHost.kt: retained home, independent scrim, asymmetric enter/exit. */
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
    private val offset = AgoraMotionValue(0f, ::render)
    private val pageAlpha = AgoraMotionValue(1f, ::render)
    private val scale = AgoraMotionValue(1f, ::render)
    private val scrimAlpha = AgoraMotionValue(0f, ::render)
    private var generation = 0
    private var pendingDraw: OneShotPreDrawListener? = null
    var closing = false
        private set

    init { addView(scrim, LayoutParams(-1, -1)); addView(surface, LayoutParams(-1, -1)); clipChildren = true }

    fun enter(animate: Boolean = true) {
        cancel()
        closing = false
        surface.consumesInput = false
        val spatial = animate && allowAgoraSpatialMotion(context)
        offset.snap(if (spatial) .25f else 0f)
        scale.snap(if (spatial) .92f else 1f)
        pageAlpha.snap(if (animate) 0f else 1f)
        scrimAlpha.snap(if (animate) 0f else .45f)
        if (!animate) return
        val token = generation
        pendingDraw = doOnPreDraw {
            pendingDraw = null
            if (token != generation) return@doOnPreDraw
            if (spatial) { offset.spring(0f) {}; scale.spring(1f) {} }
            pageAlpha.tween(1f) {}
            scrimAlpha.tween(.45f, delay = if (spatial) 50L else 0L) {}
        }
    }

    fun exit(complete: () -> Unit) {
        if (closing) return
        cancel()
        closing = true
        surface.consumesInput = true
        val token = generation
        val spatial = allowAgoraSpatialMotion(context)
        var remaining = if (spatial) 4 else 2
        val done = { if (token == generation && --remaining == 0) complete(); Unit }
        val duration = if (spatial) 400L else 300L
        if (spatial) { offset.tween(1f, duration, complete = done); scale.tween(.94f, duration, complete = done) }
        else { offset.snap(0f); scale.snap(1f) }
        pageAlpha.tween(0f, duration, complete = done)
        scrimAlpha.tween(0f, duration, complete = done)
    }
    private fun render() {
        surface.translationX = (width * offset.value).roundToInt().toFloat()
        surface.alpha = pageAlpha.value.coerceIn(0f, 1f)
        surface.scaleX = scale.value
        surface.scaleY = scale.value
        scrim.alpha = scrimAlpha.value.coerceIn(0f, .45f)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); render() }
    private fun cancel() {
        generation++
        pendingDraw?.removeListener(); pendingDraw = null
        offset.cancel(); scale.cancel(); pageAlpha.cancel(); scrimAlpha.cancel()
    }
    fun dispose() { cancel(); host.dispose() }
}
