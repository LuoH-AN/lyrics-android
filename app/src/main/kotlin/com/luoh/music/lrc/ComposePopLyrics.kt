package com.luoh.music.lrc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import kotlinx.coroutines.delay
import kotlin.math.min

/** A sentence-sized native stage: layout on sentence changes; clock reads only invalidate drawing. */
@Composable
internal fun PopLyricsStage(state: HomePresentationState, modifier: Modifier, albumBackground: Boolean,
    appearance: HomeLyricAppearance, onBrowse: () -> Unit) {
    val document = state.document
    val position = remember(state) { mutableLongStateOf(state.clock.positionMs() + state.displayOffsetMs) }
    var index by remember(document) { mutableIntStateOf(LyricTiming.activeIndex(document.lines, state.clock.positionMs() + state.displayOffsetMs)) }
    var interlude by remember(document) { mutableStateOf(LyricInterlude.at(document, position.longValue, state.clock.durationMs)) }
    val line = document.lines.getOrNull(index)
    val primary = line?.let {
        if (state.translationDisplayMode == LyricsOverlayService.TRANSLATION_TRANSLATED && it.translation.isNotBlank()) it.translation else it.text
    }.orEmpty()
    val timeline = remember(line, primary) { line?.let { PopLyricTimeline.prepare(it, primary) } }
    val policy = LocalAgoraMotionPolicy.current
    val allowMotion = policy.allowSpatialTransitions
    val foreground = if (albumBackground) Color.White else MaterialTheme.colorScheme.onSurface
    val activeColor = if (albumBackground) Color.White else MaterialTheme.colorScheme.primary
    val nextLineMs = document.lines.getOrNull(index + 1)?.startMs

    LaunchedEffect(document, timeline, state.presentationActive, state.playing, state.playbackRevision,
        state.displayOffsetMs, allowMotion) {
        fun update() {
            val now = state.clock.positionMs() + state.displayOffsetMs
            position.longValue = now
            index = LyricTiming.activeIndex(document.lines, now)
            interlude = LyricInterlude.at(document, now, state.clock.durationMs)
        }
        update()
        var lastFrame = 0L
        while (state.presentationActive && state.playing) {
            val now = position.longValue
            if (state.clock.durationMs > 0L && state.clock.positionMs() >= state.clock.durationMs) break
            // Stable words need no frames until a word boundary. Gaps keep a determinate progress bar.
            val waitMs = when {
                interlude != null -> 0L
                !allowMotion || timeline == null -> ((nextLineMs ?: state.clock.durationMs) - now).coerceIn(1L, 1000L)
                else -> timeline.nextChangeDelayMs(now, nextLineMs)
            }
            if (waitMs > 0L) delay((waitMs / state.clock.speed).toLong().coerceIn(1L, 1000L))
            withFrameNanos { frame ->
                if (frame - lastFrame >= PopLyricMotion.FRAME_INTERVAL_NS) {
                    lastFrame = frame
                    update()
                }
            }
        }
    }
    Column(modifier.testTag("home_pop_stage").padding(horizontal = 24.dp, vertical = 12.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val gap = interlude
            if (gap != null || line == null) {
                Column(Modifier.fillMaxWidth(.65f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (index < 0) "前奏" else "间奏", color = foreground.copy(alpha = .7f))
                    if (gap != null) LinearProgressIndicator(progress = { gap.progress(position.longValue) },
                        modifier = Modifier.fillMaxWidth().testTag("home_pop_interlude"), color = activeColor)
                }
            } else if (timeline != null) {
                PopLyricCanvas(timeline, position, appearance, allowMotion, foreground, activeColor,
                    Modifier.fillMaxSize().semantics { text = AnnotatedString(primary) }
                        .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { state.seek(line.startMs - state.displayOffsetMs) },
                            onLongClickLabel = "摘句与分享", onLongClick = { state.beginSharing(index) }))
            }
        }
        if (line != null && interlude == null && state.translationDisplayMode == LyricsOverlayService.TRANSLATION_BILINGUAL && line.translation.isNotBlank()) {
            Text(line.translation, color = foreground.copy(alpha = .75f), fontSize = appearance.translationSize.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (index >= 0) "${index + 1} / ${document.lines.size}" else "逐词跃动",
                style = MaterialTheme.typography.labelMedium, color = foreground.copy(alpha = .6f))
            TextButton(onClick = onBrowse) { Text("浏览歌词", color = activeColor) }
        }
    }
}

private data class PopGlyph(val layout: TextLayoutResult, val slot: PopLyricSlot, val angle: Float)
private data class PopScene(val glyphs: List<PopGlyph>, val scale: Float)

@Composable
private fun PopLyricCanvas(timeline: PopLyricTimeline, position: State<Long>, appearance: HomeLyricAppearance,
    allowMotion: Boolean, foreground: Color, activeColor: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val textStyle = MaterialTheme.typography.headlineLarge.copy(
        fontSize = (appearance.fontSize + 6).coerceAtMost(48).sp,
        fontWeight = FontWeight(appearance.fontWeight), color = foreground,
    )
    BoxWithConstraints(modifier.clipToBounds()) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val gap = with(density) { (appearance.lineSpacing / 2f + 6f).dp.toPx() }
        val scene = remember(timeline, width, height, gap, textStyle, density) {
            val layouts = timeline.tokens.map { token ->
                measurer.measure(AnnotatedString(token.text), style = textStyle,
                    constraints = Constraints(maxWidth = (width * .85f).toInt().coerceAtLeast(1)),
                    maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            val packing = packPopLyrics(FloatArray(layouts.size) { layouts[it].size.width.toFloat() },
                FloatArray(layouts.size) { layouts[it].size.height.toFloat() }, width, height, gap)
            PopScene(layouts.mapIndexed { index, layout ->
                PopGlyph(layout, packing.slots.getOrElse(index) { PopLyricSlot(0f, 0f) },
                    if (timeline.wordTimed) (Math.floorMod(timeline.tokens[index].text.hashCode().toLong() + index * 17L, 7L) - 3L).toFloat() else 0f)
            }, packing.scale)
        }
        Canvas(Modifier.fillMaxSize().testTag("home_pop_canvas")) {
            val now = position.value
            for (index in scene.glyphs.indices) {
                val token = timeline.tokens[index]
                val animated = timeline.wordTimed && allowMotion
                if (animated && now < token.startMs) continue
                val entered = if (animated) PopLyricMotion.enter(now - token.startMs) else 1f
                val passed = if (animated) PopLyricMotion.pass(now - token.endMs) else 0f
                val alpha = if (animated) (.2f + .8f * min(entered, 1f)) * (1f - passed * .2f) else 1f
                val scale = if (animated) (.65f + entered * .41f - passed * .06f).coerceAtMost(PopLyricMotion.MAX_SCALE) else 1f
                val glyph = scene.glyphs[index]
                val origin = Offset(glyph.slot.x, glyph.slot.y)
                withTransform({
                    translate(origin.x, origin.y)
                    scale(scene.scale * scale, scene.scale * scale, pivot = Offset.Zero)
                    if (animated) rotate(glyph.angle * min(entered, 1f), pivot = Offset.Zero)
                    translate(-glyph.layout.size.width / 2f, -glyph.layout.size.height / 2f)
                }) {
                    drawText(glyph.layout, color = if (animated && now < token.endMs) activeColor else foreground, alpha = alpha)
                }
            }
        }
    }
}
