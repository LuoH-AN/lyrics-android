package com.luoh.music.lrc.ui.home

import android.icu.text.BreakIterator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.lyrics.LyricLine
import com.luoh.music.lrc.lyrics.LyricTiming
import java.util.Locale

internal data class ScrollingLyricCue(val first: Int, val end: Int, val startMs: Long, val endMs: Long) {
    fun progress(positionMs: Long): Float =
        ((positionMs - startMs).toDouble() / (endMs - startMs)).coerceIn(0.0, 1.0).toFloat()
}

private const val MAX_CHARACTERS_PER_LINE = 256

/** Map provider words back to displayed graphemes even when their spacing differs. */
internal fun scrollingLyricCues(line: LyricLine, nextLineMs: Long?): List<ScrollingLyricCue> {
    if (line.words.isEmpty() || line.text.length > MAX_CHARACTERS_PER_LINE) return emptyList()
    val offsets = line.text.indices.filter { !line.text[it].isWhitespace() }
    val wordAt = IntArray(line.text.length)
    var offsetIndex = 0
    line.words.forEachIndexed { wordIndex, word ->
        word.text.forEach { character ->
            if (!character.isWhitespace()) {
                val target = offsets.getOrNull(offsetIndex++) ?: return emptyList()
                if (line.text[target] != character) return emptyList()
                wordAt[target] = wordIndex
            }
        }
    }
    if (offsetIndex != offsets.size) return emptyList()

    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(line.text) }
    val ranges = buildList {
        var first = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if (!line.text[first].isWhitespace()) add(first until end)
            first = end
            end = iterator.next()
        }
    }
    val counts = IntArray(line.words.size)
    val ordinals = IntArray(line.words.size)
    ranges.forEach { counts[wordAt[it.first]]++ }
    return ranges.mapNotNull { range ->
        val index = wordAt[range.first]
        val word = line.words[index]
        val duration = LyricTiming.wordDuration(line, index).coerceAtMost(Long.MAX_VALUE - word.startMs.coerceAtLeast(0))
        val ordinal = ordinals[index]++
        val start = word.startMs + (duration * (ordinal.toDouble() / counts[index])).toLong().coerceAtMost(duration)
        val end = word.startMs + (duration * ((ordinal + 1.0) / counts[index])).toLong().coerceAtMost(duration)
        if (start == Long.MAX_VALUE) return@mapNotNull null
        val cueEnd = minOf(maxOf(start + 1L, end), nextLineMs ?: Long.MAX_VALUE)
        if (start < cueEnd) ScrollingLyricCue(range.first, range.last + 1, start, cueEnd) else null
    }
}

/** Each character finishes its rise independently of how quickly it was sung. */
internal fun scrollingLyricLift(elapsedMs: Long, durationMs: Int): Float =
    FastOutSlowInEasing.transform((elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f))

internal fun scrollingLyricGlow(cue: ScrollingLyricCue, positionMs: Long): Float {
    val duration = cue.endMs - cue.startMs
    val attack = (duration * .3).coerceIn(40.0, 140.0)
    val release = (duration * .55).coerceIn(140.0, 520.0)
    val intensity = when {
        positionMs < cue.startMs -> .25 * (1 + (positionMs - cue.startMs) / attack).coerceAtLeast(0.0)
        positionMs < cue.endMs -> .25 + .75 * ((positionMs - cue.startMs) / attack).coerceAtMost(1.0)
        else -> (.25 + .75 * (duration / attack).coerceAtMost(1.0)) *
            (1 - (positionMs - cue.endMs) / release).coerceAtLeast(0.0)
    }
    return (intensity * (.24 + .76 * (duration / 900.0).coerceAtMost(1.0))).toFloat()
}

private data class FloatingGlyph(
    val cue: ScrollingLyricCue,
    val path: Path,
    val glowText: TextLayoutResult?,
    val glowOrigin: Offset,
)

/** Keep the original paragraph's wrapping, spacing and shaping while lifting individual glyphs. */
@Composable
internal fun KaraokeLyricText(
    text: String,
    line: LyricLine,
    position: State<Long>,
    fullySung: Boolean,
    active: Boolean,
    wordTiming: Boolean,
    color: Color,
    textAlign: TextAlign,
    appearance: HomeLyricAppearance,
    modifier: Modifier = Modifier,
    characterMotion: ScrollingCharacterMotion? = null,
    nextLineMs: Long? = null,
) {
    val float = active && wordTiming && text == line.text && characterMotion?.visible == true
    val cues = remember(line, nextLineMs, float) {
        if (float) scrollingLyricCues(line, nextLineMs) else emptyList()
    }
    val baseColor = if (wordTiming) color.copy(alpha = if (cues.isEmpty()) .32f else .43f) else color
    val glowMeasurer = if (cues.isNotEmpty() && (characterMotion?.glowPercent ?: 0) > 0) rememberTextMeasurer() else null
    var layout by remember(text, textAlign) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        color = baseColor,
        fontSize = appearance.fontSize.sp,
        lineHeight = (appearance.fontSize * 1.3f).sp,
        fontWeight = FontWeight(appearance.fontWeight),
        textAlign = textAlign,
        onTextLayout = { layout = it },
        modifier = modifier.fillMaxWidth().drawWithCache {
            val result = layout
            if (result == null || !wordTiming) return@drawWithCache onDrawWithContent { drawContent() }
            val glyphs = cues.mapNotNull { cue ->
                val path = result.getPathForRange(cue.first, cue.end)
                val bounds = path.getBounds()
                if (bounds.isEmpty) return@mapNotNull null
                // A separate glyph is only used for its shadow. Keep joined RTL shapes in the paragraph.
                val glowText = glowMeasurer?.takeIf { result.getBidiRunDirection(cue.first) == ResolvedTextDirection.Ltr }
                    ?.measure(text.substring(cue.first, cue.end), result.layoutInput.style.copy(textAlign = TextAlign.Start),
                        softWrap = false, maxLines = 1)
                val origin = if (glowText == null) Offset.Zero else Offset(
                    bounds.center.x - glowText.size.width / 2f,
                    result.getLineBaseline(result.getLineForOffset(cue.first)) - glowText.firstBaseline,
                )
                FloatingGlyph(cue, path, glowText, origin)
            }
            val highlight = Path()
            val changed = Path()
            val settled = Path()
            val rises = FloatArray(glyphs.size)
            val brightness = FloatArray(glyphs.size)
            val lift = (characterMotion?.liftDp ?: 0).dp.toPx()
            val glow = (characterMotion?.glowPercent ?: 0) / 100f
            val riseMs = characterMotion?.durationMs ?: 1
            val glowRadius = (appearance.fontSize.sp.toPx() * .23f).coerceAtLeast(1f)
            onDrawWithContent {
                val now = if (active) position.value else 0L
                if (glyphs.isEmpty()) {
                    drawContent()
                    val sung = when {
                        fullySung -> text.length.toFloat()
                        active -> LyricTiming.sungCharacters(line, now)
                        else -> 0f
                    }
                    if (sung > 0f) {
                        highlight.rewind()
                        highlight.addSungCharacters(result, text.length, sung)
                        clipPath(highlight) { drawText(result, color = color) }
                    }
                    return@onDrawWithContent
                }

                changed.rewind()
                settled.rewind()
                glyphs.forEachIndexed { index, glyph ->
                    val elapsed = now - glyph.cue.startMs
                    val rise = if (lift > 0f) scrollingLyricLift(elapsed, riseMs) else 0f
                    val progress = glyph.cue.progress(now)
                    rises[index] = rise
                    brightness[index] = progress
                    if (progress > 0f || rise > 0f) {
                        changed.addPath(glyph.path)
                        if (progress >= 1f && (rise >= 1f || lift == 0f)) settled.addPath(glyph.path)
                    }
                    val glowText = glyph.glowText
                    if (glowText != null) {
                        val strength = scrollingLyricGlow(glyph.cue, now) * glow
                        if (strength > .01f) drawText(glowText, color = Color.Transparent,
                            topLeft = glyph.glowOrigin - Offset(0f, rise * lift),
                            shadow = Shadow(color.copy(alpha = strength), Offset.Zero, glowRadius))
                    }
                }
                if (changed.isEmpty) drawContent()
                else clipPath(changed, clipOp = ClipOp.Difference) { this@onDrawWithContent.drawContent() }
                if (!settled.isEmpty) translate(top = -lift) {
                    clipPath(settled) { drawText(result, color = color) }
                }
                glyphs.forEachIndexed { index, glyph ->
                    val rise = rises[index]
                    val progress = brightness[index]
                    if ((progress > 0f || rise > 0f) && !(progress >= 1f && (rise >= 1f || lift == 0f))) {
                        translate(top = -lift * rise) {
                            clipPath(glyph.path) { drawText(result, color = lerp(baseColor, color, progress)) }
                        }
                    }
                }
            }
        },
    )
}

private fun Path.addSungCharacters(result: TextLayoutResult, length: Int, sung: Float) {
    val complete = sung.toInt().coerceIn(0, length)
    if (complete > 0) addPath(result.getPathForRange(0, complete))
    val fraction = sung - complete
    if (complete < length && fraction > 0f) {
        val bounds = result.getBoundingBox(complete)
        val rtl = result.getBidiRunDirection(complete) == ResolvedTextDirection.Rtl
        addRect(if (rtl) Rect(bounds.right - bounds.width * fraction, bounds.top, bounds.right, bounds.bottom)
            else Rect(bounds.left, bounds.top, bounds.left + bounds.width * fraction, bounds.bottom))
    }
}
