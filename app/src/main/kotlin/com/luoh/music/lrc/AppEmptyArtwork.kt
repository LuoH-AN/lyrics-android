package com.luoh.music.lrc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources

/**
 * The empty state's illustration: a soft superellipse medallion with the app's
 * music glyph, plus two small accents that orbit the shape. Drawn, never a
 * bitmap, so it follows the seed colour and dark mode.
 */
class AppEmptyArtwork @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyph = AppCompatResources.getDrawable(context, R.drawable.ic_home_music)?.mutate()
    private val glyphRect = Rect()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        applyScheme(AppTheme.scheme(context))
    }

    fun applyScheme(scheme: AppScheme) {
        fillPaint.color = scheme.primaryContainer
        accentPaint.color = scheme.primary
        strokePaint.color = scheme.primary
        strokePaint.strokeWidth = DesignTokens.dp(context, 1.5f)
        glyph?.setTint(scheme.onPrimaryContainer)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = DesignTokens.dp(context, 112).toInt()
        setMeasuredDimension(
            resolveSize(desired, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        val shapeSide = side * 0.72f
        val left = (width - shapeSide) / 2f
        val top = (height - shapeSide) / 2f
        val shape: Path = SquircleDrawable.path(shapeSide, shapeSide, shapeSide * 0.34f)
        canvas.save()
        canvas.rotate(-10f, width / 2f, height / 2f)
        canvas.translate(left, top)
        canvas.drawPath(shape, fillPaint)
        canvas.restore()

        // Two accents: one solid dot, one hairline ring, as on the empty-state
        // shapes the system uses instead of stock illustrations.
        canvas.save()
        strokePaint.alpha = 140
        canvas.drawCircle(width * 0.19f, height * 0.29f, side * 0.028f, accentPaint)
        canvas.drawCircle(width * 0.80f, height * 0.70f, side * 0.055f, strokePaint)
        strokePaint.alpha = 255
        canvas.restore()

        val glyphSide = (side * 0.30f).toInt()
        glyph?.let {
            val cx = width / 2
            val cy = height / 2
            glyphRect.set(cx - glyphSide / 2, cy - glyphSide / 2, cx + glyphSide / 2, cy + glyphSide / 2)
            it.bounds = glyphRect
            it.draw(canvas)
        }
    }
}
