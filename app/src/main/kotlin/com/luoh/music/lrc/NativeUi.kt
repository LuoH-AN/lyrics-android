package com.luoh.music.lrc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView

/** Agora's tonal surfaces, 24/5dp grouped rows, and shared scrolling title scaffold. */
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
            iconTint = ColorStateList.valueOf(color)
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

    fun toolbar(activity: Context, title: String, onBack: () -> Unit): MaterialToolbar = MaterialToolbar(activity).apply {
        this.title = title
        setNavigationIcon(R.drawable.ic_native_back)
        navigationContentDescription = "返回"
        setNavigationOnClickListener { onBack() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(activity, 64))
    }

    fun confirm(context: Context, title: String, message: String, positive: String, onConfirm: () -> Unit): AlertDialog =
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton(positive) { _, _ -> onConfirm() }
            .show()

    fun screen(activity: AppCompatActivity, title: String, onBack: () -> Unit = { activity.finish() }): LinearLayout {
        val content = column(activity, 24)
        scaffold(activity, title, NestedScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }, onBack)
        return content
    }

    fun screen(page: NativePage, title: String, onBack: () -> Unit = { page.finish() }): LinearLayout {
        val context = page.uiContext
        val content = column(context, 24)
        scaffold(context, title, NestedScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }, onBack, page::setContentView)
        return content
    }

    fun listScreen(page: NativePage, title: String, header: View? = null): RecyclerView = RecyclerView(page.uiContext).apply {
        val context = page.uiContext
        layoutManager = LinearLayoutManager(context)
        itemAnimator = null
        clipToPadding = false
        setPadding(dp(context, 24), dp(context, 12), dp(context, 24), dp(context, 32))
        val list = this
        val body = if (header == null) list else column(context, 0).apply {
            addView(header)
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        scaffold(context, title, body, { page.onBackPressedDispatcher.onBackPressed() }, page::setContentView)
    }

    fun listScreen(activity: AppCompatActivity, title: String, header: View? = null): RecyclerView = RecyclerView(activity).apply {
        layoutManager = LinearLayoutManager(activity)
        itemAnimator = null
        clipToPadding = false
        setPadding(dp(activity, 24), dp(activity, 12), dp(activity, 24), dp(activity, 32))
        val list = this
        val body = if (header == null) list else column(activity, 0).apply {
            addView(header)
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        scaffold(activity, title, body) { activity.onBackPressedDispatcher.onBackPressed() }
    }

    private fun scaffold(activity: AppCompatActivity, title: String, body: View, onBack: () -> Unit) {
        scaffold(activity, title, body, onBack, activity::setContentView)
    }

    private fun scaffold(activity: Context, title: String, body: View, onBack: () -> Unit, mount: (View) -> Unit) {
        val root = CoordinatorLayout(activity).apply {
            setBackgroundColor(ContextCompat.getColor(activity, R.color.app_bg))
        }
        val appBar = AppBarLayout(activity).apply {
            setBackgroundColor(ContextCompat.getColor(activity, R.color.app_bg))
            elevation = 0f
            stateListAnimator = null
        }
        val collapsing = CollapsingToolbarLayout(activity).apply {
            this.title = title
            minimumHeight = dp(activity, 64)
            setExpandedTitleTextAppearance(R.style.AgoraExpandedTitle)
            setCollapsedTitleTextAppearance(R.style.AgoraCollapsedTitle)
            setExpandedTitleColor(ContextCompat.getColor(activity, R.color.text_primary))
            setCollapsedTitleTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            expandedTitleGravity = Gravity.START or Gravity.BOTTOM
            collapsedTitleGravity = Gravity.START or Gravity.CENTER_VERTICAL
            setExpandedTitleMargin(dp(activity, 24), 0, dp(activity, 24), dp(activity, 24))
            setContentScrimColor(ContextCompat.getColor(activity, R.color.app_bg))
            addView(toolbar(activity, title, onBack), CollapsingToolbarLayout.LayoutParams(-1, dp(activity, 64)).apply {
                collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
            })
        }
        appBar.addView(collapsing, AppBarLayout.LayoutParams(-1, dp(activity, 154)).apply {
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
        })
        root.addView(appBar, CoordinatorLayout.LayoutParams(-1, -2))
        root.addView(body, CoordinatorLayout.LayoutParams(-1, -1).apply {
            behavior = AppBarLayout.ScrollingViewBehavior()
        })
        mount(root)
        if (activity is AppCompatActivity) ThemePrefs.updateSystemBars(activity)
    }

    /** Only visible rows are created; list data and lyric summaries are prepared off the UI thread. */
    fun <T> items(list: RecyclerView, values: List<T>, create: (Context) -> View,
                  retainScroll: Boolean = true, bind: (View, T) -> Unit) {
        val scrollState = if (retainScroll) list.layoutManager?.onSaveInstanceState() else null
        list.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = values.size
            override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder =
                object : RecyclerView.ViewHolder(create(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 8) }
                }) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = bind(holder.itemView, values[position])
        }
        if (scrollState != null) list.layoutManager?.onRestoreInstanceState(scrollState)
        else if (!retainScroll) list.scrollToPosition(0)
    }

    fun group(context: Context, rows: List<View>): LinearLayout = column(context, 0).apply {
        rows.forEachIndexed { index, row ->
            val card = MaterialCardView(context).apply {
                cardElevation = 0f
                strokeWidth = 0
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.app_surface))
                shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setTopLeftCornerSize(dp(context, if (index == 0) 24 else 5).toFloat())
                    .setTopRightCornerSize(dp(context, if (index == 0) 24 else 5).toFloat())
                    .setBottomLeftCornerSize(dp(context, if (index == rows.lastIndex) 24 else 5).toFloat())
                    .setBottomRightCornerSize(dp(context, if (index == rows.lastIndex) 24 else 5).toFloat()).build()
                addView(row)
            }
            addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = if (index == 0) 0 else dp(context, 2) })
        }
    }

    fun styleGroupRows(group: ViewGroup) {
        val rows = (0 until group.childCount).map { group.getChildAt(it) }.filterIsInstance<MaterialCardView>()
        rows.forEachIndexed { index, card ->
            val shape = when {
                rows.size == 1 -> R.style.SettingsRowSingle
                index == 0 -> R.style.SettingsRowFirst
                index == rows.lastIndex -> R.style.SettingsRowLast
                else -> R.style.SettingsRowMiddle
            }
            card.shapeAppearanceModel = ShapeAppearanceModel.builder(card.context, shape, 0).build()
        }
    }

    fun navigationRow(context: Context, title: String, supporting: String, icon: Int, action: () -> Unit): MaterialButton =
        button(context, "$title\n$supporting", action = action).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            cornerRadius = 0
            setIconResource(icon)
            iconSize = dp(context, 24)
            iconPadding = dp(context, 16)
            iconGravity = MaterialButton.ICON_GRAVITY_START
            iconTint = ColorStateList.valueOf(AccentPrefs.accent(context))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 16f
            minHeight = dp(context, 80)
            setPaddingRelative(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
            val caption = android.text.SpannableString(text)
            val start = title.length + 1
            caption.setSpan(android.text.style.ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_secondary)), start, caption.length, 0)
            caption.setSpan(android.text.style.RelativeSizeSpan(13f / 16f), start, caption.length, 0)
            text = caption
        }
}
