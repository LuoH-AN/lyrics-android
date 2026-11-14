package com.luoh.music.lrc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.text.style.TextAlign

internal data class PopPlaybackFrame(val position: State<Long>, val index: Int, val line: LyricLine?,
    val primary: String, val timeline: PopLyricTimeline?, val interlude: LyricInterlude?,
    val cameraPath: MutableState<PopCameraPath?>)

@Composable
internal fun rememberPopPlayback(state: HomePresentationState, style: PopLyricsStyle): PopPlaybackFrame {
    val document = state.document
    val position = remember(state) { mutableLongStateOf(state.clock.positionMs() + state.displayOffsetMs) }
    var index by remember(document) { mutableIntStateOf(LyricTiming.activeIndex(document.lines, state.clock.positionMs() + state.displayOffsetMs)) }
    var interlude by remember(document) { mutableStateOf(LyricInterlude.at(document, position.longValue, state.clock.durationMs)) }
    val line = document.lines.getOrNull(index)
    val primary = line?.let {
        if (state.translationDisplayMode == LyricsOverlayService.TRANSLATION_TRANSLATED && it.translation.isNotBlank()) it.translation else it.text
    }.orEmpty()
    val timeline = remember(line, primary, style.reveal) { line?.let { PopLyricTimeline.prepare(it, primary, style.reveal) } }
    val cameraPath = remember(line, primary, style) { mutableStateOf<PopCameraPath?>(null) }
    val policy = LocalAgoraMotionPolicy.current
    val allowMotion = policy.allowSpatialTransitions
    val nextLineMs = document.lines.getOrNull(index + 1)?.startMs
    val gapStartMs = line?.let {
        val end = LyricTiming.lineEnd(it)
        end.takeIf { (nextLineMs ?: state.clock.durationMs) - end > 4500L }
    }

    LaunchedEffect(document, timeline, state.presentationActive, state.playing, state.playbackRevision,
        state.displayOffsetMs, allowMotion, style, cameraPath) {
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
            val nextBoundary = gapStartMs?.takeIf { it > now } ?: nextLineMs
            val wordDelay = timeline?.nextChangeDelayMs(now, nextBoundary, style) ?: 1000L
            val cameraDelay = PopStageMotion.nextChangeDelayMs(now, cameraPath.value, style)
            val cameraMoving = cameraDelay == 0L
            // A static camera sleeps between word events; breathing shares one capped clock.
            val waitMs = when {
                interlude != null -> 0L
                !allowMotion || timeline == null -> ((nextBoundary ?: state.clock.durationMs) - now).coerceIn(1L, 1000L)
                (style.continuousCameraMotion || cameraMoving) && interlude == null -> 0L
                else -> minOf(wordDelay, cameraDelay)
            }
            if (waitMs > 0L) delay((waitMs / state.clock.speed).toLong().coerceIn(1L, 1000L))
            withFrameNanos { frame ->
                if (frame - lastFrame >= if (wordDelay == 0L || cameraMoving || interlude != null)
                    PopLyricMotion.FRAME_INTERVAL_NS else style.frameIntervalNs) {
                    lastFrame = frame
                    update()
                }
            }
        }
    }
    return PopPlaybackFrame(position, index, line, primary, timeline, interlude, cameraPath)
}

/** Clock reads only invalidate native drawing and the shared camera's graphics layers. */
@Composable
internal fun PopLyricsStage(state: HomePresentationState, modifier: Modifier, albumBackground: Boolean,
    appearance: HomeLyricAppearance, style: PopLyricsStyle, frame: PopPlaybackFrame) {
    val position = frame.position
    val line = frame.line
    val interlude = frame.interlude
    val allowMotion = LocalAgoraMotionPolicy.current.allowSpatialTransitions
    val foreground = if (albumBackground) Color.White else MaterialTheme.colorScheme.onSurface
    val activeColor = if (albumBackground) Color.White else MaterialTheme.colorScheme.primary
    Column(modifier.testTag("home_pop_stage").padding(horizontal = 24.dp, vertical = 12.dp)) {
        AnimatedContent(targetState = frame, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentKey = { it.line?.startMs to it.primary }, label = "popSentenceTransition",
            transitionSpec = {
                val duration = if (allowMotion) 320 else 0
                val enter = when (style.transition) {
                    "zoom" -> fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = .72f)
                    "slide" -> fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { it / 3 }
                    else -> fadeIn(tween(duration))
                }
                val exit = when (style.transition) {
                    "zoom" -> fadeOut(tween(duration)) + scaleOut(tween(duration), targetScale = 1.18f)
                    "slide" -> fadeOut(tween(duration)) + slideOutHorizontally(tween(duration)) { -it / 3 }
                    else -> fadeOut(tween(duration))
                }
                enter togetherWith exit
            }) { sentence ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val gap = sentence.interlude
                val currentLine = sentence.line
                if (gap != null || currentLine == null) {
                    Column(Modifier.fillMaxWidth(.65f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(if (sentence.index < 0) "前奏" else "间奏", color = foreground.copy(alpha = .7f))
                        if (gap != null) LinearProgressIndicator(progress = { gap.progress(position.value) },
                            modifier = Modifier.fillMaxWidth().testTag("home_pop_interlude"), color = activeColor)
                    }
                } else if (sentence.timeline != null) {
                    PopLyricCanvas(sentence.timeline, position, appearance, style, allowMotion, foreground, activeColor,
                        cameraPath = if (sentence.timeline === frame.timeline) frame.cameraPath else null,
                        modifier = Modifier.fillMaxSize().semantics { text = AnnotatedString(sentence.primary) }
                            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                                onClick = { state.seek(currentLine.startMs - state.displayOffsetMs) },
                                onLongClickLabel = "摘句与分享", onLongClick = { state.beginSharing(sentence.index) }))
                }
            }
        }
        if (line != null && interlude == null && state.translationDisplayMode == LyricsOverlayService.TRANSLATION_BILINGUAL && line.translation.isNotBlank()) {
            Text(line.translation, color = foreground.copy(alpha = .75f), fontSize = appearance.translationSize.sp,
                maxLines = 3, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp))
        }

    }
}

private data class PopGlyph(val layout: TextLayoutResult, val slot: PopLyricSlot, val angle: Float, val entranceOffset: Offset)
private data class PopScene(val glyphs: List<PopGlyph>, val scale: Float, val cameraPath: PopCameraPath)

@Composable
private fun PopLyricCanvas(timeline: PopLyricTimeline, position: State<Long>, appearance: HomeLyricAppearance,
    style: PopLyricsStyle, allowMotion: Boolean, foreground: Color, activeColor: Color,
    cameraPath: MutableState<PopCameraPath?>?, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Shaping width is stable while player controls resize the stage; only packing repeats.
    val shapingWidth = with(density) {
        (minOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(160) * .85f).dp.roundToPx()
    }.coerceAtLeast(1)
    val textStyle = MaterialTheme.typography.headlineLarge.copy(
        fontSize = (appearance.fontSize + 8).coerceIn(24, 64).sp,
        lineHeight = ((appearance.fontSize + 8).coerceIn(24, 64) * 1.3f).sp,
        fontWeight = FontWeight(appearance.fontWeight), color = foreground,
    )
    val layouts = remember(timeline, textStyle, density, shapingWidth) {
        timeline.tokens.map { token ->
            measurer.measure(AnnotatedString(popLyricPreview(token.text)), style = textStyle,
                constraints = Constraints(maxWidth = shapingWidth), maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
    }
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val gap = with(density) { (appearance.lineSpacing / 3f + 4f).dp.toPx() }
        val scene = remember(timeline, layouts, width, height, gap, density, style) {
            val packing = packPopLyrics(FloatArray(layouts.size) { layouts[it].size.width.toFloat() },
                FloatArray(layouts.size) { layouts[it].size.height.toFloat() },
                width * .92f, height * .9f, gap, style.layout, style.maxWordScale, style.rotationDegrees)
            val travelX = with(density) { (72.dp * (style.travelPercent / 100f)).toPx() }
            val travelY = with(density) { (48.dp * (style.travelPercent / 100f)).toPx() }
            val glyphs = layouts.mapIndexed { index, layout ->
                val packed = packing.slots.getOrElse(index) { PopLyricSlot(0f, 0f) }
                val slot = PopLyricSlot(packed.x + width * .04f, packed.y + height * .05f)
                val seed = Math.floorMod(timeline.tokens[index].text.hashCode().toLong() + index * 17L, 997L).toFloat()
                val halfWidth = (layout.size.width * style.maxWordScale + layout.size.height * .52f) * packing.scale / 2f
                val halfHeight = (layout.size.height * style.maxWordScale + layout.size.width * .52f) * packing.scale / 2f
                // Cache a seeded entrance, kept inside the viewport even in small landscape windows.
                val dx = (if (style.entrance == "scatter") sin(seed) * travelX else 0f).coerceIn(
                    (halfWidth - slot.x).coerceAtMost(0f), (width - halfWidth - slot.x).coerceAtLeast(0f))
                val dy = (when (style.entrance) { "scatter" -> cos(seed) * travelY; "rise" -> travelY; else -> 0f }).coerceIn(
                    (halfHeight - slot.y).coerceAtMost(0f), (height - halfHeight - slot.y).coerceAtLeast(0f))
                PopGlyph(layout, slot, if (timeline.wordTimed) ((seed % 7f) - 3f) * style.rotationDegrees / 20f else 0f, Offset(dx, dy))
            }
            PopScene(glyphs, packing.scale, PopStageMotion.path(timeline, glyphs.map {
                PopCameraPoint(it.slot.x / width.coerceAtLeast(1f) - .5f, it.slot.y / height.coerceAtLeast(1f) - .5f)
            }, style))
        }
        SideEffect { if (cameraPath != null) cameraPath.value = scene.cameraPath }
        Canvas(Modifier.fillMaxSize().testTag("home_pop_canvas")) {
            val now = position.value
            for (index in scene.glyphs.indices) {
                val token = timeline.tokens[index]
                val animated = timeline.wordTimed && allowMotion
                val elapsed = now - (token.startMs - style.lookaheadMs + token.visualDelayMs)
                if (animated && elapsed < 0L) continue
                val entered = if (animated) PopLyricMotion.enter(elapsed, style.entranceMs.toLong()) else 1f
                val passed = if (animated) PopLyricMotion.pass(now - token.endMs) else 0f
                val alpha = if (animated) (elapsed / 100f).coerceIn(0f, 1f) * (1f - passed * .18f) else 1f
                val scale = if (animated) (.5f + entered * (style.activeScalePercent / 100f - .5f) - passed * (style.activeScalePercent / 100f - 1f))
                    .coerceIn(.5f, style.maxWordScale) else 1f
                val glyph = scene.glyphs[index]
                val origin = Offset(glyph.slot.x, glyph.slot.y)
                withTransform({
                    val remaining = if (animated) 1f - entered else 0f
                    translate(origin.x + glyph.entranceOffset.x * remaining, origin.y + glyph.entranceOffset.y * remaining)
                    scale(scene.scale * scale, scene.scale * scale, pivot = Offset.Zero)
                    if (animated) rotate(glyph.angle + style.rotationDegrees * (1f - entered), pivot = Offset.Zero)
                    translate(-glyph.layout.size.width / 2f, -glyph.layout.size.height / 2f)
                }) {
                    drawText(glyph.layout, color = if (animated && now < token.endMs) activeColor else foreground, alpha = alpha)
                }
            }
        }
    }
}
