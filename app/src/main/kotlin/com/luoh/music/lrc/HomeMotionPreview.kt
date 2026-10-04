package com.luoh.music.lrc

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * 设置页里的动效预览：用与主页同一个 [HomeLyricMotion] 驱动几行示例歌词，自动逐行切换，
 * 改动任何设置都会立刻按新样式静置，看到的就是主页真实观感。
 */
class HomeMotionPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = false
    }
    private val track = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(dp(16), dp(PRESET_PADDING_DP), dp(16), dp(PRESET_PADDING_DP))
    }
    private val rows = mutableListOf<LyricLineView>()
    private val motion = HomeLyricMotion(scroll, rows)
    private var index = FIRST_INDEX
    private var running = false

    private val advance = object : Runnable {
        override fun run() {
            if (!running) return
            step()
            postDelayed(this, STEP_MS)
        }
    }

    /** 拖滑杆会连续触发套用，合并成每帧一次，避免堆积。 */
    private val settleTask = Runnable { motion.moveTo(index, target(index), animate = false) }

    init {
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        scroll.addView(track, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        SAMPLE.forEach { text ->
            val row = LyricLineView(context).apply {
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setPaddingRelative(0, dp(6), 0, dp(6))
                setLineSpacing(0f, 1.2f)
                bind(LyricLine(0L, text), "original")
            }
            rows += row
            track.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    /** 立即套用新样式，并按新样式把当前行静置到焦点位置。 */
    fun applyStyle(style: MotionStyle) {
        motion.style = style
        motion.cancel()
        settle()
    }

    fun start() {
        if (running) return
        running = true
        index = FIRST_INDEX
        settle()
        postDelayed(advance, STEP_MS)
    }

    fun stop() {
        running = false
        removeCallbacks(advance)
        removeCallbacks(settleTask)
        motion.cancel()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // 首次量出高度后才知道该滚到哪，重新静置一次。
        if (changed) settle()
    }

    private fun settle() {
        removeCallbacks(settleTask)
        post(settleTask)
    }

    private fun step() {
        index = if (index >= LAST_INDEX) FIRST_INDEX else index + 1
        motion.moveTo(index, target(index), animate = true)
    }

    /** 与主页一致：当前行居中在视口 42% 高处。 */
    private fun target(index: Int): Int {
        val row = rows.getOrNull(index) ?: return 0
        val content = scroll.getChildAt(0)?.height ?: return 0
        if (row.height == 0 || scroll.height == 0) return 0
        val maxScroll = (content - scroll.height).coerceAtLeast(0)
        return (row.top + row.height / 2 - (scroll.height * .42f).roundToInt()).coerceIn(0, maxScroll)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    private companion object {
        const val STEP_MS = 1600L
        const val FIRST_INDEX = 1
        const val LAST_INDEX = 4
        const val PRESET_PADDING_DP = 72
        val SAMPLE = listOf(
            "夜色才刚刚开始",
            "跟随这段旋律",
            "陪我留在这一刻",
            "让音乐缓缓流淌",
            "再多停留一会儿",
            "在城市的灯火下"
        )
    }
}
