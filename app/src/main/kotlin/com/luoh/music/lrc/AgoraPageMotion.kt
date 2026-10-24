package com.luoh.music.lrc

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.roundToInt

internal fun allowAgoraSpatialMotion(context: Context): Boolean = listOf(
    Settings.Global.ANIMATOR_DURATION_SCALE,
    Settings.Global.TRANSITION_ANIMATION_SCALE,
    Settings.Global.WINDOW_ANIMATION_SCALE
).none { Settings.Global.getFloat(context.contentResolver, it, 1f) == 0f }

/** Prevent an outgoing page from dispatching input to its children, including during interruption. */
internal class AgoraPageSurface(context: Context) : FrameLayout(context) {
    var consumesInput = false
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = consumesInput || super.onInterceptTouchEvent(event)
    override fun onTouchEvent(event: MotionEvent): Boolean = consumesInput || super.onTouchEvent(event)
}

/** Native View port of Agora's ui/settings/SettingsAnimations.kt GuardedAnimatedContent. */
class AgoraPageHost(context: Context) : FrameLayout(context) {
    private class Slot(val surface: View, incoming: Boolean, spatial: Boolean) {
        val enter = Animatable(if (incoming && spatial) 0f else 1f)
        val exit = Animatable(0f)
        val alpha = Animatable(if (incoming) 0f else 1f)
        val scale = Animatable(if (incoming && spatial) .94f else 1f)
        var outgoing = false
    }
    private val slots = mutableListOf<Slot>()
    private var shield: View? = null
    private var generation = 0
    private var forward = true
    private val runtime = AgoraTransitionRuntime(context)
    var onDiscard: (View) -> Unit = {}
    val targetView: View? get() = slots.lastOrNull { !it.outgoing }?.surface
    val isTransitioning: Boolean get() = slots.any { it.outgoing }
    val pageCount: Int get() = slots.size

    init {
        clipChildren = true; clipToPadding = true
        runtime.onPolicyChanged = ::render
    }

    fun show(surface: View, forward: Boolean, animate: Boolean = true) {
        generation++
        val token = generation
        runtime.cancel()
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
        val next = Slot(surface, previous != null && animate, spatial)
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
            runtime.start {
                if (token != generation) return@start
                if (spatial) {
                    listOf(
                        launch { animateAgora(next.enter, 1f, agoraPageSpring(), ::render) },
                        launch { animateAgora(next.alpha, 1f, tween(300), ::render) },
                        launch { animateAgora(next.scale, 1f, agoraPageSpring(), ::render) },
                        launch { animateAgora(previous.exit, 1f, agoraPageSpring(), ::render) },
                        launch { animateAgora(previous.alpha, 0f, agoraPageSpring(), ::render) },
                        launch { animateAgora(previous.scale, .94f, agoraPageSpring(), ::render) }
                    ).joinAll()
                } else {
                    next.enter.snapTo(1f); next.scale.snapTo(1f)
                    previous.exit.snapTo(0f); previous.scale.snapTo(1f)
                    render()
                    listOf(
                        launch { animateAgora(next.alpha, 1f, tween(300), ::render) },
                        launch { animateAgora(previous.alpha, 0f, tween(300), ::render) }
                    ).joinAll()
                }
                if (token == generation) {
                    discard(previous)
                    removeShield()
                    next.enter.snapTo(1f); next.exit.snapTo(0f)
                    next.alpha.snapTo(1f); next.scale.snapTo(1f)
                    render()
                }
            }
        } else {
            previous?.let { discard(it) }
            removeShield()
            render()
        }
    }

    private fun render() {
        val sign = (if (forward) 1f else -1f) * (if (layoutDirection == LAYOUT_DIRECTION_RTL) -1f else 1f)
        slots.forEach { slot ->
            val x = if (!runtime.allowSpatialMotion) 0f else if (slot.outgoing) -sign * width * .75f * slot.exit.value
                else sign * width * .75f * (1f - slot.enter.value)
            slot.surface.translationX = x.roundToInt().toFloat()
            slot.surface.alpha = slot.alpha.value.coerceIn(0f, 1f)
            slot.surface.scaleX = if (runtime.allowSpatialMotion) slot.scale.value else 1f
            slot.surface.scaleY = slot.surface.scaleX
        }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); render() }
    private fun discard(slot: Slot) {
        slots.remove(slot)
        removeView(slot.surface)
        onDiscard(slot.surface)
    }
    private fun removeShield() { shield?.let(::removeView); shield = null }
    fun dispose() {
        generation++
        runtime.dispose()
        slots.clear()
        removeShield()
        removeAllViews()
    }
}
