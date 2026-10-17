package com.luoh.music.lrc

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

/**
 * A card whose surface is a continuous-corner fill instead of a Material
 * shape. The outline is a convex squircle, so elevation shadows and
 * `clipToOutline` follow the same curve as the fill.
 */
class SquircleCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val surface = SquircleDrawable()

    init {
        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.SquircleCardView,
            defStyleAttr,
            0
        )
        val radius = a.getDimension(
            R.styleable.SquircleCardView_squircleRadius,
            DesignTokens.dp(context, DesignTokens.RADIUS_CARD)
        )
        val fill = a.getColor(
            R.styleable.SquircleCardView_squircleFill,
            DesignTokens.card(context)
        )
        val stroke = a.getColor(
            R.styleable.SquircleCardView_squircleStroke,
            Color.TRANSPARENT
        )
        val strokeWidth = a.getDimension(
            R.styleable.SquircleCardView_squircleStrokeWidth,
            0f
        )
        val corners = a.getInt(
            R.styleable.SquircleCardView_squircleCorners,
            SquircleDrawable.CORNER_ALL
        )
        a.recycle()

        surface.setFillColor(fill)
        surface.setRadius(radius)
        surface.setStrokeColor(stroke)
        surface.setStrokeWidth(strokeWidth)
        surface.setCorners(corners)
        background = surface
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                surface.getOutline(outline)
            }
        }
    }

    fun setSquircleFillColor(color: Int) {
        surface.setFillColor(color)
        invalidateOutline()
    }

    fun setSquircleRadiusDp(radiusDp: Float) {
        surface.setRadius(DesignTokens.dp(context, radiusDp))
        invalidateOutline()
    }

    fun setSquircleStroke(color: Int, widthDp: Float) {
        surface.setStrokeColor(color)
        surface.setStrokeWidth(DesignTokens.dp(context, widthDp))
        invalidateOutline()
    }

    fun setSquircleCorners(corners: Int) {
        surface.setCorners(corners)
        invalidateOutline()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
    }
}
