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
    private var animator: ValueAnimator? = null
    private var focusedIndex = -1
    private val easing = PathInterpolator(.22f, 1f, .36f, 1f)
    val isRunning: Boolean get() = animator != null

    private data class RowStart(
        val row: LyricLineView,
        val scale: Float,
        val alpha: Float,
        val translation: Float,
        val targetScale: Float,
        val targetAlpha: Float,
        val delayMs: Long
    )

    fun moveTo(index: Int, targetScrollY: Int?, animate: Boolean) {
        stopAnimator()
        focusedIndex = index
        val startScroll = scroll.scrollY
        val maxScroll = max(0, (scroll.getChildAt(0)?.height ?: 0) - scroll.height)
        val target = targetScrollY?.coerceIn(0, maxScroll) ?: startScroll
        if (!animate || !animationsEnabled() || scroll.height == 0 || abs(target - startScroll) > scroll.height) {
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
            if (row.bottom < firstVisible || row.top > lastVisible) {
                settleRow(i, row)
                null
            } else RowStart(row, row.scaleX, row.alpha, row.translationY,
                scale(distance), opacity(distance), if (targetScrollY == null) 0L else min(distance, 4) * 32L)
        }
        val scrollDuration = 560L
        val durationMs = scrollDuration + (starts.maxOfOrNull { it.delayMs } ?: 0L)
        val motion = ValueAnimator.ofFloat(0f, durationMs.toFloat()).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { frame ->
                val time = frame.animatedValue as Float
                fun progress(delay: Long, duration: Long): Float =
                    easing.getInterpolation(((time - delay) / duration).coerceIn(0f, 1f))
                if (targetScrollY != null) {
                    scroll.scrollTo(0, (startScroll + (target - startScroll) * progress(0L, scrollDuration)).toInt())
                }
                starts.forEach { start ->
                    val travel = progress(start.delayMs, scrollDuration)
                    val focus = progress(start.delayMs, 380L)
                    start.row.scaleX = start.scale + (start.targetScale - start.scale) * focus
                    start.row.scaleY = start.row.scaleX
                    start.row.alpha = start.alpha + (start.targetAlpha - start.alpha) * focus
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
    }

    private fun distance(index: Int) = if (focusedIndex < 0) index + 1 else abs(index - focusedIndex)
    private fun scale(distance: Int) = when (distance) { 0 -> 1f; 1 -> .92f; 2 -> .87f; else -> .82f }
    private fun opacity(distance: Int) = when (distance) { 0 -> 1f; 1 -> .7f; 2 -> .55f; 3 -> .4f; else -> .26f }
}
