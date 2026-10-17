package com.luoh.music.lrc

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.google.android.material.button.MaterialButton

/**
 * A Material button row with an iOS style segmented control's moving pill.
 *
 * The children stay the XML [MaterialButton]s, so existing ids, click
 * listeners, checked state and accessibility keep working. The pill is a
 * separate view behind them; it travels on a retargetable spring and the text
 * colors cross-fade at the same time.
 */
class SegmentedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val DURATION_TEXT = 180L
        private const val HEIGHT_DP = 44f
        private const val INSET_DP = 4f
        private val EASE_OUT_CUBIC = PathInterpolator(0.215f, 0.61f, 0.355f, 1f)
        private val ARGB = ArgbEvaluator()
    }

    private val buttons = ArrayList<MaterialButton>()
    private val indicator = View(context)
    private val indicatorDrawable = SquircleDrawable(
        DesignTokens.accent(context),
        DesignTokens.dp(context, DesignTokens.RADIUS_PILL)
    )
    private val inset = DesignTokens.dp(context, INSET_DP)
    private var accentColor = DesignTokens.accent(context)
    private var selectedIndex = -1
    private var segmentWidth = 0
    private var textAnimator: ValueAnimator? = null
    private var accentAnimator: ValueAnimator? = null
    private var indicatorSpring: SpringAnimation? = null
    private var indicatorAnimating = false

    init {
        background = SquircleDrawable(
            DesignTokens.block(context),
            DesignTokens.dp(context, DesignTokens.RADIUS_PILL)
        )
        indicator.background = indicatorDrawable
        indicator.isClickable = false
        indicator.isFocusable = false
        indicator.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(indicator)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        buttons.clear()
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is MaterialButton) {
                styleButton(child)
                buttons.add(child)
            }
        }
        if (buttons.isEmpty()) return
        val checked = buttons.indexOfFirst { it.isChecked }
        setSelectedIndex(if (checked >= 0) checked else 0, animate = false)
    }

    private fun styleButton(button: MaterialButton) {
        button.isAllCaps = false
        button.minWidth = 0
        button.minHeight = 0
        button.minimumWidth = 0
        button.minimumHeight = 0
        button.insetTop = 0
        button.insetBottom = 0
        button.stateListAnimator = null
        button.elevation = 0f
        button.gravity = Gravity.CENTER
        button.setPadding(0, 0, 0, 0)
        button.cornerRadius = DesignTokens.dp(context, DesignTokens.RADIUS_PILL).toInt()
        button.strokeWidth = 0
        button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        button.setRippleColor(
            ColorStateList.valueOf(withAlpha(DesignTokens.accent(context), 28))
        )
        button.isCheckable = true
        button.setToggleCheckedStateOnClick(false)
        button.isChecked = false
        button.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
    }

    /** Keeps the pill under the segment whose button carries [id]. */
    fun selectById(id: Int) {
        val index = buttons.indexOfFirst { it.id == id }
        if (index >= 0) setSelectedIndex(index, animate = true)
    }

    fun setSelectedIndex(index: Int, animate: Boolean = true) {
        if (index !in buttons.indices) return
        val changed = index != selectedIndex
        selectedIndex = index
        val onAccent = DesignTokens.onAccent(context)
        val unselected = ContextCompat.getColor(context, R.color.text_secondary)
        buttons.forEachIndexed { buttonIndex, button ->
            button.isChecked = buttonIndex == index
            button.isSelected = buttonIndex == index
            if (!animate || !changed) {
                button.setTextColor(if (buttonIndex == index) onAccent else unselected)
            }
        }
        if (animate && changed) animateTextColors(onAccent, unselected)
        positionIndicator(animate && changed)
    }

    /** Called when the custom accent changes without recreating the page. */
    fun setAccentColor(color: Int) {
        if (accentColor == color) return
        val from = accentColor
        accentColor = color
        accentAnimator?.cancel()
        if (ValueAnimator.areAnimatorsEnabled()) {
            accentAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, color).apply {
                duration = 180
                addUpdateListener {
                    indicatorDrawable.setFillColor(it.animatedValue as Int)
                }
                start()
            }
        } else {
            indicatorDrawable.setFillColor(color)
        }
        val ripple = ColorStateList.valueOf(withAlpha(color, 28))
        buttons.forEachIndexed { index, button ->
            button.setRippleColor(ripple)
            if (index == selectedIndex) button.setTextColor(DesignTokens.onAccent(context))
        }
    }

    private fun animateTextColors(onAccent: Int, unselected: Int) {
        textAnimator?.cancel()
        val start = IntArray(buttons.size) { buttons[it].currentTextColor }
        val end = IntArray(buttons.size) { if (it == selectedIndex) onAccent else unselected }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            buttons.forEachIndexed { index, button -> button.setTextColor(end[index]) }
            return
        }
        textAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_TEXT
            interpolator = EASE_OUT_CUBIC
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                buttons.forEachIndexed { index, button ->
                    button.setTextColor(ARGB.evaluate(fraction, start[index], end[index]) as Int)
                }
            }
            start()
        }
    }

    private fun positionIndicator(animate: Boolean) {
        if (segmentWidth <= 0) return
        val target = (selectedIndex.coerceAtLeast(0) * segmentWidth).toFloat()
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            indicatorSpring?.cancel()
            indicatorAnimating = false
            indicator.translationX = target
            return
        }
        indicatorAnimating = true
        indicatorSpring().animateToFinalPosition(target)
    }

    private fun indicatorSpring(): SpringAnimation =
        indicatorSpring ?: Springs.spring(indicator, DynamicAnimation.TRANSLATION_X).apply {
            addEndListener { _, _, _, _ -> indicatorAnimating = false }
        }.also { indicatorSpring = it }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            DesignTokens.dp(context, HEIGHT_DP).toInt()
        }
        val innerWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val innerHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        val count = buttons.size
        segmentWidth = if (count > 0) innerWidth / count else innerWidth
        buttons.forEach { button ->
            button.measure(
                MeasureSpec.makeMeasureSpec(segmentWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(innerHeight, MeasureSpec.EXACTLY)
            )
        }
        indicator.measure(
            MeasureSpec.makeMeasureSpec(
                (segmentWidth - inset * 2f).coerceAtLeast(0f).toInt(),
                MeasureSpec.EXACTLY
            ),
            MeasureSpec.makeMeasureSpec(
                (innerHeight - inset * 2f).coerceAtLeast(0f).toInt(),
                MeasureSpec.EXACTLY
            )
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val innerHeight = height - paddingTop - paddingBottom
        buttons.forEachIndexed { index, button ->
            val start = paddingLeft + index * segmentWidth
            button.layout(start, paddingTop, start + segmentWidth, paddingTop + innerHeight)
        }
        val indicatorLeft = paddingLeft + inset.toInt()
        val indicatorTop = paddingTop + inset.toInt()
        indicator.layout(
            indicatorLeft,
            indicatorTop,
            indicatorLeft + (segmentWidth - inset * 2f).coerceAtLeast(0f).toInt(),
            indicatorTop + (innerHeight - inset * 2f).coerceAtLeast(0f).toInt()
        )
        // A layout pass during the pill animation must not snap it to the
        // destination; only size changes need a fresh base position.
        if (!indicatorAnimating) positionIndicator(false)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP && isEnabled && buttons.isNotEmpty()) {
            val index = indexAt(ev.x)
            if (index >= 0) setSelectedIndex(index, animate = true)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun indexAt(x: Float): Int {
        if (segmentWidth <= 0 || x < paddingLeft || x > width - paddingRight) return -1
        val index = ((x - paddingLeft) / segmentWidth).toInt()
        return index.coerceIn(0, buttons.lastIndex)
    }

    override fun onDetachedFromWindow() {
        textAnimator?.cancel()
        accentAnimator?.cancel()
        indicatorSpring?.cancel()
        super.onDetachedFromWindow()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
