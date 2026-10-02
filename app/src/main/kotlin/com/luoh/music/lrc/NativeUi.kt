package com.luoh.music.lrc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
            backgroundTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ColorUtils.setAlphaComponent(color, 24), Color.TRANSPARENT)
            )
            layoutParams = LinearLayout.LayoutParams(dp(context, 48), dp(context, 48))
            setOnClickListener { action() }
        }

    fun text(context: Context, text: String, size: Float = 14f): MaterialTextView = MaterialTextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        includeFontPadding = false
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

    fun card(context: Context): MaterialCardView = MaterialCardView(context).apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 12) }
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
        layoutParams = LinearLayout.LayoutParams(-1, dp(activity, 56))
    }

    fun confirm(context: Context, title: String, message: String, positive: String, onConfirm: () -> Unit): AlertDialog =
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton(positive) { _, _ -> onConfirm() }
            .show()

    fun screen(activity: AppCompatActivity, title: String, onBack: () -> Unit = { activity.finish() }): LinearLayout {
        val root = column(activity, 0).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setBackgroundColor(ContextCompat.getColor(activity, R.color.app_bg))
        }
        root.addView(toolbar(activity, title, onBack))
        val content = column(activity, 20)
        root.addView(NestedScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        activity.setContentView(root)
        return content
    }
}
