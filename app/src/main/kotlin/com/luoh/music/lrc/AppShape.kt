package com.luoh.music.lrc

import android.graphics.RectF
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.CornerSize
import com.google.android.material.shape.CornerTreatment
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.ShapePath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A continuous-corner treatment for Material's shape engine.
 *
 * Android's rounded corners are quarter circles; iOS continuous corners are
 * superellipses. This writes a 4th-power superellipse as a short polyline, so
 * MaterialButton keeps its ripple, mask and shadow machinery while drawing
 * the same curve as [SquircleDrawable].
 */
class SuperellipseCornerTreatment(
    private val exponent: Float = 4f,
    private val steps: Int = 24
) : CornerTreatment() {

    override fun getCornerPath(
        shapePath: ShapePath,
        angle: Float,
        interpolation: Float,
        bounds: RectF,
        size: CornerSize
    ) {
        val limit = min(bounds.width(), bounds.height()) / 2f
        getCornerPath(shapePath, angle, interpolation, size.getCornerSize(bounds).coerceIn(0f, limit))
    }

    override fun getCornerPath(
        shapePath: ShapePath,
        angle: Float,
        interpolation: Float,
        radius: Float
    ) {
        val r = radius * interpolation
        // The origin is the corner point; the path runs from the vertical
        // edge to the horizontal edge, matching RoundedCornerTreatment.
        shapePath.reset(0f, r, 180f, 180f - angle)
        if (r <= 0f) return
        val power = 2.0 / exponent
        for (step in 1..steps) {
            val theta = step.toDouble() / steps * (PI / 2.0)
            val x = (r * (1.0 - cos(theta).pow(power))).toFloat()
            val y = (r * (1.0 - sin(theta).pow(power))).toFloat()
            shapePath.lineTo(x, y)
        }
    }
}

/** Gives a Material button the app's superellipse corner family. */
fun MaterialButton.applySuperellipseShape(radiusPx: Float = 0f) {
    val radius = when {
        radiusPx > 0f -> radiusPx
        cornerRadius > 0 -> cornerRadius.toFloat()
        else -> DesignTokens.dp(context, DesignTokens.RADIUS_XL)
    }
    shapeAppearanceModel = ShapeAppearanceModel.builder()
        .setAllCorners(SuperellipseCornerTreatment())
        .setAllCornerSizes(radius.coerceAtLeast(0f))
        .build()
}
