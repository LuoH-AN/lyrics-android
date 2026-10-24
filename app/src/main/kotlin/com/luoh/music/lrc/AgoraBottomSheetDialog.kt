package com.luoh.music.lrc

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.view.doOnPreDraw
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/** Agora's sheet spring (0.9 / 350), with Material's drag, back and scrim handling. */
class AgoraBottomSheetDialog(context: Context) : BottomSheetDialog(context) {
    private var motion: SpringAnimation? = null
    private var closing = false
    private var scrim: View? = null
    private val sheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_DRAGGING) motion?.cancel()
        }
        override fun onSlide(bottomSheet: View, slideOffset: Float) = updateScrim(bottomSheet)
    }

    private fun spatialMotionAllowed() = ValueAnimator.areAnimatorsEnabled() && listOf(
        Settings.Global.ANIMATOR_DURATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.WINDOW_ANIMATION_SCALE
    ).all { Settings.Global.getFloat(context.contentResolver, it, 1f) != 0f }

    override fun show() {
        closing = false
        window?.setWindowAnimations(0)
        // WindowManager dimming has its own exit fade. Draw the scrim in the same
        // view hierarchy as the sheet so both leave on the same rendered frame.
        window?.setDimAmount(0f)
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        super.show()
        scrim = findViewById<View>(com.google.android.material.R.id.touch_outside)?.apply {
            setBackgroundColor(Color.BLACK)
            alpha = .32f
        }
        behavior.removeBottomSheetCallback(sheetCallback)
        behavior.addBottomSheetCallback(sheetCallback)
        val sheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.translationY = 0f
        sheet.doOnPreDraw {
            if (closing || !isShowing) return@doOnPreDraw
            val animate = spatialMotionAllowed()
            if (animate) sheet.translationY = sheet.height.toFloat()
            updateScrim(sheet)
            if (animate) motion = spring(sheet, 0f).apply { start() }
        }
    }

    override fun dismiss() {
        if (closing) return
        val sheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (!isShowing || sheet == null || !sheet.isAttachedToWindow ||
            !spatialMotionAllowed() || visibleHeight(sheet) <= 1f) {
            finishDismiss()
            return
        }
        closing = true
        motion?.cancel()
        motion = spring(sheet, sheet.height.toFloat()).apply {
            addEndListener { _, canceled, _, _ -> if (!canceled) finishDismiss() }
            start()
        }
    }

    private fun finishDismiss() {
        motion?.cancel()
        motion = null
        scrim?.alpha = 0f
        super.dismiss()
    }

    private fun spring(sheet: View, target: Float) = SpringAnimation(sheet, SpringAnimation.TRANSLATION_Y).apply {
        spring = SpringForce(target).apply { dampingRatio = .9f; stiffness = 350f }
        addUpdateListener { _, _, _ ->
            updateScrim(sheet)
            // Do not keep an invisible dialog alive for the spring's settling tail.
            if (closing && visibleHeight(sheet) <= 1f) finishDismiss()
        }
    }

    private fun visibleHeight(sheet: View): Float {
        val bottom = (sheet.parent as? View)?.height ?: (sheet.top + sheet.height)
        return (bottom - sheet.y).coerceIn(0f, sheet.height.toFloat())
    }

    private fun updateScrim(sheet: View) {
        scrim?.alpha = .32f * (visibleHeight(sheet) / sheet.height.coerceAtLeast(1))
    }

    override fun onDetachedFromWindow() {
        motion?.cancel()
        motion = null
        scrim?.alpha = 0f
        behavior.removeBottomSheetCallback(sheetCallback)
        super.onDetachedFromWindow()
    }
}
