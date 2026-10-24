package com.luoh.music.lrc

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

/** Reusable list cell: bind changes text/listeners, retaining the inflated Material controls. */
class LibraryRow(context: Context) : MaterialCardView(context) {
    private val title = NativeUi.text(context, "", 19f).apply {
        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
    }
    private val supporting = NativeUi.text(context, "", 13f)
    private val metadata = NativeUi.text(context, "", 13f)
    private val first = NativeUi.button(context, "") {}
    private val second = NativeUi.button(context, "") {}

    init {
        addView(NativeUi.column(context).apply {
            addView(title)
            addView(supporting, LinearLayout.LayoutParams(-1, -2).apply { topMargin = NativeUi.dp(context, 4) })
            addView(metadata, LinearLayout.LayoutParams(-1, -2).apply { topMargin = NativeUi.dp(context, 8) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(first, LinearLayout.LayoutParams(0, -2, 1f))
                addView(second, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = NativeUi.dp(context, 8) })
            })
        })
    }

    fun bind(title: String, supporting: String = "", metadata: String = "",
             action: String = "", onAction: () -> Unit = {}, secondary: String = "", onSecondary: () -> Unit = {},
             enabled: Boolean = true, secondaryEnabled: Boolean = true) {
        this.title.text = title
        this.supporting.text = supporting
        this.supporting.visibility = if (supporting.isBlank()) View.GONE else View.VISIBLE
        this.metadata.text = metadata
        this.metadata.visibility = if (metadata.isBlank()) View.GONE else View.VISIBLE
        first.text = action
        first.visibility = if (action.isBlank()) View.GONE else View.VISIBLE
        first.setOnClickListener { onAction() }
        first.isEnabled = enabled
        second.text = secondary
        second.visibility = if (secondary.isBlank()) View.GONE else View.VISIBLE
        second.setOnClickListener { onSecondary() }
        second.isEnabled = secondaryEnabled
    }
}
