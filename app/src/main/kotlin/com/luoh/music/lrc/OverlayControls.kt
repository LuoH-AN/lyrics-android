package com.luoh.music.lrc
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
/** Android controls for the service-owned WindowManager floating overlay. */
internal object OverlayControls {
    /** Chrome overlays the content; showing it never adds a column, margin or window height. */
    fun attachFloating(root: FrameLayout, touchArea: View, controls: LinearLayout) {
        root.addView(touchArea, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(controls, FrameLayout.LayoutParams(dp(root.context, 96), dp(root.context, 48), Gravity.TOP or Gravity.END))
        controls.translationZ = dp(root.context, 4).toFloat()
    }
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun iconButton(context: Context, drawableRes: Int, description: String, action: () -> Unit): MaterialButton =
        MaterialButton(ContextThemeWrapper(context, R.style.NativeIconButtonTheme)).apply {
            contentDescription = description
            setIconResource(drawableRes)
            iconSize = dp(context, 22)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            isCheckable = true
            setToggleCheckedStateOnClick(false)
            val color = ContextCompat.getColor(context, R.color.text_primary)
            iconTint = ColorStateList.valueOf(color)
            backgroundTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ColorUtils.setAlphaComponent(color, 24), Color.TRANSPARENT)
            )
            layoutParams = LinearLayout.LayoutParams(dp(context, 48), dp(context, 48))
            setOnClickListener { action() }
        }

}
