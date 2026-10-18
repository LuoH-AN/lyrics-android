package com.luoh.music.lrc

import android.animation.ValueAnimator
import android.app.Dialog
import android.view.MotionEvent
import android.view.View
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

    private const val PRESSED_SCALE = 0.96f
    private const val ALERT_ENTRY_SCALE = 1.2f
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

    /** Press feedback for a button: 1 → [pressedScale] → 1, retargetable. */
    fun attachPress(view: View, pressedScale: Float = PRESSED_SCALE) {
        if (pressed[view] == true) return
        pressed[view] = true
        val scale = Scale(view)
        view.setOnTouchListener { _, event ->
            if (!ValueAnimator.areAnimatorsEnabled()) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> scale.animateTo(pressedScale)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scale.animateTo(1f)
            }
            false
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                scale.cancel()
                v.removeOnAttachStateChangeListener(this)
                pressed.remove(v)
            }
        })
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

    /** The alert grows from 1.2× and fades in, matching the measured UIKit motion. */
    fun animateDialogIn(dialog: Dialog) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val decor = dialog.window?.decorView ?: return
        decor.post {
            if (!decor.isAttachedToWindow) return@post
            decor.pivotX = decor.width / 2f
            decor.pivotY = decor.height / 2f
            decor.alpha = 0f
            decor.scaleX = ALERT_ENTRY_SCALE
            decor.scaleY = ALERT_ENTRY_SCALE
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
            decor.animate().alpha(1f).setDuration(DesignTokens.Motion.DIALOG_MS).start()
        }
    }

    /** Motion durations collapse to zero when the system asks for less motion. */
    fun duration(milliseconds: Long): Long =
        if (ValueAnimator.areAnimatorsEnabled()) milliseconds else 0L
}
