package com.luoh.music.lrc

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnPreDraw
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.roundToInt

internal fun allowAgoraSpatialMotion(context: Context): Boolean = listOf(
    Settings.Global.ANIMATOR_DURATION_SCALE,
    Settings.Global.TRANSITION_ANIMATION_SCALE,
    Settings.Global.WINDOW_ANIMATION_SCALE
).none { Settings.Global.getFloat(context.contentResolver, it, 1f) == 0f }

/** Real interruptible scalar motion; alpha is clamped only when drawing, like Compose Animatable. */
internal class AgoraMotionValue(initial: Float, private val render: () -> Unit) {
    var value = initial
        set(value) { field = value; render() }
    var velocity = 0f
    private var cancelAnimation: (() -> Unit)? = null

    fun cancel() { cancelAnimation?.invoke(); cancelAnimation = null }
    fun snap(value: Float) { cancel(); velocity = 0f; this.value = value }

    fun spring(target: Float, complete: () -> Unit) {
        cancel()
        val property = object : FloatPropertyCompat<AgoraMotionValue>("agoraProgress") {
            override fun getValue(value: AgoraMotionValue) = value.value
            override fun setValue(value: AgoraMotionValue, progress: Float) { value.value = progress }
        }
        val animation = SpringAnimation(this, property).apply {
            spring = SpringForce(target).apply { dampingRatio = .5f; stiffness = 200f }
            // DynamicAnimation multiplies minimumVisibleChange by .75 when choosing its threshold.
            setMinimumVisibleChange(.001f / .75f)
            setStartVelocity(velocity)
            addUpdateListener { _, _, speed -> velocity = speed }
            addEndListener { _, canceled, _, _ -> if (!canceled) { velocity = 0f; complete() } }
        }
        cancelAnimation = { animation.cancel() }
        animation.start()
    }

    fun tween(target: Float, duration: Long = 300L, delay: Long = 0L, complete: () -> Unit) {
        cancel()
        var previousTime = SystemClock.uptimeMillis()
        val animation = ValueAnimator.ofFloat(value, target).apply {
            this.duration = duration
            startDelay = delay
            interpolator = PathInterpolator(.4f, 0f, .2f, 1f) // Compose tween default: FastOutSlowInEasing.
            addUpdateListener {
                val now = SystemClock.uptimeMillis()
                val next = it.animatedValue as Float
                if (now > previousTime) velocity = (next - value) * 1000f / (now - previousTime)
                previousTime = now
                value = next
            }
            addListener(object : AnimatorListenerAdapter() {
                private var canceled = false
                override fun onAnimationCancel(animation: Animator) { canceled = true }
                override fun onAnimationEnd(animation: Animator) { if (!canceled) { velocity = 0f; complete() } }
            })
        }
        cancelAnimation = { animation.cancel() }
        animation.start()
    }
}

/** Prevent an outgoing page from dispatching input to its children, including during interruption. */
internal class AgoraPageSurface(context: Context) : FrameLayout(context) {
    var consumesInput = false
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = consumesInput || super.onInterceptTouchEvent(event)
    override fun onTouchEvent(event: MotionEvent): Boolean = consumesInput || super.onTouchEvent(event)
}

/** Native View port of Agora's ui/settings/SettingsAnimations.kt GuardedAnimatedContent. */
class AgoraPageHost(context: Context) : FrameLayout(context) {
    private class Slot(val surface: View, incoming: Boolean, val spatial: Boolean, render: () -> Unit) {
        val enter = AgoraMotionValue(if (incoming && spatial) 0f else 1f, render)
        val exit = AgoraMotionValue(0f, render)
        val alpha = AgoraMotionValue(if (incoming) 0f else 1f, render)
        val scale = AgoraMotionValue(if (incoming && spatial) .94f else 1f, render)
        var outgoing = false
        fun cancel() { enter.cancel(); exit.cancel(); alpha.cancel(); scale.cancel() }
    }
    private val slots = mutableListOf<Slot>()
    private var shield: View? = null
    private var generation = 0
    private var forward = true
    private var pendingDraw: OneShotPreDrawListener? = null
    var onDiscard: (View) -> Unit = {}
    val targetView: View? get() = slots.lastOrNull { !it.outgoing }?.surface
    val isTransitioning: Boolean get() = slots.any { it.outgoing }
    val pageCount: Int get() = slots.size

    init { clipChildren = true; clipToPadding = true }

    fun show(surface: View, forward: Boolean, animate: Boolean = true) {
        generation++
        val token = generation
        pendingDraw?.removeListener()
        pendingDraw = null
        slots.forEach { it.cancel() }
        slots.filter { it.outgoing }.toList().forEach { discard(it) }
        val previous = slots.lastOrNull()
        this.forward = forward
        val spatial = allowAgoraSpatialMotion(context)
        if (previous != null) {
            previous.outgoing = true
            (previous.surface as? AgoraPageSurface)?.consumesInput = true
            previous.surface.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            previous.surface.elevation = 0f
        }
        val next = Slot(surface, previous != null && animate, spatial, ::render)
        slots.add(next)
        // Target first in hierarchy for scoped findViewById; z-order still puts it above shield/outgoing.
        addView(surface, 0, LayoutParams(-1, -1))
        surface.elevation = 2f
        if (previous != null && animate) {
            shield = shield ?: View(context).apply {
                isClickable = true
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                elevation = 1f
                addView(this, LayoutParams(-1, -1))
            }
            render()
            // Wait only for the first draw's geometry, not an arbitrary startup delay.
            pendingDraw = doOnPreDraw {
                pendingDraw = null
                if (token != generation) return@doOnPreDraw
                var remaining = if (spatial) 6 else 2
                val complete = {
                    if (token == generation && --remaining == 0) {
                        discard(previous)
                        removeShield()
                        next.enter.snap(1f); next.exit.snap(0f); next.alpha.snap(1f); next.scale.snap(1f)
                    }
                    Unit
                }
                if (spatial) {
                    next.enter.spring(1f, complete)
                    next.alpha.tween(1f, complete = complete)
                    next.scale.spring(1f, complete)
                    previous.exit.spring(1f, complete)
                    previous.alpha.spring(0f, complete)
                    previous.scale.spring(.94f, complete)
                } else {
                    next.enter.snap(1f); next.scale.snap(1f)
                    previous.exit.snap(0f); previous.scale.snap(1f)
                    next.alpha.tween(1f, complete = complete)
                    previous.alpha.tween(0f, complete = complete)
                }
            }
        } else {
            previous?.let { discard(it) }
            removeShield()
            render()
        }
    }

    private fun render() {
        val sign = if (forward) 1f else -1f
        slots.forEach { slot ->
            val x = if (!slot.spatial) 0f else if (slot.outgoing) -sign * width * .75f * slot.exit.value
                else sign * width * .75f * (1f - slot.enter.value)
            slot.surface.translationX = x.roundToInt().toFloat()
            slot.surface.alpha = slot.alpha.value.coerceIn(0f, 1f)
            slot.surface.scaleX = slot.scale.value
            slot.surface.scaleY = slot.scale.value
        }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); render() }
    private fun discard(slot: Slot) {
        slot.cancel()
        slots.remove(slot)
        removeView(slot.surface)
        onDiscard(slot.surface)
    }
    private fun removeShield() { shield?.let(::removeView); shield = null }
    fun dispose() {
        generation++
        pendingDraw?.removeListener(); pendingDraw = null
        slots.forEach { it.cancel() }
        slots.clear()
        removeShield()
        removeAllViews()
    }
}
