package com.luoh.music.lrc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.AttributeSet
import android.view.Gravity
import androidx.core.graphics.ColorUtils
import com.google.android.material.textview.MaterialTextView
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Native text layout and glyph clipping keep karaoke accurate even when a line wraps. */
class LyricLineView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialTextView(context, attrs) {

    private var lyric = LyricLine(0L, "")
    private var primaryLength = 0
    private var wordTiming = false
    private var sungCharacters = 0f
    private val highlight = Path()
    private val selection = Path()
    private var gradientEnd: Int? = null
    private var gradientVertical = false
    private data class GradientKey(val first: Int, val last: Int, val left: Float, val right: Float, val height: Int, val vertical: Boolean)
    private var gradientKey: GradientKey? = null
    private var gradient: Shader? = null

    init {
        gravity = Gravity.TOP or Gravity.START
        includeFontPadding = false
    }

    fun bind(line: LyricLine, translationMode: String) {
        lyric = line
        val primary = if (translationMode == "translated" && line.translation.isNotEmpty()) line.translation else line.text
        primaryLength = primary.length
        wordTiming = translationMode != "translated" && line.words.isNotEmpty()
        sungCharacters = 0f
        text = SpannableStringBuilder(primary).apply {
            if (translationMode == "bilingual" && line.translation.isNotBlank()) {
                append('\n')
                val start = length
                append(line.translation)
                setSpan(RelativeSizeSpan(.62f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    fun setGradient(endColor: Int?, vertical: Boolean = false) {
        if (gradientEnd == endColor && gradientVertical == vertical) return
        gradientEnd = endColor
        gradientVertical = vertical
        gradientKey = null
        gradient = null
        invalidate()
    }

    private fun gradientFor(layout: Layout?): Shader? {
        val end = gradientEnd ?: return null
        if (layout == null || layout.lineCount == 0) return null
        val left = (0 until layout.lineCount).minOf { layout.getLineLeft(it) }
        val right = (0 until layout.lineCount).maxOf { layout.getLineRight(it) }.coerceAtLeast(left + 1f)
        val key = GradientKey(currentTextColor, end, left, right, layout.height, gradientVertical)
        if (key != gradientKey) {
            gradientKey = key
            gradient = LinearGradient(left, 0f, if (gradientVertical) left else right,
                if (gradientVertical) layout.height.coerceAtLeast(1).toFloat() else 0f,
                currentTextColor, end, Shader.TileMode.CLAMP)
        }
        return gradient
    }

    fun setPlaybackPosition(positionMs: Long) {
        if (!wordTiming) return
        val value = LyricTiming.sungCharacters(lyric, positionMs)
        if (abs(value - sungCharacters) < .001f) return
        sungCharacters = value
        invalidate()
    }

    /** A line that cedes focus rests fully sung, even when the next line starts before its words end. */
    fun markFullySung() {
        if (!wordTiming || sungCharacters >= primaryLength) return
        sungCharacters = primaryLength.toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout
        val oldShader = paint.shader
        paint.shader = gradientFor(textLayout)
        if (!wordTiming || textLayout == null || primaryLength == 0) {
            super.onDraw(canvas)
            paint.shader = oldShader
            return
        }
        highlight.reset()
        val complete = floor(sungCharacters.toDouble()).toInt().coerceIn(0, primaryLength)
        if (complete > 0) textLayout.getSelectionPath(0, complete, highlight)
        if (complete < primaryLength) {
            val fraction = sungCharacters - complete
            if (fraction > 0f) {
                val row = textLayout.getLineForOffset(complete)
                val startX = textLayout.getPrimaryHorizontal(complete)
                val endX = if (textLayout.getLineForOffset(complete + 1) != row) {
                    if (textLayout.isRtlCharAt(complete)) textLayout.getLineLeft(row) else textLayout.getLineRight(row)
                } else textLayout.getPrimaryHorizontal(complete + 1)
                val currentX = startX + (endX - startX) * fraction
                highlight.addRect(min(startX, currentX), textLayout.getLineTop(row).toFloat(),
                    max(startX, currentX), textLayout.getLineBottom(row).toFloat(), Path.Direction.CW)
            }
        }
        // Translation remains legible and does not inherit the original track's word timing.
        if (text.length > primaryLength) {
            selection.reset()
            textLayout.getSelectionPath(primaryLength, text.length, selection)
            highlight.addPath(selection)
        }
        val available = height - compoundPaddingTop - compoundPaddingBottom - textLayout.height
        val verticalOffset = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.CENTER_VERTICAL -> available.coerceAtLeast(0) / 2
            Gravity.BOTTOM -> available.coerceAtLeast(0)
            else -> 0
        }
        val foreground = currentTextColor
        val oldColor = paint.color
        canvas.save()
        canvas.translate((compoundPaddingLeft - scrollX).toFloat(),
            (compoundPaddingTop + verticalOffset - scrollY).toFloat())
        canvas.save()
        canvas.clipOutPath(highlight)
        paint.color = ColorUtils.setAlphaComponent(foreground, (android.graphics.Color.alpha(foreground) * .32f).toInt())
        textLayout.draw(canvas)
        canvas.restore()
        canvas.save()
        canvas.clipPath(highlight)
        paint.color = foreground
        textLayout.draw(canvas)
        canvas.restore()
        canvas.restore()
        paint.color = oldColor
        paint.shader = oldShader
    }
}
