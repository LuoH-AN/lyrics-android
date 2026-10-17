package com.luoh.music.lrc

import android.animation.ValueAnimator
import android.app.Dialog
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Spring physics mirrored from FlClash's motion tokens.
 *
 * FlClash drives most of its UI through SpringSimulation: presses settle on a
 * mostly damped spring, page and tab travel uses a little bounce, and every
 * value can be retargeted mid-flight. Android's SpringAnimation gives us the
 * same state (position + velocity) without restarting a ticker.
 */
object Springs {
    // FlClash: _pressSpring (mass 1, stiffness 311, ratio 0.593)
    const val PRESS_STIFFNESS = 311f
    const val PRESS_DAMPING_RATIO = 0.593f

    // FlClash: _travelSpring (400ms, bounce 0.15) for moving panes.
    const val TRAVEL_STIFFNESS = 246.7f
    const val TRAVEL_DAMPING_RATIO = 0.85f

    // FlClash: _followSpring (stiffness 1024, ratio 0.9) for docking.
    const val FOLLOW_STIFFNESS = 1024f
    const val FOLLOW_DAMPING_RATIO = 0.9f

    fun force(stiffness: Float, dampingRatio: Float): SpringForce =
        SpringForce().apply {
            this.stiffness = stiffness
            this.dampingRatio = dampingRatio.coerceIn(0.05f, 1f)
        }

    /**
     * SpringAnimation validates the final position against min/max. The
     * defaults are not useful for arbitrary translation values, so every
     * spring here states an unbounded range explicitly.
     */
    fun spring(
        view: View,
        property: FloatPropertyCompat<View>,
        stiffness: Float = TRAVEL_STIFFNESS,
        dampingRatio: Float = TRAVEL_DAMPING_RATIO,
        minVisibleChange: Float = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
    ): SpringAnimation = SpringAnimation(view, property).apply {
        setMinValue(-Float.MAX_VALUE)
        setMaxValue(Float.MAX_VALUE)
        spring = force(stiffness, dampingRatio)
        setMinimumVisibleChange(minVisibleChange)
    }

    /** A two-property spring for press feedback on one view. */
    class Scale(
        view: View,
        stiffness: Float = PRESS_STIFFNESS,
        dampingRatio: Float = PRESS_DAMPING_RATIO
    ) {
        private val scaleX = spring(
            view,
            DynamicAnimation.SCALE_X,
            stiffness,
            dampingRatio,
            DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
        )
        private val scaleY = spring(
            view,
            DynamicAnimation.SCALE_Y,
            stiffness,
            dampingRatio,
            DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
        )

        fun animateTo(value: Float) {
            scaleX.animateToFinalPosition(value)
            scaleY.animateToFinalPosition(value)
        }

        fun cancel() {
            scaleX.cancel()
            scaleY.cancel()
        }
    }

    /** Press feedback for a button: 1 → [pressedScale] → 1, retargetable. */
    fun attachPress(view: View, pressedScale: Float = 0.94f) {
        val scale = Scale(view)
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> scale.animateTo(pressedScale)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scale.animateTo(1f)
            }
            false
        }
    }

    /** A panel entering with a short upward spring, used by sheets. */
    fun animateContentIn(view: View, offsetPx: Float) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.translationY = 0f
            view.alpha = 1f
            return
        }
        view.translationY = offsetPx
        view.alpha = 0f
        spring(view, DynamicAnimation.TRANSLATION_Y).animateToFinalPosition(0f)
        view.animate().alpha(1f).setDuration(180).start()
    }

    /** FlClash's alert motion: grow from slightly larger on a damped spring. */
    fun animateDialogIn(dialog: Dialog) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val decor = dialog.window?.decorView ?: return
        decor.post {
            if (!decor.isAttachedToWindow) return@post
            decor.pivotX = decor.width / 2f
            decor.pivotY = decor.height / 2f
            decor.alpha = 0f
            decor.scaleX = 1.08f
            decor.scaleY = 1.08f
            spring(
                decor,
                DynamicAnimation.SCALE_X,
                PRESS_STIFFNESS,
                0.72f,
                DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
            ).animateToFinalPosition(1f)
            spring(
                decor,
                DynamicAnimation.SCALE_Y,
                PRESS_STIFFNESS,
                0.72f,
                DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
            ).animateToFinalPosition(1f)
            decor.animate().alpha(1f).setDuration(180).start()
        }
    }
}
