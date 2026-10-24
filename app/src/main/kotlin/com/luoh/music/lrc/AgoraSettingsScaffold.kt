package com.luoh.music.lrc

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ConcatAdapter
import com.google.android.material.textview.MaterialTextView
import kotlin.math.roundToInt

/** SettingsScaffold.kt: one scroll owner and one title glyph, with 66dp linear travel. */
class AgoraSettingsScaffold @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val easing = PathInterpolator(.2f, 0f, .5f, 1f)
    private var statusTop = 0
    private var safeBottom = 0
    private var safeLeft = 0
    private var safeRight = 0
    private var body: View? = null
    private var scroll: NestedScrollView? = null
    private var list: RecyclerView? = null
    private var header: HeaderAdapter? = null
    private val bar = FrameLayout(context).apply {
        id = R.id.settings_toolbar
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_bg))
    }
    val titleView = MaterialTextView(context).apply {
        id = R.id.settings_header
        setTextAppearance(R.style.AgoraExpandedTitle)
        includeFontPadding = false
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        ViewCompat.setAccessibilityHeading(this, true)
    }
    var title: CharSequence
        get() = titleView.text
        set(value) { titleView.text = value; requestLayout() }
    var onBack: () -> Unit = {}
    private val back = NativeUi.iconButton(context, R.drawable.ic_native_back, "返回") { onBack() }.apply {
        id = R.id.settings_back
        isCheckable = false
        iconSize = dp(24)
        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_surface_high))
    }
    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_bg))
        bar.addView(back, LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.START).apply {
            marginStart = dp(16); topMargin = dp(12)
        })
        addView(bar, LayoutParams(-1, dp(64)))
        addView(titleView, LayoutParams(-2, -2))
        if (attrs != null) {
            val content = NativeUi.column(context, 24).apply { id = R.id.settings_content }
            setColumn(NestedScrollView(context).apply {
                id = R.id.settings_scroll
                isFillViewport = true
                overScrollMode = OVER_SCROLL_NEVER
                addView(content)
            })
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            statusTop = bars.top
            safeBottom = maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            safeLeft = bars.left; safeRight = bars.right
            applyScrollInsets()
            requestLayout()
            insets
        }
    }
    private fun dp(value: Int) = NativeUi.dp(context, value)
    fun setColumn(view: NestedScrollView) {
        scroll = view
        mountBody(view)
        view.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> updateTitle() })
        applyScrollInsets()
    }
    fun setList(view: RecyclerView, controls: View? = null) {
        list = view
        header = HeaderAdapter(context, controls)
        view.setTag(R.id.agora_list_header, header)
        mountBody(view)
        view.adapter = ConcatAdapter(requireNotNull(header))
        view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) { updateTitle() }
        })
        applyScrollInsets()
    }
    private fun mountBody(view: View) {
        body?.let(::removeView)
        body = view
        addView(view, 0, LayoutParams(-1, -1, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }
    private fun applyScrollInsets() {
        (scroll?.getChildAt(0) as? ViewGroup)?.let {
            it.setPadding(dp(24), statusTop + dp(154), dp(24), safeBottom + dp(32))
        }
        list?.setPadding(dp(24), 0, dp(24), safeBottom + dp(32))
        header?.setTopSpace(statusTop + dp(154))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val contentWidth = minOf((width - safeLeft - safeRight).coerceAtLeast(0), dp(840))
        body?.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        bar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(statusTop + dp(64), MeasureSpec.EXACTLY))
        val titleWidth = (contentWidth - dp(40)).coerceAtLeast(0)
        var size = 33f
        val scaledDensity = resources.displayMetrics.scaledDensity
        val paint = android.text.TextPaint(titleView.paint)
        paint.textSize = size * scaledDensity
        while (size > 20f && paint.measureText(title.toString()) > titleWidth) {
            size -= 1f
            paint.textSize = size * scaledDensity
        }
        titleView.textSize = size
        titleView.measure(MeasureSpec.makeMeasureSpec(titleWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        body?.let {
            val x = safeLeft + (width - safeLeft - safeRight - it.measuredWidth) / 2
            it.layout(x, 0, x + it.measuredWidth, height)
        }
        // Set the margin before laying out the bar; insets must take effect in the same frame.
        val contentWidth = minOf((width - safeLeft - safeRight).coerceAtLeast(0), dp(840))
        val outerSpace = (width - safeLeft - safeRight - contentWidth) / 2
        (back.layoutParams as LayoutParams).apply {
            topMargin = statusTop + dp(12)
            val direction = this@AgoraSettingsScaffold.layoutDirection
            marginStart = (if (direction == LAYOUT_DIRECTION_RTL) safeRight else safeLeft) + outerSpace + dp(16)
            resolveLayoutDirection(direction)
        }
        bar.forceLayout()
        bar.layout(0, 0, width, statusTop + dp(64))
        titleView.layout(0, 0, titleView.measuredWidth, titleView.measuredHeight)
        updateTitle()
    }
    private fun updateTitle() {
        val travel = dp(66).toFloat()
        val offset = scroll?.scrollY ?: list?.let { view ->
            val manager = view.layoutManager as? LinearLayoutManager
            val first = manager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            when {
                first > 0 -> travel.roundToInt()
                first == 0 -> -(manager?.findViewByPosition(0)?.top ?: 0)
                else -> 0
            }
        } ?: 0
        val fraction = (offset / travel).coerceIn(0f, 1f)
        val eased = easing.getInterpolation(fraction)
        val scale = 1f - (1f - 22f / (titleView.textSize / resources.displayMetrics.scaledDensity)) * eased
        val contentWidth = minOf((width - safeLeft - safeRight).coerceAtLeast(0), dp(840))
        val contentLeft = safeLeft + (width - safeLeft - safeRight - contentWidth) / 2
        val x = contentLeft + dp(24) + dp(46) * eased
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        titleView.pivotX = if (rtl) titleView.width.toFloat() else 0f
        titleView.pivotY = 0f
        titleView.translationX = if (rtl) width - x - titleView.width else x
        titleView.translationY = statusTop + dp(84) - travel * fraction
        titleView.scaleX = scale; titleView.scaleY = scale
    }
    /** A scrolling first item replaces fixed padding and the second AppBar scroll owner. */
    internal class HeaderAdapter(private val context: Context, private val controls: View?) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var topSpace = NativeUi.dp(context, 154)
        private var spacer: View? = null
        override fun getItemCount() = 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(-1, -2)
                addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, topSpace)
                    spacer = this
                })
                controls?.let {
                    (it.parent as? ViewGroup)?.removeView(it)
                    addView(it)
                }
            }
            return object : RecyclerView.ViewHolder(content) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        fun setTopSpace(height: Int) {
            topSpace = height
            spacer?.let { if (it.layoutParams.height != height) it.layoutParams = LinearLayout.LayoutParams(-1, height) }
        }
    }
}
