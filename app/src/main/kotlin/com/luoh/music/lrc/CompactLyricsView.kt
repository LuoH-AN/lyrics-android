package com.luoh.music.lrc

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.text.Layout
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Compact native lyrics. The hidden matching engine never participates in animation. */
class CompactLyricsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    var onContentSizeChanged: ((hasLyrics: Boolean, hasTranslation: Boolean, heightPx: Int) -> Unit)? = null

    private var document = LyricDocument(emptyList(), true)
    private var emptyMessage = "未在播放"
    private var trackKey = ""
    private val clock = LyricClock { SystemClock.elapsedRealtime() }
    private var resolvedDurationMs = 0L
    private val durationMs: Long get() = clock.durationMs
    private val playing: Boolean get() = clock.playing
    private var offsetMs = 0
    private var fontScale = 1f
    private var fontWeight = OverlayAppearance.DEFAULT_FONT_WEIGHT
    private var lyricColor = Color.WHITE
    private var translationMode = LyricsOverlayService.TRANSLATION_BILINGUAL
    private var before = 0
    private var after = 1
    private var activeIndex = -2
    private var paintedRows = emptyList<RowModel>()
    private val rowViews = mutableListOf<LyricRow>()
    private var lastFrameAt = 0L
    private var lastPositionMs = 0L
    private var scheduled = false
    private var lastContentSize = ""

    private data class RowModel(val line: LyricLine, val current: Boolean, val translated: Boolean = false)

    private val frame = object : Runnable {
        override fun run() {
            scheduled = false
            render(false)
            if (playing && isAttachedToWindow) scheduleFrame()
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = true
        clipToPadding = true
        setPadding(dp(8), dp(6), dp(8), dp(6))
        minimumHeight = dp(48)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setDocument(value: LyricDocument, message: String = "", fallbackDurationMs: Long = 0L) {
        document = value
        emptyMessage = message.ifBlank { "找不到歌词" }
        resolvedDurationMs = fallbackDurationMs
        if (durationMs <= 0L && fallbackDurationMs > 0L) clock.durationMs = fallbackDurationMs
        render(true)
        scheduleFrame()
    }

    fun setPlayback(key: String, positionMs: Long, duration: Long, isPlaying: Boolean, playbackSpeed: Float) {
        clock.durationMs = if (duration > 0L) duration else resolvedDurationMs
        clock.sync(positionMs, isPlaying, playbackSpeed, force = key != trackKey)
        trackKey = key
        render(false)
        scheduleFrame()
    }

    fun setOffset(value: Int) {
        offsetMs = value.coerceIn(-5000, 5000)
        render(true)
    }

    fun setAppearance(percent: Int, color: Int, mode: String, contextBefore: Int, contextAfter: Int,
                      weight: Int = OverlayAppearance.DEFAULT_FONT_WEIGHT) {
        val scale = percent.coerceIn(35, 150) / 100f
        val normalizedWeight = OverlayAppearance.normalizeWeight(weight)
        val normalizedBefore = contextBefore.coerceIn(0, 2)
        val normalizedAfter = contextAfter.coerceIn(0, 2)
        if (fontScale == scale && fontWeight == normalizedWeight && lyricColor == color &&
            translationMode == mode && before == normalizedBefore && after == normalizedAfter) return
        fontScale = scale
        fontWeight = normalizedWeight
        lyricColor = color
        translationMode = mode
        before = normalizedBefore
        after = normalizedAfter
        // Recreate rows for metrics/color changes, even if their text is unchanged.
        paintedRows = emptyList()
        render(true)
    }

    fun clear(message: String) {
        clock.reset()
        trackKey = ""
        resolvedDurationMs = 0L
        offsetMs = 0
        document = LyricDocument(emptyList(), true)
        emptyMessage = message
        removeCallbacks(frame)
        scheduled = false
        render(true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        render(true)
        scheduleFrame()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        scheduled = false
        super.onDetachedFromWindow()
    }

    private fun scheduleFrame() {
        if (!scheduled && isAttachedToWindow) {
            scheduled = true
            postOnAnimation(frame)
        }
    }

    private fun currentPosition(): Long = clock.positionMs()

    private fun displayLine(line: LyricLine, current: Boolean): LyricLine {
        val translated = translationMode == LyricsOverlayService.TRANSLATION_TRANSLATED && line.translation.isNotBlank()
        return line.copy(
            text = if (translated) line.translation else line.text,
            translation = "",
            words = if (current && !translated) line.words else emptyList()
        )
    }

    private fun render(force: Boolean) {
        val position = (currentPosition() + if (document.timed) offsetMs else 0).coerceAtLeast(0L)
        val nextIndex = CompactLyricSelection.activeIndex(document, position, durationMs)
        if (force || nextIndex != activeIndex) {
            activeIndex = nextIndex
            val rows = if (nextIndex < 0) {
                listOf(RowModel(LyricLine(0L, emptyMessage), true))
            } else buildList {
                for (index in CompactLyricSelection.contextIndices(document.lines.size, nextIndex, before, after)) {
                    val line = document.lines[index]
                    add(RowModel(displayLine(line, index == nextIndex), index == nextIndex))
                    if (index == nextIndex && translationMode == LyricsOverlayService.TRANSLATION_BILINGUAL && line.translation.isNotBlank()) {
                        add(RowModel(LyricLine(line.startMs, line.translation, endMs = line.endMs), false, true))
                    }
                }
            }
            if (rows != paintedRows) rebuildRows(rows)
        }
        val line = document.lines.getOrNull(activeIndex)
        val lineEnd = if (line == null) 0L else if (document.timed) {
            document.lines.getOrNull(activeIndex + 1)?.startMs ?: line.endMs ?: durationMs
        } else if (durationMs > 0L) durationMs * (activeIndex + 1) / document.lines.size else 0L
        val lineStart = if (line == null || document.timed) line?.startMs ?: 0L
            else durationMs * activeIndex / document.lines.size
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - lastFrameAt).coerceIn(1L, 40L)
        val rewound = position + 800L < lastPositionMs
        rowViews.forEach { row -> row.paintPosition(position, lineStart, lineEnd, elapsed, rewound) }
        lastPositionMs = position
        lastFrameAt = now
    }

    private fun rebuildRows(rows: List<RowModel>) {
        removeAllViews()
        rowViews.clear()
        paintedRows = rows
        rows.forEach { model ->
            val row = LyricRow(model)
            rowViews.add(row)
            addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, row.rowHeight))
        }
        val hasLyrics = document.lines.isNotEmpty()
        val hasTranslation = rows.any { it.translated }
        val height = max(dp(48), paddingTop + paddingBottom + rowViews.sumOf { it.rowHeight })
        val state = "$hasLyrics:$hasTranslation:$height"
        if (state != lastContentSize) {
            lastContentSize = state
            onContentSizeChanged?.invoke(hasLyrics, hasTranslation, height)
        }
        contentDescription = rows.joinToString("\n") { it.line.text }
    }

    private inner class LyricRow(private val model: RowModel) : FrameLayout(context) {
        private val lyric = LyricLineView(context)
        private var scrollShift = 0f
        private var scrollTarget = 0f
        private var contentWidth = 0
        private var followPositionMs = 0L
        private val rtl = !java.text.Bidi(model.line.text, java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()
        val rowHeight: Int

        init {
            clipChildren = true
            clipToPadding = true
            val textSp = when {
                document.lines.isEmpty() -> 18f
                model.current -> 30f * fontScale
                else -> 23f * fontScale
            }
            lyric.apply {
                textSize = textSp
                typeface = OverlayAppearance.typeface(fontWeight)
                setTextColor(lyricColor)
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                setSingleLine(true)
                gravity = Gravity.CENTER_VERTICAL
                alpha = if (model.current) 1f else if (model.translated) .8f else .55f
                bind(model.line, LyricsOverlayService.TRANSLATION_ORIGINAL)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            rowHeight = ceil(lyric.paint.fontSpacing).toInt() + dp(2)
            addView(lyric, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight))
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val viewportWidth = MeasureSpec.getSize(widthMeasureSpec)
            val canScroll = model.current || model.translated
            val measuredContent = if (canScroll) max(viewportWidth,
                ceil(Layout.getDesiredWidth(model.line.text, lyric.paint)).toInt() + dp(2)) else viewportWidth
            if (contentWidth != measuredContent) {
                contentWidth = measuredContent
                scrollShift = if (rtl) -(contentWidth - viewportWidth).toFloat() else 0f
                scrollTarget = scrollShift
            }
            // Size the text before measuring children; changing LayoutParams during layout can
            // leave a long line constrained to the viewport and prevent horizontal following.
            (lyric.layoutParams as FrameLayout.LayoutParams).apply {
                width = contentWidth
                height = rowHeight
                gravity = Gravity.TOP or Gravity.LEFT
            }
            lyric.ellipsize = if (canScroll) null else TextUtils.TruncateAt.END
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        fun paintPosition(position: Long, lineStart: Long, lineEnd: Long, elapsed: Long, rewound: Boolean) {
            if (model.current) lyric.setPlaybackPosition(position)
            val overflow = contentWidth - width
            if (width <= 0 || overflow <= dp(4) || (!model.current && !model.translated)) {
                lyric.translationX = 0f
                return
            }
            val words = model.line.words
            if (model.current && words.isNotEmpty()) {
                if (rewound) {
                    scrollTarget = if (rtl) -overflow.toFloat() else 0f
                    scrollShift = scrollTarget
                    followPositionMs = position
                } else followPositionMs = max(followPositionMs, position)
                val wordIndex = words.indexOfLast { it.startMs <= followPositionMs }.coerceAtLeast(0)
                val word = words[wordIndex]
                val prefix = words.take(wordIndex).sumOf { it.text.length }
                val progress = ((followPositionMs - word.startMs).toFloat() / word.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                val layout = lyric.layout
                val startX = layout?.getPrimaryHorizontal(prefix.coerceAtMost(model.line.text.length))
                    ?: lyric.paint.measureText(model.line.text.take(prefix))
                val endX = layout?.getPrimaryHorizontal((prefix + word.text.length).coerceAtMost(model.line.text.length))
                    ?: (startX + lyric.paint.measureText(word.text))
                val point = startX + (endX - startX) * progress
                val desired = (width * (if (rtl) .62f else .38f) - point).coerceIn(-overflow.toFloat(), 0f)
                scrollTarget = if (rtl) max(scrollTarget, desired) else min(scrollTarget, desired)
                scrollShift += (scrollTarget - scrollShift) * (1f - exp(-elapsed / 95f))
            } else {
                val budget = (lineEnd - lineStart).coerceAtLeast(1L).toFloat()
                val delay = min(750f, budget * .18f)
                val natural = (1450f + overflow / resources.displayMetrics.density / 90f * 1000f).coerceIn(1800f, 5200f)
                val available = (budget - delay - min(180f, budget * .06f)).coerceAtLeast(80f)
                val progress = ((position - lineStart - delay) / min(natural, available)).coerceIn(0f, 1f)
                scrollShift = -overflow * (if (rtl) 1f - progress else progress)
            }
            lyric.translationX = scrollShift
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}
