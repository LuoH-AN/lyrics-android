package com.luoh.music.lrc

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.bottomsheet.BottomSheetDialog

/** Agora's sheet spring (0.9 / 350), with Material's drag, back and scrim handling. */
class AgoraBottomSheetDialog(context: Context) : BottomSheetDialog(context) {
    private var motion: SpringAnimation? = null
    private var closing = false

    private fun spatialMotionAllowed() = ValueAnimator.areAnimatorsEnabled() && listOf(
        Settings.Global.ANIMATOR_DURATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.WINDOW_ANIMATION_SCALE
    ).all { Settings.Global.getFloat(context.contentResolver, it, 1f) != 0f }

    override fun show() {
        super.show()
        window?.setWindowAnimations(0)
        window?.setDimAmount(.32f)
        val sheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.doOnPreDraw {
            if (closing || !isShowing || !spatialMotionAllowed()) return@doOnPreDraw
            sheet.translationY = sheet.height.toFloat()
            motion = spring(sheet, 0f).apply { start() }
        }
    }

    override fun dismiss() {
        if (closing) return
        val sheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (!isShowing || sheet == null || !sheet.isAttachedToWindow || !spatialMotionAllowed()) {
            motion?.cancel()
            super.dismiss()
            return
        }
        closing = true
        motion?.cancel()
        motion = spring(sheet, sheet.height.toFloat()).apply {
            addEndListener { _, _, _, _ -> finishDismiss() }
            start()
        }
    }

    private fun finishDismiss() {
        motion = null
        super.dismiss()
    }

    private fun spring(sheet: View, target: Float) = SpringAnimation(sheet, SpringAnimation.TRANSLATION_Y).apply {
        spring = SpringForce(target).apply { dampingRatio = .9f; stiffness = 350f }
    }

    override fun onDetachedFromWindow() {
        motion?.cancel()
        motion = null
        super.onDetachedFromWindow()
    }
}
