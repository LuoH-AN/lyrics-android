package com.luoh.music.lrc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView

/**
 * Shared native components. Appearance comes from the token layer only:
 * surfaces step one level at a time, corners are superellipses, and type comes
 * from the Apple scale.
 */
object NativeUi {
    fun dp(context: Context, value: Int): Int = DesignTokens.dp(context, value)

    fun dp(context: Context, value: Float): Float = DesignTokens.dp(context, value)

    fun button(context: Context, text: String, primary: Boolean = false, action: () -> Unit): MaterialButton =
        MaterialButton(ContextThemeWrapper(context, if (primary) R.style.NativePrimaryButtonTheme else R.style.NativeButtonTheme)).apply {
            this.text = text
            isAllCaps = false
            textSize = AppTextStyle.subheadline.sizeSp
            setTextAppearance(R.style.Text_Subheadline_Emphasized)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            stateListAnimator = null
            elevation = 0f
            cornerRadius = dp(context, DesignTokens.RADIUS_XL).toInt()
            applySuperellipseShape()
            strokeWidth = 0
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            backgroundTintList = filledButtonTint(context, primary)
            setTextColor(filledButtonText(context, primary))
            rippleColor = ColorStateList.valueOf(
                AppColor.alpha(if (primary) DesignTokens.onAccent(context) else DesignTokens.textPrimary(context), 0.16f)
            )
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 8) }
            setOnClickListener { action() }
            Springs.attachPress(this)
        }

    fun iconButton(context: Context, drawableRes: Int, description: String, action: () -> Unit): MaterialButton =
        MaterialButton(ContextThemeWrapper(context, R.style.NativeIconButtonTheme)).apply {
            contentDescription = description
            setIconResource(drawableRes)
            iconSize = dp(context, 22)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            gravity = Gravity.CENTER
            text = ""
            isCheckable = true
            setToggleCheckedStateOnClick(false)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            strokeWidth = 0
            stateListAnimator = null
            elevation = 0f
            cornerRadius = dp(context, DesignTokens.RADIUS_XL).toInt()
            applySuperellipseShape()
            val scheme = DesignTokens.scheme(context)
            val states = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            )
            backgroundTintList = ColorStateList(
                states,
                intArrayOf(Color.TRANSPARENT, AppColor.alpha(scheme.primary, 0.12f), Color.TRANSPARENT)
            )
            iconTint = ColorStateList(
                states,
                intArrayOf(
                    AppColor.alpha(scheme.onSurfaceVariant, 0.38f),
                    scheme.primary,
                    scheme.onSurface
                )
            )
            rippleColor = ColorStateList.valueOf(AppColor.alpha(scheme.primary, 0.12f))
            layoutParams = LinearLayout.LayoutParams(dp(context, 48), dp(context, 48))
            setOnClickListener { action() }
            Springs.attachPress(this)
        }

    fun text(context: Context, text: String, size: Float = 14f): MaterialTextView =
        MaterialTextView(context).apply {
            this.text = text
            val token = appTextToken(size)
            applyTextStyle(
                token,
                emphasized = size >= AppTextStyle.headline.sizeSp,
                color = if (size >= AppTextStyle.callout.sizeSp) DesignTokens.textPrimary(context)
                else DesignTokens.textSecondary(context)
            )
        }

    fun field(context: Context, hint: String, value: String = "", multiline: Boolean = false): TextInputLayout {
        val scheme = DesignTokens.scheme(context)
        return TextInputLayout(context).apply {
            this.hint = hint
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_FILLED
            setBoxBackgroundColor(scheme.block)
            setBoxStrokeWidth(0)
            setBoxStrokeWidthFocused(0)
            setHintTextColor(ColorStateList.valueOf(scheme.onSurfaceVariant))
            val radius = dp(context, DesignTokens.RADIUS_MD).toInt()
            setBoxCornerRadii(radius.toFloat(), radius.toFloat(), radius.toFloat(), radius.toFloat())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 8) }
            addView(TextInputEditText(context).apply {
                applyTextStyle(if (multiline) AppTextStyle.footnote else AppTextStyle.subheadline)
                setTextColor(scheme.onSurface)
                setHintTextColor(scheme.onSurfaceVariant)
                setText(value)
                isSingleLine = !multiline
                if (multiline) {
                    gravity = Gravity.TOP or Gravity.START
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    minLines = 12
                    setHorizontallyScrolling(false)
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    fun card(context: Context): SquircleCardView = SquircleCardView(context).apply {
        setSquircleRadiusDp(DesignTokens.RADIUS_XL)
        setSquircleFillColor(DesignTokens.card(context))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 12) }
    }

    /** A card holding a single row, preserving the row's fixed height. */
    fun cardWithRow(context: Context, row: View): SquircleCardView = card(context).apply {
        addView(row, FrameLayout.LayoutParams(-1, row.layoutParams?.height ?: -2))
    }

    fun cardColumn(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun sectionLabel(context: Context, text: String): MaterialTextView = MaterialTextView(context).apply {
        this.text = text
        applyTextStyle(AppTextStyle.footnote, emphasized = true, color = DesignTokens.textSecondary(context))
        setPadding(dp(context, 4), dp(context, 24), 0, dp(context, 8))
    }

    fun divider(context: Context, indentDp: Int = 16): MaterialDivider = MaterialDivider(context).apply {
        dividerColor = DesignTokens.hairline(context)
        layoutParams = LinearLayout.LayoutParams(-1, dp(context, 1)).apply {
            marginStart = dp(context, indentDp)
        }
    }

    /** A compact destructive text action for a grouped row. */
    fun dangerButton(context: Context, text: String, action: () -> Unit): MaterialButton =
        MaterialButton(ContextThemeWrapper(context, R.style.NativeButtonTheme)).apply {
            this.text = text
            applyTextStyle(AppTextStyle.footnote, emphasized = true, color = DesignTokens.danger(context))
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            setPadding(dp(context, 8), 0, dp(context, 8), 0)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = 0
            cornerRadius = dp(context, DesignTokens.RADIUS_LG).toInt()
            applySuperellipseShape()
            rippleColor = ColorStateList.valueOf(AppColor.alpha(DesignTokens.danger(context), 0.12f))
            layoutParams = LinearLayout.LayoutParams(-2, dp(context, 40)).apply {
                marginStart = dp(context, 6)
            }
            setOnClickListener { action() }
            Springs.attachPress(this)
        }

    /**
     * A grouped row: leading glyph, title/subtitle column, optional value,
     * optional trailing view and a chevron.
     */
    fun row(
        context: Context,
        title: String,
        iconRes: Int? = null,
        subtitle: String? = null,
        value: String? = null,
        valueColor: Int? = null,
        chevron: Boolean = false,
        trailing: View? = null,
        danger: Boolean = false,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val scheme = DesignTokens.scheme(context)
        val foreground = if (danger) scheme.error else scheme.onSurface
        val iconColor = if (danger) scheme.error else scheme.onSurfaceVariant
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16), 0, dp(context, 16), 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(context, if (subtitle != null) 64 else 56))
        }
        if (iconRes != null) {
            row.addView(ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(iconColor)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)).apply {
                marginEnd = dp(context, 14)
            })
        }
        val textColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(MaterialTextView(context).apply {
            text = title
            applyTextStyle(AppTextStyle.body, color = foreground)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2))
        if (subtitle != null) {
            textColumn.addView(MaterialTextView(context).apply {
                this.text = subtitle
                applyTextStyle(AppTextStyle.footnote, color = scheme.onSurfaceVariant)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 2) })
        }
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        if (value != null) {
            row.addView(MaterialTextView(context).apply {
                text = value
                applyTextStyle(AppTextStyle.subheadline, color = valueColor ?: scheme.onSurfaceVariant)
                maxLines = 1
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(context, 8) })
        }
        if (trailing != null) {
            row.addView(trailing)
        }
        if (chevron) {
            row.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_forward)
                imageTintList = ColorStateList.valueOf(scheme.outline)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)).apply {
                marginStart = dp(context, 8)
            })
        }
        if (onClick != null) {
            row.isClickable = true
            row.isFocusable = true
            row.foreground = RippleDrawable(
                ColorStateList.valueOf(AppColor.alpha(scheme.onSurface, 0.10f)),
                null,
                null
            )
            row.setOnClickListener { onClick() }
            Springs.attachPress(row, 0.99f)
        }
        return row
    }

    fun column(context: Context, padding: Int = 16): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun toolbar(activity: AppCompatActivity, title: String, onBack: () -> Unit): MaterialToolbar =
        MaterialToolbar(activity).apply {
            this.title = title
            setNavigationIcon(R.drawable.ic_native_back)
            navigationContentDescription = "返回"
            navigationIcon?.mutate()?.setTint(DesignTokens.textPrimary(activity))
            setTitleTextColor(DesignTokens.textPrimary(activity))
            setContentInsetsRelative(dp(activity, 16), dp(activity, 16))
            setNavigationOnClickListener { onBack() }
            setBackgroundColor(DesignTokens.page(activity))
            layoutParams = LinearLayout.LayoutParams(-1, dp(activity, DesignTokens.PAGE_TOOLBAR_HEIGHT.toInt()))
            Springs.attachPressTree(this)
        }

    fun confirm(context: Context, title: String, message: String, positive: String, onConfirm: () -> Unit): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton(positive) { _, _ -> onConfirm() }
            .create()
        return showDialog(dialog, widthDp = 348)
    }

    /** Window fades cover buttons, Back and outside-tap dismissal equally. */
    fun showDialog(dialog: AlertDialog, widthDp: Int? = null): AlertDialog {
        dialog.window?.setWindowAnimations(R.style.AppDialogAnimation)
        dialog.window?.setDimAmount(if (DesignTokens.scheme(dialog.context).dark) 0.48f else 0.20f)
        dialog.show()
        if (widthDp != null) {
            dialog.window?.setLayout(dp(dialog.context, widthDp), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        styleDialog(dialog)
        return dialog
    }

    /** Recolours an already shown Material alert with the live seed. */
    fun styleDialog(dialog: AlertDialog) {
        val scheme = DesignTokens.scheme(dialog.context)
        dialog.window?.setDimAmount(if (scheme.dark) 0.48f else 0.20f)
        val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val destructive = positive?.text?.let {
            it.contains("删除") || it.contains("清除") || it.contains("重置")
        } == true
        positive?.setTextColor(if (destructive) scheme.destructive else scheme.primary)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(scheme.onSurface)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(scheme.onSurface)
        dialog.window?.decorView?.let(Springs::attachPressTree)
    }

    fun screen(activity: AppCompatActivity, title: String, onBack: () -> Unit = { activity.finish() }): LinearLayout {
        val root = column(activity, 0).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setBackgroundColor(DesignTokens.page(activity))
        }
        root.addView(toolbar(activity, title, onBack))
        val content = column(activity, 16).apply {
            setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 28))
        }
        root.addView(NestedScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        activity.setContentView(root)
        AppTheme.apply(activity)
        return content
    }

    /** Applies a superellipse fill, optionally with a state list. */
    fun squircle(
        context: Context,
        radiusDp: Float,
        fill: Int,
        corners: Int = SquircleDrawable.CORNER_ALL
    ): SquircleDrawable = SquircleDrawable(fill, dp(context, radiusDp), corners = corners)

    private fun filledButtonTint(context: Context, primary: Boolean): ColorStateList {
        val scheme = DesignTokens.scheme(context)
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val enabled = if (primary) scheme.primary else scheme.block
        val checked = scheme.primary
        return ColorStateList(states, intArrayOf(scheme.inset, checked, enabled))
    }

    private fun filledButtonText(context: Context, primary: Boolean): ColorStateList {
        val scheme = DesignTokens.scheme(context)
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val normal = if (primary) scheme.onPrimary else scheme.onSurface
        return ColorStateList(states, intArrayOf(
            AppColor.alpha(scheme.onSurfaceVariant, 0.6f),
            scheme.onPrimary,
            normal
        ))
    }

    /** Kept for call sites that build a text view with a raw sp size. */
    fun sp(size: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        size,
        android.content.res.Resources.getSystem().displayMetrics
    )
}
