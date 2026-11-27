package com.luoh.music.lrc.overlay

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

internal class OverlayWindowGeometry(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    fun currentDisplaySize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                return bounds.width() to bounds.height()
            }
        }
        return max(1, context.resources.displayMetrics.widthPixels) to
            max(1, context.resources.displayMetrics.heightPixels)
    }

    /** Ask the overlay window for the fastest mode at its current resolution. */
    @Suppress("DEPRECATION")
    fun highestOverlayRefreshRate(): Float {
        val display = windowManager.defaultDisplay
        val current = display.mode
        val modes = display.supportedModes.map {
            OverlayDisplayMode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
        }
        return preferredOverlayRefreshRate(
            modes,
            OverlayDisplayMode(current.modeId, current.physicalWidth, current.physicalHeight, current.refreshRate)
        )
    }

    fun currentSafeDisplayBounds(root: View?): Rect {
        val (screenWidth, screenHeight) = currentDisplaySize()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Rect(0, 0, screenWidth, screenHeight)
        }
        val metrics = windowManager.currentWindowMetrics
        val insets = (root?.rootWindowInsets ?: metrics.windowInsets)
            .getInsets(WindowInsets.Type.systemBars())
        val bounds = metrics.bounds
        val safe = Rect(
            bounds.left + insets.left,
            bounds.top + insets.top,
            bounds.right - insets.right,
            bounds.bottom - insets.bottom
        )
        return if (safe.width() > 0 && safe.height() > 0) {
            safe
        } else {
            Rect(0, 0, screenWidth, screenHeight)
        }
    }

    fun contentLayoutParams(width: Int, height: Int): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(width, height, Gravity.CENTER)

    fun fittedOverlaySize(
        desiredWidth: Int,
        desiredHeight: Int,
        safeBounds: Rect,
    ): Pair<Int, Int> {
        val screenWidth = safeBounds.width()
        val screenHeight = safeBounds.height()
        val maxWidth = max(1, screenWidth)
        val minWidth = min(dp(120), maxWidth)
        val maxHeight = availableHeight(screenHeight)
        return desiredWidth.coerceIn(minWidth, maxWidth) to
            desiredHeight.coerceIn(min(dp(48), maxHeight), maxHeight)
    }

    fun availableHeight(screenHeight: Int): Int = max(1, screenHeight - dp(8))

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
