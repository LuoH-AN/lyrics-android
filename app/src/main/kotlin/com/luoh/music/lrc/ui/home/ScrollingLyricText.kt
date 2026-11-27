package com.luoh.music.lrc.ui.home

import android.icu.text.BreakIterator
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.lyrics.LyricLine
import com.luoh.music.lrc.lyrics.LyricTiming
import java.util.Locale

internal data class ScrollingLyricCue(val first: Int, val end: Int, val startMs: Long)

private const val MAX_CHARACTERS_PER_LINE = 256
private const val MAX_LIFTED_CHARACTERS = 12

internal fun scrollingLyricCues(line: LyricLine, endMs: Long): List<ScrollingLyricCue> {
    if (endMs <= line.startMs || line.text.length > MAX_CHARACTERS_PER_LINE) return emptyList()
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
    val sourceStarts = wordCharacterStarts(line)
    return ranges.mapIndexedNotNull { index, range ->
        val start = sourceStarts?.get(range.first)?.coerceAtLeast(line.startMs)
            ?: (line.startMs + ((endMs - line.startMs) * (index.toDouble() / ranges.size)).toLong())
        if (start >= endMs) null else ScrollingLyricCue(range.first, range.last + 1, start)
    }.sortedBy { it.startMs }
}

/** Providers may attach timing from the same lyric with different spacing. */
private fun wordCharacterStarts(line: LyricLine): LongArray? {
    if (line.words.isEmpty()) return null
    val offsets = line.text.indices.filter { !line.text[it].isWhitespace() }
    val starts = LongArray(line.text.length)
    var index = 0
    line.words.forEachIndexed { wordIndex, word ->
        val duration = LyricTiming.wordDuration(line, wordIndex)
        word.text.forEachIndexed { offset, character ->
            if (!character.isWhitespace()) {
                val target = offsets.getOrNull(index++) ?: return null
                if (line.text[target] != character) return null
                val delay = (duration * (offset.toDouble() / word.text.length)).toLong()
                starts[target] = word.startMs + delay.coerceAtMost(Long.MAX_VALUE - word.startMs.coerceAtLeast(0L))
            }
        }
    }
    return starts.takeIf { index == offsets.size }
}

internal fun scrollingLyricPulse(elapsedMs: Long, durationMs: Long): Float {
    if (elapsedMs <= 0L || elapsedMs >= durationMs) return 0f
    val progress = elapsedMs.toFloat() / durationMs
    val travel = if (progress < .35f) progress / .35f else (1f - progress) / .65f
    return travel * travel * (3f - 2f * travel)
}

private data class MovingLyricGlyph(
    val startMs: Long,
    val durationMs: Long,
    val path: Path,
    val bounds: Rect,
    val halo: Brush,
    val haloRadius: Float,
)

/** The full paragraph keeps its shaping and wrapping. Only the sounding glyphs leave their baseline. */
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
    characterMotion: ScrollingCharacterMotion = ScrollingCharacterMotion(),
    motionEndMs: Long = line.startMs,
) {
    val cues = remember(text, line, motionEndMs, active, characterMotion.visible) {
        if (active && characterMotion.visible && text == line.text) scrollingLyricCues(line, motionEndMs)
        else emptyList()
    }
    val baseColor = if (wordTiming) color.copy(alpha = .32f) else color
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
            if (result == null || (!wordTiming && cues.isEmpty())) {
                return@drawWithCache onDrawWithContent { drawContent() }
            }
            val glyphs = cues.mapNotNull { cue ->
                val path = result.getPathForRange(cue.first, cue.end)
                val bounds = path.getBounds()
                if (bounds.isEmpty) return@mapNotNull null
                val radius = maxOf(bounds.width * .8f, bounds.height * .65f)
                MovingLyricGlyph(
                    cue.startMs, minOf(characterMotion.durationMs.toLong(), motionEndMs - cue.startMs),
                    path, bounds, Brush.radialGradient(listOf(color, Color.Transparent), bounds.center, radius), radius,
                )
            }
            val highlight = Path()
            val liftedGlyphs = Path()
            val pulses = FloatArray(glyphs.size)
            val lift = characterMotion.liftDp.dp.toPx()
            val glow = characterMotion.glowPercent / 100f
            onDrawWithContent {
                val now = if (active) position.value else 0L
                val sung = when {
                    !wordTiming -> 0f
                    fullySung -> text.length.toFloat()
                    active -> LyricTiming.sungCharacters(line, now)
                    else -> 0f
                }
                highlight.rewind()
                if (sung > 0f) highlight.addSungCharacters(result, text.length, sung)

                liftedGlyphs.rewind()
                val last = glyphs.indexOfLast { it.startMs < now }
                // Bound how many copies of the paragraph can be drawn in a rapid passage.
                val recentGlyphs = (last - MAX_LIFTED_CHARACTERS + 1).coerceAtLeast(0)..last
                for (index in recentGlyphs) {
                    val glyph = glyphs[index]
                    val pulse = scrollingLyricPulse(now - glyph.startMs, glyph.durationMs)
                    pulses[index] = pulse
                    if (pulse <= 0f) continue
                    if (lift > 0f) liftedGlyphs.addPath(glyph.path)
                    if (glow > 0f) translate(top = -lift * pulse) {
                        drawCircle(glyph.halo, glyph.haloRadius, glyph.bounds.center, alpha = glow * pulse * .45f)
                    }
                }
                fun drawBaseline() {
                    drawContent()
                    if (!highlight.isEmpty) clipPath(highlight) { drawText(result, color = color) }
                }
                if (liftedGlyphs.isEmpty) drawBaseline()
                else clipPath(liftedGlyphs, clipOp = ClipOp.Difference) { drawBaseline() }

                if (lift > 0f) for (index in recentGlyphs) {
                    val pulse = pulses[index]
                    if (pulse <= 0f) continue
                    translate(top = -lift * pulse) {
                        clipPath(glyphs[index].path) {
                            drawText(result, color = lerp(baseColor, color, glow * pulse))
                            if (!highlight.isEmpty) clipPath(highlight) { drawText(result, color = color) }
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
