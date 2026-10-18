package com.luoh.music.lrc

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout

/**
 * Resolves the seed scheme and pushes it onto the platform widgets.
 *
 * XML themes can only name static colours, but the accent is a runtime
 * preference. This walks an inflated page once and recolours the controls that
 * Material would otherwise paint with the resource values.
 */
object AppTheme {

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedScheme: AppScheme? = null

    fun scheme(context: Context): AppScheme {
        val dark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val seed = AccentPrefs.customColor(context)
        val key = "${seed ?: 0}:$dark"
        val current = cachedScheme
        if (key == cachedKey && current != null) return current
        val value = AppScheme.build(seed, dark)
        cachedKey = key
        cachedScheme = value
        return value
    }

    fun invalidate() {
        cachedKey = null
        cachedScheme = null
    }

    /** Window chrome plus a full recolour of the activity's content. */
    fun apply(activity: Activity) {
        val colors = scheme(activity)
        val window = activity.window ?: return
        window.statusBarColor = colors.page
        window.navigationBarColor = colors.page
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.setBackgroundDrawable(ColorDrawable(colors.page))
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !colors.dark
            isAppearanceLightNavigationBars = !colors.dark
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.setBackgroundColor(colors.page)
        apply(content, colors)
    }

    fun apply(root: View) {
        apply(root, scheme(root.context))
    }

    private fun apply(root: View, scheme: AppScheme) {
        styleView(root, scheme)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) apply(root.getChildAt(index), scheme)
        }
    }

    private fun styleView(view: View, scheme: AppScheme) {
        when (view) {
            is MaterialToolbar -> {
                view.setBackgroundColor(scheme.page)
                view.setTitleTextColor(scheme.onSurface)
                view.setSubtitleTextColor(scheme.onSurfaceVariant)
                view.navigationIcon?.mutate()?.setTint(scheme.onSurface)
            }
            is MaterialButton -> if (view.parent !is SegmentedControl) styleButton(view, scheme)
            is MaterialSwitch -> styleSwitch(view, scheme)
            is Slider -> styleSlider(view, scheme)
            is TextInputLayout -> styleField(view, scheme)
            is MaterialDivider -> view.dividerColor = scheme.hairline
            is MaterialCardView -> {
                view.rippleColor = ColorStateList.valueOf(AppColor.alpha(scheme.primary, 0.12f))
            }
        }
    }

    private fun styleButton(button: MaterialButton, scheme: AppScheme) {
        Springs.attachPress(button)
        button.applySuperellipseShape()
        // Checkable buttons own their state list (selection, swatches, segments);
        // recolouring them here would flatten the checked state.
        if (button.isCheckable) return
        val context = button.context
        val tint = button.backgroundTintList
        val default = tint?.defaultColor ?: Color.TRANSPARENT
        val staticAccent = ContextCompat.getColor(context, R.color.accent)
        val staticBlock = ContextCompat.getColor(context, R.color.app_surface_container)
        when {
            same(default, scheme.primary) || same(default, staticAccent) -> {
                button.backgroundTintList = stateList(scheme.inset, scheme.primary, scheme.primary)
                button.setTextColor(stateList(
                    AppColor.alpha(scheme.onSurfaceVariant, 0.6f), scheme.onPrimary, scheme.onPrimary
                ))
                button.iconTint = button.textColors
                button.strokeWidth = 0
                button.rippleColor = ColorStateList.valueOf(AppColor.alpha(scheme.onPrimary, 0.18f))
            }
            same(default, scheme.block) || same(default, staticBlock) -> {
                button.backgroundTintList = stateList(scheme.inset, scheme.primary, scheme.block)
                button.setTextColor(stateList(
                    AppColor.alpha(scheme.onSurfaceVariant, 0.6f), scheme.onPrimary, scheme.onSurface
                ))
                button.iconTint = button.textColors
                button.strokeWidth = 0
                button.rippleColor = ColorStateList.valueOf(AppColor.alpha(scheme.onSurface, 0.10f))
            }
            else -> {
                // Outline and plain actions keep their fill; only the ripple and
                // label follow the seed so a custom accent still reads.
                button.rippleColor = ColorStateList.valueOf(AppColor.alpha(scheme.onSurface, 0.10f))
            }
        }
    }

    private fun styleSwitch(switch: MaterialSwitch, scheme: AppScheme) {
        switch.trackTintList = stateList(scheme.inset, scheme.primary, scheme.inset)
        switch.thumbTintList = stateList(scheme.card, scheme.onPrimary, scheme.card)
    }

    private fun styleSlider(slider: Slider, scheme: AppScheme) {
        slider.trackActiveTintList = ColorStateList.valueOf(scheme.primary)
        slider.trackInactiveTintList = ColorStateList.valueOf(scheme.track)
        slider.haloTintList = ColorStateList.valueOf(AppColor.alpha(scheme.primary, 0.12f))
        slider.thumbTintList = ColorStateList.valueOf(scheme.primary)
        slider.thumbStrokeColor = ColorStateList.valueOf(scheme.primary)
    }

    private fun styleField(field: TextInputLayout, scheme: AppScheme) {
        val context = field.context
        field.setBoxBackgroundColor(scheme.block)
        field.setBoxStrokeWidth(0)
        field.setBoxStrokeWidthFocused(0)
        field.setHintTextColor(ColorStateList.valueOf(scheme.onSurfaceVariant))
        val radius = DesignTokens.dp(context, DesignTokens.RADIUS_MD)
        field.setBoxCornerRadii(radius.toFloat(), radius.toFloat(), radius.toFloat(), radius.toFloat())
    }

    private fun stateList(disabled: Int, checked: Int, normal: Int): ColorStateList =
        ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(disabled, checked, normal)
        )

    private fun same(left: Int, right: Int): Boolean =
        (left and 0xFFFFFF) == (right and 0xFFFFFF)
}
