package com.luoh.music.lrc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

/** Keep the palette and HEX draft separate from the saved lyric color until Apply. */
class LyricColorPickerDialog(
    private val context: Context,
    initialHex: String,
    draftHex: String? = null,
    private val onApply: (String) -> Unit
) {
    private val content = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
    private val preview = content.findViewById<TextView>(R.id.color_picker_preview)
    private val hexField = content.findViewById<TextInputLayout>(R.id.color_hex_field)
    private val hexInput = content.findViewById<TextInputEditText>(R.id.color_hex_input)
    private val defaultHex = normalizeHex(initialHex) ?: LyricsOverlayService.LYRIC_COLOR_DEFAULT
    private val choices = mutableListOf<Pair<MaterialButton, String>>()
    private var applyButton: Button? = null
    private val dialog = MaterialAlertDialogBuilder(context)
        .setTitle("歌词颜色")
        .setView(content)
        .setNegativeButton("取消", null)
        .setPositiveButton("应用", null)
        .create()

    val draft: String get() = hexInput.text.toString()
    val isShowing: Boolean get() = dialog.isShowing

    init {
        val palette = content.findViewById<LinearLayout>(R.id.color_palette)
        COLORS.chunked(4).forEach { colors ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            colors.forEach { hex ->
                val cell = FrameLayout(context)
                val swatch = NativeUi.button(context, "") {
                    hexInput.setText(hex)
                    hexInput.setSelection(hexInput.text?.length ?: 0)
                }.apply {
                    tag = hex
                    isCheckable = true
                    setToggleCheckedStateOnClick(false)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(hex))
                    setTextColor(contrastColor(Color.parseColor(hex)))
                    cornerRadius = dp(24)
                    textSize = 20f
                    setPadding(0, 0, 0, 0)
                    layoutParams = FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER)
                }
                cell.addView(swatch)
                row.addView(cell, LinearLayout.LayoutParams(0, dp(56), 1f))
                choices += swatch to hex
            }
            palette.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        hexInput.setText(draftHex ?: defaultHex)
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateSelection()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateSelection()
    }

    fun show() {
        dialog.show()
        applyButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            setOnClickListener {
                val color = normalizeHex(draft) ?: return@setOnClickListener
                onApply(color)
                dialog.dismiss()
            }
        }
        updateSelection()
    }

    fun dismiss() = dialog.dismiss()

    private fun updateSelection() {
        val hex = normalizeHex(draft)
        hexField.error = if (hex == null) "请输入 6 位 HEX 色值" else null
        applyButton?.isEnabled = hex != null
        val color = Color.parseColor(hex ?: defaultHex)
        preview.text = hex ?: defaultHex
        preview.setTextColor(contrastColor(color))
        preview.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(color)
            setStroke(dp(1), ContextCompat.getColor(context, R.color.app_line))
        }
        choices.forEach { (choice, candidate) ->
            val selected = candidate == hex
            choice.isChecked = selected
            choice.text = if (selected) "✓" else ""
            choice.contentDescription = if (selected) "$candidate，已选中" else candidate
            choice.strokeWidth = dp(if (selected) 2 else 1)
            choice.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context,
                if (selected) R.color.accent else R.color.app_line))
        }
    }

    private fun dp(value: Int) = NativeUi.dp(context, value)
    private fun contrastColor(color: Int) =
        if (ColorUtils.calculateContrast(Color.WHITE, color) >= 4.5) Color.WHITE else Color.BLACK

    companion object {
        val COLORS = listOf(
            "#FFFFFF", "#E2E8F0", "#64748B", "#111111",
            "#FF6B6B", "#FFA07A", "#FFB347", "#FFE066",
            "#D4F26E", "#95D5B2", "#43AA8B", "#00B4D8",
            "#9FD8FF", "#74C0FC", "#6C8DFF", "#B197FC",
            "#E599F7", "#FFB6D5", "#F783AC", "#E8C4A2",
            "#F4E8D2", "#B8B8D1", "#5F6F52", "#283618"
        )
        private val HEX = Regex("^#?([0-9a-fA-F]{6})$")

        fun normalizeHex(value: String): String? = HEX.matchEntire(value.trim())
            ?.groupValues?.get(1)?.let { "#${it.uppercase(Locale.ROOT)}" }
    }
}
