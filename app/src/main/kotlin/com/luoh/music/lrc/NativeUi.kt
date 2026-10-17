package com.luoh.music.lrc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView

/** Shared native Material components; appearance comes from the app's monochrome theme. */
object NativeUi {
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density + .5f).toInt()

    fun button(context: Context, text: String, primary: Boolean = false, action: () -> Unit): MaterialButton =
        MaterialButton(ContextThemeWrapper(context, if (primary) R.style.NativePrimaryButtonTheme else R.style.NativeButtonTheme)).apply {
            this.text = text
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
            isCheckable = true
            setToggleCheckedStateOnClick(false)
            val color = ContextCompat.getColor(context, R.color.text_primary)
            iconTint = ColorStateList.valueOf(color)
            cornerRadius = dp(context, 24)
            backgroundTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ColorUtils.setAlphaComponent(color, 24), Color.TRANSPARENT)
            )
            layoutParams = LinearLayout.LayoutParams(dp(context, 48), dp(context, 48))
            setOnClickListener { action() }
            Springs.attachPress(this)
        }

    fun text(context: Context, text: String, size: Float = 14f): MaterialTextView = MaterialTextView(context).apply {
        this.text = text
        textSize = size
        includeFontPadding = false
        if (size >= 17f) {
            // Card headings use the same title color/weight as the settings page.
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        } else {
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
    }

    fun field(context: Context, hint: String, value: String = "", multiline: Boolean = false): TextInputLayout =
        TextInputLayout(context).apply {
            this.hint = hint
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 8) }
            addView(TextInputEditText(context).apply {
                textSize = if (multiline) 13f else 14f
                setText(value)
                isSingleLine = !multiline
                if (multiline) {
                    gravity = Gravity.TOP or Gravity.START
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    minLines = 12
                    setHorizontallyScrolling(false)
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }

    fun card(context: Context): SquircleCardView = SquircleCardView(context).apply {
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
        textSize = 13f
        includeFontPadding = false
        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        setPadding(dp(context, 4), dp(context, 20), 0, dp(context, 8))
    }

    fun divider(context: Context, indentDp: Int = 16): MaterialDivider = MaterialDivider(context).apply {
        dividerColor = ContextCompat.getColor(context, R.color.app_line_soft)
        layoutParams = LinearLayout.LayoutParams(-1, dp(context, 1)).apply {
            marginStart = dp(context, indentDp)
        }
    }

    /** A compact destructive text action for a grouped row. */
    fun dangerButton(context: Context, text: String, action: () -> Unit): MaterialButton =
        MaterialButton(ContextThemeWrapper(context, R.style.NativeButtonTheme)).apply {
            this.text = text
            textSize = 13f
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            setPadding(dp(context, 8), 0, dp(context, 8), 0)
            setTextColor(ContextCompat.getColor(context, R.color.danger))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = 0
            cornerRadius = dp(context, 20)
            layoutParams = LinearLayout.LayoutParams(-2, dp(context, 40)).apply {
                marginStart = dp(context, 6)
            }
            setOnClickListener { action() }
            Springs.attachPress(this)
        }

    /**
     * FlClash-style grouped row: a leading glyph, a title/subtitle column,
     * an optional value, an optional trailing view and a chevron.
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
        val titleColor = ContextCompat.getColor(context, if (danger) R.color.danger else R.color.text_primary)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16), 0, dp(context, 14), 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(context, if (subtitle != null) 64 else 56))
        }
        if (iconRes != null) {
            row.addView(ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, if (danger) R.color.danger else R.color.text_secondary)
                )
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)).apply {
                marginEnd = dp(context, 14)
            })
        }
        val textColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(MaterialTextView(context).apply {
            text = title
            textSize = 16f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(titleColor)
        }, LinearLayout.LayoutParams(-1, -2))
        if (subtitle != null) {
            textColumn.addView(MaterialTextView(context).apply {
                this.text = subtitle
                textSize = 13f
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 2) })
        }
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        if (value != null) {
            row.addView(MaterialTextView(context).apply {
                text = value
                textSize = 14f
                includeFontPadding = false
                maxLines = 1
                setTextColor(valueColor ?: ContextCompat.getColor(context, R.color.text_secondary))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(context, 8) })
        }
        if (trailing != null) {
            row.addView(trailing)
        }
        if (chevron) {
            row.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_forward)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_tertiary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)).apply {
                marginStart = dp(context, 8)
            })
        }
        if (onClick != null) {
            row.isClickable = true
            row.isFocusable = true
            row.foreground = RippleDrawable(
                ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.text_primary), 24)
                ),
                null,
                null
            )
            row.setOnClickListener { onClick() }
        }
        return row
    }

    fun column(context: Context, padding: Int = 16): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun toolbar(activity: AppCompatActivity, title: String, onBack: () -> Unit): MaterialToolbar = MaterialToolbar(activity).apply {
        this.title = title
        setNavigationIcon(R.drawable.ic_native_back)
        navigationContentDescription = "返回"
        setNavigationOnClickListener { onBack() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(activity, 64))
    }

    fun confirm(context: Context, title: String, message: String, positive: String, onConfirm: () -> Unit): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton(positive) { _, _ -> onConfirm() }
            .create()
        // The alert grows in on a spring instead of the window's fixed curve.
        dialog.window?.setWindowAnimations(0)
        dialog.setOnShowListener { Springs.animateDialogIn(dialog) }
        dialog.show()
        return dialog
    }

    fun screen(activity: AppCompatActivity, title: String, onBack: () -> Unit = { activity.finish() }): LinearLayout {
        val root = column(activity, 0).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setBackgroundColor(ContextCompat.getColor(activity, R.color.app_bg))
        }
        root.addView(toolbar(activity, title, onBack))
        val content = column(activity, 16)
        root.addView(NestedScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        activity.setContentView(root)
        return content
    }
}
