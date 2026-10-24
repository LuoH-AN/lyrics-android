package com.luoh.music.lrc

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnPreDraw

/** A light color-change fade. No full-screen bitmap or synchronous software draw. */
class ThemeTransition(
    private val root: ViewGroup,
    private val animationsEnabled: () -> Boolean = { ValueAnimator.areAnimatorsEnabled() }
) {
    private var animation: ValueAnimator? = null
    private var pendingDraw: OneShotPreDrawListener? = null
    private var generation = 0
    private var disposed = false
    private var prepared = false
    val isRunning: Boolean get() = prepared || animation != null

    private val attachmentListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = clear()
    }

    init { root.addOnAttachStateChangeListener(attachmentListener) }

    fun capture() {
        if (disposed) return
        clear()
        prepared = animationsEnabled() && root.isAttachedToWindow && root.windowVisibility == View.VISIBLE
    }

    fun finish(afterLayout: () -> Unit = {}) {
        if (disposed) return
        pendingDraw?.removeListener()
        val token = generation
        pendingDraw = root.doOnPreDraw {
            pendingDraw = null
            if (disposed || token != generation) return@doOnPreDraw
            afterLayout()
            if (disposed || token != generation) return@doOnPreDraw
            val animate = prepared && animationsEnabled()
            prepared = false
            if (!animate) { root.alpha = 1f; return@doOnPreDraw }
            animation = ValueAnimator.ofFloat(.65f, 1f).apply {
                duration = 200L
                addUpdateListener { root.alpha = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animator: Animator) {
                        if (token == generation) { animation = null; root.alpha = 1f }
                    }
                })
                start()
            }
        }
        root.invalidate()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        clear()
        root.removeOnAttachStateChangeListener(attachmentListener)
    }

    private fun clear() {
        generation++
        pendingDraw?.removeListener()
        pendingDraw = null
        animation?.apply { removeAllUpdateListeners(); removeAllListeners(); cancel() }
        animation = null
        prepared = false
        root.alpha = 1f
    }
}
