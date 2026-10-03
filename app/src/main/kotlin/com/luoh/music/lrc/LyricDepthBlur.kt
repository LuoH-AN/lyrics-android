package com.luoh.music.lrc

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Share a small set of native blur effects instead of allocating one per row and frame. */
internal class LyricDepthBlur {
    private val levels = WeakHashMap<LyricLineView, Int>()
    private val renderer = if (Build.VERSION.SDK_INT >= 31) Renderer() else null

    fun radiusOf(row: LyricLineView): Float = (levels[row] ?: 0) * STEP_DP

    fun setRadius(row: LyricLineView, radiusDp: Float) {
        val level = if (renderer == null) 0 else
            (radiusDp.coerceIn(0f, MAX_RADIUS_DP) / STEP_DP).roundToInt()
        if ((levels[row] ?: 0) == level) return
        if (level == 0) levels.remove(row) else levels[row] = level
        renderer?.apply(row, level)
    }

    @RequiresApi(31)
    private class Renderer {
        private val effects = mutableMapOf<Float, RenderEffect>()

        fun apply(row: LyricLineView, level: Int) {
            val radius = level * STEP_DP * row.resources.displayMetrics.density
            row.setRenderEffect(if (level == 0) null else effects.getOrPut(radius) {
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            })
        }
    }

    companion object {
        private const val STEP_DP = .125f
        const val MAX_RADIUS_DP = .25f

        fun radiusForDistance(distance: Int): Float = when {
            distance < 3 -> 0f
            distance == 3 -> .125f
            else -> MAX_RADIUS_DP
        }
    }
}
