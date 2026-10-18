package com.luoh.music.lrc

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A fill/stroke drawable with continuous ("squircle") corners.
 *
 * Android's ShapeDrawable only knows circular corners. This samples the
 * superellipse |x|^n + |y|^n = 1 with n = 4 for each corner, the closest
 * cheap approximation of iOS's continuous corner curve. Straight edges meet
 * the curve with a matching tangent, so the joins stay soft instead of
 * showing the corner as a quarter circle.
 */
class SquircleDrawable @JvmOverloads constructor(
    fillColor: Int = Color.TRANSPARENT,
    radiusPx: Float = 0f,
    strokeColor: Int = Color.TRANSPARENT,
    strokeWidthPx: Float = 0f,
    corners: Int = CORNER_ALL
) : Drawable() {

    companion object {
        const val CORNER_ALL = 0
        const val CORNER_TOP = 1
        const val CORNER_BOTTOM = 2
        const val CORNER_NONE = 3

        private const val CURVE_STEPS = 24
        private const val SUPER_ELLIPSE_POWER = 0.5f

        /** A white fill used as a ripple mask; the ripple then follows the squircle. */
        fun mask(radiusPx: Float, corners: Int = CORNER_ALL): SquircleDrawable =
            SquircleDrawable(Color.WHITE, radiusPx, corners = corners)

        fun fill(color: Int, radiusPx: Float, corners: Int = CORNER_ALL): SquircleDrawable =
            SquircleDrawable(color, radiusPx, corners = corners)

        fun stroked(
            fillColor: Int,
            radiusPx: Float,
            strokeColor: Int,
            strokeWidthPx: Float,
            corners: Int = CORNER_ALL
        ): SquircleDrawable = SquircleDrawable(fillColor, radiusPx, strokeColor, strokeWidthPx, corners)

        /**
         * Builds the same path used by [SquircleDrawable] for callers that
         * need an outline, a clip, or a custom blend.
         */
        fun path(
            width: Float,
            height: Float,
            radiusPx: Float,
            corners: Int = CORNER_ALL
        ): Path {
            val left = 0f
            val top = 0f
            val right = width
            val bottom = height
            val maxRadius = min(width, height) / 2f
            val radius = radiusPx.coerceIn(0f, maxRadius)
            val topRadius = if (corners == CORNER_BOTTOM || corners == CORNER_NONE) 0f else radius
            val bottomRadius = if (corners == CORNER_TOP || corners == CORNER_NONE) 0f else radius

            val path = Path()
            if (width <= 0f || height <= 0f) return path
            path.moveTo(left + topRadius, top)
            path.lineTo(right - topRadius, top)
            if (topRadius > 0f) {
                // Top right: from the top edge to the right edge.
                corner(path, right - topRadius, top + topRadius, topRadius, TOP_RIGHT, reverse = true)
            }
            path.lineTo(right, bottom - bottomRadius)
            if (bottomRadius > 0f) {
                corner(path, right - bottomRadius, bottom - bottomRadius, bottomRadius, BOTTOM_RIGHT, reverse = false)
            }
            path.lineTo(left + bottomRadius, bottom)
            if (bottomRadius > 0f) {
                corner(path, left + bottomRadius, bottom - bottomRadius, bottomRadius, BOTTOM_LEFT, reverse = true)
            }
            path.lineTo(left, top + topRadius)
            if (topRadius > 0f) {
                corner(path, left + topRadius, top + topRadius, topRadius, TOP_LEFT, reverse = false)
            }
            path.close()
            return path
        }

        private const val TOP_RIGHT = 0
        private const val BOTTOM_RIGHT = 1
        private const val BOTTOM_LEFT = 2
        private const val TOP_LEFT = 3

        /**
         * Appends one quarter of the superellipse. Each parameterization runs
         * from the horizontal edge to the vertical edge, so the caller only
         * needs to pick the traversal direction that keeps the path clockwise.
         */
        private fun corner(
            path: Path,
            centerX: Float,
            centerY: Float,
            radius: Float,
            which: Int,
            reverse: Boolean
        ) {
            for (step in 1..CURVE_STEPS) {
                val progress = step.toDouble() / CURVE_STEPS
                val angle = (Math.PI / 2.0) * (if (reverse) 1.0 - progress else progress)
                val cos = cos(angle).toFloat().coerceAtLeast(0f).pow(SUPER_ELLIPSE_POWER)
                val sin = sin(angle).toFloat().coerceAtLeast(0f).pow(SUPER_ELLIPSE_POWER)
                val x: Float
                val y: Float
                when (which) {
                    TOP_RIGHT -> {
                        x = centerX + radius * cos
                        y = centerY - radius * sin
                    }
                    BOTTOM_RIGHT -> {
                        x = centerX + radius * cos
                        y = centerY + radius * sin
                    }
                    BOTTOM_LEFT -> {
                        x = centerX - radius * cos
                        y = centerY + radius * sin
                    }
                    else -> {
                        x = centerX - radius * cos
                        y = centerY - radius * sin
                    }
                }
                path.lineTo(x, y)
            }
        }
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var cornerRadiusPx: Float = radiusPx
    private var cornerMode: Int = corners
    private var cachedPath: Path? = null
    private var cachedStrokePath: Path? = null

    init {
        fillPaint.color = fillColor
        strokePaint.color = strokeColor
        strokePaint.strokeWidth = strokeWidthPx
    }

    fun setFillColor(color: Int) {
        if (fillPaint.color == color) return
        fillPaint.color = color
        invalidateSelf()
    }

    /** The current fill, for previews and tests that only need the colour. */
    fun fillColor(): Int = fillPaint.color

    fun setStrokeColor(color: Int) {
        if (strokePaint.color == color) return
        strokePaint.color = color
        invalidateSelf()
    }

    fun setStrokeWidth(widthPx: Float) {
        if (strokePaint.strokeWidth == widthPx) return
        strokePaint.strokeWidth = widthPx
        cachedPath = null
        cachedStrokePath = null
        invalidateSelf()
    }

    fun setRadius(radiusPx: Float) {
        if (cornerRadiusPx == radiusPx) return
        cornerRadiusPx = radiusPx
        cachedPath = null
        cachedStrokePath = null
        invalidateSelf()
    }

    fun setCorners(corners: Int) {
        if (this.cornerMode == corners) return
        this.cornerMode = corners
        cachedPath = null
        cachedStrokePath = null
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        cachedPath = null
        cachedStrokePath = null
    }

    private fun path(): Path {
        cachedPath?.let { return it }
        val rect = bounds
        return path(
            rect.width().toFloat(),
            rect.height().toFloat(),
            cornerRadiusPx,
            cornerMode
        ).also {
            it.offset(rect.left.toFloat(), rect.top.toFloat())
            cachedPath = it
        }
    }

    /**
     * The stroke is drawn inside the bounds: a stroke centred on the edge
     * loses half its width to the view's clip, which makes a 1 dp hairline
     * render as a lighter 0.5 dp line.
     */
    private fun strokePath(): Path {
        cachedStrokePath?.let { return it }
        val rect = bounds
        val inset = strokePaint.strokeWidth / 2f
        return path(
            (rect.width() - inset * 2f).coerceAtLeast(0f),
            (rect.height() - inset * 2f).coerceAtLeast(0f),
            (cornerRadiusPx - inset).coerceAtLeast(0f),
            cornerMode
        ).also {
            it.offset(rect.left + inset, rect.top + inset)
            cachedStrokePath = it
        }
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty()) return
        if (fillPaint.color != Color.TRANSPARENT && fillPaint.alpha > 0) {
            canvas.drawPath(path(), fillPaint)
        }
        if (strokePaint.strokeWidth > 0f && strokePaint.alpha > 0) {
            canvas.drawPath(strokePath(), strokePaint)
        }
    }

    override fun getOutline(outline: Outline) {
        if (bounds.isEmpty()) {
            outline.setEmpty()
            return
        }
        if (cornerRadiusPx <= 0f) {
            outline.setRect(bounds)
            return
        }
        try {
            outline.setConvexPath(path())
        } catch (_: RuntimeException) {
            // An outline is optional; keep the card drawable even if a
            // particular device (or a test renderer) refuses this path.
            outline.setRoundRect(bounds, min(bounds.width(), bounds.height()) / 2f)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
