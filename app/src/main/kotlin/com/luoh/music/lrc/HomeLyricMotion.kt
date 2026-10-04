package com.luoh.music.lrc

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One timeline keeps scrolling, staggered rows and focus styling interruptible together. */
internal class HomeLyricMotion(
    private val scroll: ScrollView,
    private val rows: List<LyricLineView>,
    private val animationsEnabled: () -> Boolean = { ValueAnimator.areAnimatorsEnabled() }
) {
    var style: MotionStyle = MotionStyle()
    private var animator: ValueAnimator? = null
    private var focusedIndex = -1
    private var depthEnabled = false
    private val depthBlur = LyricDepthBlur()
    private val easing = PathInterpolator(.2f, .7f, .2f, 1f)
    val isRunning: Boolean get() = animator != null

    private data class RowStart(
        val row: LyricLineView,
        val scale: Float,
        val alpha: Float,
        val translation: Float,
        val blur: Float,
        val targetScale: Float,
        val targetAlpha: Float,
        val targetBlur: Float,
        val delayMs: Long
    )

    fun moveTo(index: Int, targetScrollY: Int?, animate: Boolean) {
        stopAnimator()
        focusedIndex = index
        depthEnabled = targetScrollY != null && style.depthBlur
        val startScroll = scroll.scrollY
        val maxScroll = max(0, (scroll.getChildAt(0)?.height ?: 0) - scroll.height)
        val target = targetScrollY?.coerceIn(0, maxScroll) ?: startScroll
        val animated = animate && style.easedScroll
        if (!animated || !animationsEnabled() || scroll.height == 0 || abs(target - startScroll) > scroll.height) {
            if (targetScrollY != null) scroll.scrollTo(0, target)
            settleRows()
            return
        }
        val firstVisible = min(startScroll, target) - scroll.height / 4
        val lastVisible = max(startScroll, target) + scroll.height * 5 / 4
        val starts = rows.mapIndexedNotNull { i, row ->
            val distance = distance(i)
            row.pivotX = 0f
            row.pivotY = row.height / 2f
            if (!depthEnabled || i == index) depthBlur.setRadius(row, 0f)
            if (row.bottom < firstVisible || row.top > lastVisible) {
                settleRow(i, row)
                null
            } else RowStart(row, row.scaleX, row.alpha, row.translationY, depthBlur.radiusOf(row),
                scale(distance), opacity(distance), targetBlur(distance, depthEnabled),
                if (targetScrollY == null || !style.stagger) 0L else min(distance, 4) * style.staggerDelayMs.toLong())
        }
        val transitionDuration = style.scrollDurationMs.toLong()
        val durationMs = transitionDuration + (starts.maxOfOrNull { it.delayMs } ?: 0L)
        val motion = ValueAnimator.ofFloat(0f, durationMs.toFloat()).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { frame ->
                val time = frame.animatedValue as Float
                fun progress(delay: Long, duration: Long): Float =
                    easing.getInterpolation(((time - delay) / duration).coerceIn(0f, 1f))
                if (targetScrollY != null) {
                    scroll.scrollTo(0, (startScroll + (target - startScroll) * progress(0L, transitionDuration)).toInt())
                }
                starts.forEach { start ->
                    val travel = progress(start.delayMs, transitionDuration)
                    val fade = progress(start.delayMs, 800L)
                    // One curve lets the line grow into place instead of bouncing ahead of the scroll.
                    start.row.scaleX = start.scale + (start.targetScale - start.scale) * travel
                    start.row.scaleY = start.row.scaleX
                    start.row.alpha = start.alpha + (start.targetAlpha - start.alpha) * fade
                    depthBlur.setRadius(start.row, start.blur + (start.targetBlur - start.blur) * fade)
                    // Compensate the shared scroll so each row follows on its own delayed curve.
                    // Starting from its current transform also preserves continuity on rapid changes.
                    start.row.translationY = (scroll.scrollY - startScroll) - (target - startScroll) * travel +
                        start.translation * (1f - travel)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null
                    settleRows()
                }
            })
        }
        animator = motion
        motion.start()
    }

    fun cancel() {
        stopAnimator()
        depthEnabled = false
        settleRows()
    }

    private fun stopAnimator() {
        val previous = animator
        animator = null
        previous?.cancel()
    }

    private fun settleRows() = rows.forEachIndexed(::settleRow)

    private fun settleRow(index: Int, row: LyricLineView) {
        val distance = distance(index)
        row.pivotX = 0f
        row.pivotY = row.height / 2f
        row.translationY = 0f
        row.scaleX = scale(distance)
        row.scaleY = row.scaleX
        row.alpha = opacity(distance)
        val nearViewport = row.bottom >= scroll.scrollY - scroll.height / 4 &&
            row.top <= scroll.scrollY + scroll.height * 5 / 4
        depthBlur.setRadius(row, if (depthEnabled && nearViewport) targetBlur(distance, true) else 0f)
    }

    private fun distance(index: Int) = if (focusedIndex < 0) index + 1 else abs(index - focusedIndex)

    /** 远景模糊半径 = 距离档位 × 强度百分比。 */
    private fun targetBlur(distance: Int, enabled: Boolean): Float =
        if (enabled) LyricDepthBlur.radiusForDistance(distance) * style.blurPercent / 100f else 0f

    /** 行间缩放 = 距离档位向静止值收缩的幅度 × 强度百分比。 */
    private fun scale(distance: Int): Float {
        if (!style.scale || distance == 0) return 1f
        val resting = when (distance) { 1 -> .95f; 2 -> .92f; else -> .9f }
        return 1f - (1f - resting) * style.scalePercent / 100f
    }
    private fun opacity(distance: Int) = when (distance) { 0 -> 1f; 1 -> .78f; 2 -> .62f; 3 -> .48f; else -> .36f }
}
