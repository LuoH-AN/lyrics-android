package com.luoh.music.lrc

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.util.WeakHashMap

/**
 * Spring physics mirrored from the design system's motion tokens.
 *
 * FlClash drives most of its UI through springs rather than fixed durations.
 * Android's SpringAnimation gives the same position + velocity state, so a
 * gesture can retarget a value mid-flight instead of restarting a ticker.
 */
object Springs {
    // Press feedback on buttons, cards and alert panels.
    const val PRESS_STIFFNESS = 311f
    const val PRESS_DAMPING_RATIO = 0.593f

    // Moving panes and tab-level travel.
    const val TRAVEL_STIFFNESS = 246.7f
    const val TRAVEL_DAMPING_RATIO = 0.85f

    // Docking / following the finger.
    const val FOLLOW_STIFFNESS = 1024f
    const val FOLLOW_DAMPING_RATIO = 0.9f

    // Sheet presentation: UIKit's mass 3, stiffness 1000.
    const val SHEET_STIFFNESS = 1000f / 3f
    const val SHEET_DAMPING_RATIO = 1f

    private const val PRESSED_SCALE = 0.97f
    private val EASE_OUT = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val pressed = WeakHashMap<View, Boolean>()

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

    /** Uses the platform's pressed state without intercepting touch gestures. */
    fun attachPress(view: View, pressedScale: Float = PRESSED_SCALE) {
        if (pressed[view] == true) return
        pressed[view] = true
        fun scaleTo(value: Float, milliseconds: Long) = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, value),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, value)
            )
            duration = Springs.duration(milliseconds)
            interpolator = EASE_OUT
        }
        view.stateListAnimator = StateListAnimator().apply {
            addState(
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
                scaleTo(pressedScale, DesignTokens.Motion.PRESS_MS)
            )
            addState(intArrayOf(), scaleTo(1f, DesignTokens.Motion.RELEASE_MS))
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                v.isPressed = false
                v.stateListAnimator?.jumpToCurrentState()
                v.scaleX = 1f
                v.scaleY = 1f
            }
        })
    }

    /** Avoids lyric rows, whose scale is owned by the playback motion controller. */
    fun attachPressFeedback(view: View) {
        when {
            view is Button || view is ImageButton -> attachPress(view)
            view.isClickable && (view is LinearLayout || view is MaterialCardView) -> attachPress(view, 0.99f)
            view is TextView && view !is EditText && view !is LyricLineView && view.isClickable -> attachPress(view)
        }
    }

    /** Covers XML buttons, toolbar icons and clickable grouped rows. */
    fun attachPressTree(root: View) {
        attachPressFeedback(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) attachPressTree(root.getChildAt(index))
        }
    }

    /** Motion durations collapse to zero when the system asks for less motion. */
    fun duration(milliseconds: Long): Long =
        if (ValueAnimator.areAnimatorsEnabled()) milliseconds else 0L
}
