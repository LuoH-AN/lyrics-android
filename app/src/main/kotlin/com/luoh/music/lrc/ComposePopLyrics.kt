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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign

internal data class PopPlaybackFrame(val position: State<Long>, val index: Int, val line: LyricLine?,
    val primary: String, val timeline: PopLyricTimeline?, val interlude: LyricInterlude?,
    val cameraPath: MutableState<PopCameraPath?>, val cameraPosition: State<Long>,
    val timing: PopLyricRenderTiming?, val upcoming: PopUpcomingSentence?)

internal data class PopUpcomingSentence(val timeline: PopLyricTimeline, val timing: PopLyricRenderTiming)
private data class PopTimelineKey(val line: LyricLine, val primary: String, val reveal: String)
private data class PopTimingKey(val timeline: PopLyricTimeline, val style: PopLyricsStyle, val nextLineMs: Long?)

@Composable
internal fun rememberPopPlayback(state: HomePresentationState, style: PopLyricsStyle): PopPlaybackFrame {
    val document = state.document
    val position = remember(state) { mutableLongStateOf(state.clock.positionMs() + state.displayOffsetMs) }
    val cameraPosition = remember(state) { mutableLongStateOf(position.longValue) }
    var index by remember(document) { mutableIntStateOf(LyricTiming.activeIndex(document.lines, state.clock.positionMs() + state.displayOffsetMs)) }
    var interlude by remember(document) { mutableStateOf(LyricInterlude.at(document, position.longValue, state.clock.durationMs)) }
    var preheat by remember(document) { mutableStateOf(false) }
    val timelineCache = remember(document, state.translationDisplayMode, style.reveal) {
        PopLyricCache<PopTimelineKey, PopLyricTimeline>()
    }
    val timingCache = remember(timelineCache) { PopLyricCache<PopTimingKey, PopLyricRenderTiming>() }
    fun profile(candidate: PopLyricTimeline, next: Long?) = timingCache.getOrPut(PopTimingKey(candidate, style, next)) {
        popLyricRenderTiming(candidate, style, next)
    }
    val line = document.lines.getOrNull(index)
    val primary = line?.let {
        if (state.translationDisplayMode == LyricsOverlayService.TRANSLATION_TRANSLATED && it.translation.isNotBlank()) it.translation else it.text
    }.orEmpty()
    fun prepare(candidate: LyricLine, text: String) = timelineCache.getOrPut(PopTimelineKey(candidate, text, style.reveal)) {
        PopLyricTimeline.prepare(candidate, text, style.reveal)
    }
    val timeline = line?.let { prepare(it, primary) }
    val cameraPath = remember(line, primary, style) { mutableStateOf<PopCameraPath?>(null) }
    val policy = LocalAgoraMotionPolicy.current
    val allowMotion = policy.allowSpatialTransitions
    val nextLine = document.lines.getOrNull(index + 1)
    val nextLineMs = nextLine?.startMs
    val timing = timeline?.let { profile(it, nextLineMs) }
    val upcoming = remember(preheat, nextLine, style, state.translationDisplayMode, timelineCache) {
        if (!preheat || nextLine == null) null else {
            val text = if (state.translationDisplayMode == LyricsOverlayService.TRANSLATION_TRANSLATED &&
                nextLine.translation.isNotBlank()) nextLine.translation else nextLine.text
            val prepared = prepare(nextLine, text)
            PopUpcomingSentence(prepared, profile(prepared, document.lines.getOrNull(index + 2)?.startMs))
        }
    }
    val preheatAtMs = nextLineMs?.minus(1200L)
    val gapStartMs = line?.let {
        val end = LyricTiming.lineEnd(it)
        end.takeIf { (nextLineMs ?: state.clock.durationMs) - end > 4500L }
    }

    LaunchedEffect(document, timeline, state.presentationActive, state.playing, state.playbackRevision,
        state.displayOffsetMs, allowMotion, style, cameraPath, timing) {
        fun update(draw: Boolean = true) {
            val now = state.clock.positionMs() + state.displayOffsetMs
            cameraPosition.longValue = now
            preheat = state.presentationActive && state.playing && nextLineMs != null && nextLineMs - now in 180L..1200L
            if (draw) {
                position.longValue = now
                index = LyricTiming.activeIndex(document.lines, now)
                interlude = LyricInterlude.at(document, now, state.clock.durationMs)
            }
        }
        update()
        var lastFrame = 0L
        var lastDraw = 0L
        while (state.presentationActive && state.playing) {
            val now = cameraPosition.longValue
            if (state.clock.durationMs > 0L && state.clock.positionMs() >= state.clock.durationMs) break
            val lineBoundary = gapStartMs?.takeIf { it > now } ?: nextLineMs
            val preheatBoundary = preheatAtMs?.takeIf { it > now }
            val nextBoundary = if (lineBoundary != null && preheatBoundary != null) minOf(lineBoundary, preheatBoundary)
                else lineBoundary ?: preheatBoundary
            val wordDelay = if (timeline?.wordTimed == true && timing != null)
                timing.nextChangeDelayMs(timeline.tokens, now, nextBoundary)
                else timeline?.nextChangeDelayMs(now, nextBoundary, style) ?: 1000L
            val cameraDelay = PopStageMotion.nextChangeDelayMs(now, cameraPath.value, style)
            val cameraMoving = allowMotion && interlude == null && cameraDelay == 0L
            // Only the inexpensive camera layer runs at display cadence; glyph drawing stays capped.
            val waitMs = when {
                interlude != null -> 0L
                !allowMotion || timeline == null -> ((nextBoundary ?: state.clock.durationMs) - now).coerceIn(1L, 1000L)
                (style.continuousCameraMotion || cameraMoving) && interlude == null -> 0L
                else -> minOf(wordDelay, cameraDelay)
            }
            if (waitMs > 0L) delay((waitMs / state.clock.speed).toLong().coerceIn(1L, 1000L))
            withFrameNanos { frame ->
                val interval = when {
                    cameraMoving -> PopStageMotion.FRAME_INTERVAL_NS
                    wordDelay == 0L || interlude != null -> PopLyricMotion.FRAME_INTERVAL_NS
                    else -> style.frameIntervalNs
                }
                if (waitMs > 0L || frame - lastFrame >= interval) {
                    lastFrame = frame
                    val draw = waitMs > 0L || frame - lastDraw >= PopLyricMotion.FRAME_INTERVAL_NS
                    update(draw)
                    if (draw) lastDraw = frame
                }
            }
        }
    }
    return PopPlaybackFrame(position, index, line, primary, timeline, interlude, cameraPath, cameraPosition, timing, upcoming)
}

/** Clock reads only invalidate native drawing and the shared camera's graphics layers. */
@Composable
internal fun PopLyricsStage(state: HomePresentationState, modifier: Modifier, albumBackground: Boolean,
    appearance: HomeLyricAppearance, style: PopLyricsStyle, frame: PopPlaybackFrame) {
    val position = frame.position
    val renderCache = remember(state.document) { PopRenderCache() }
    val line = frame.line
    val interlude = frame.interlude
    val allowMotion = LocalAgoraMotionPolicy.current.allowSpatialTransitions
    val foreground = if (albumBackground) Color.White else MaterialTheme.colorScheme.onSurface
    val activeColor = if (albumBackground) Color.White else MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier.testTag("home_pop_stage").padding(horizontal = 24.dp, vertical = 12.dp)) {
        val density = LocalDensity.current
        val translation = if (line != null && interlude == null &&
            state.translationDisplayMode == LyricsOverlayService.TRANSLATION_BILINGUAL) line.translation else ""
        val translationStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = appearance.translationSize.sp,
            lineHeight = (appearance.translationSize * 1.4f).sp, color = foreground.copy(alpha = .75f))
        val measurer = rememberTextMeasurer(cacheSize = 1)
        val translationLayout = remember(translation, translationStyle, maxWidth, density) {
            if (translation.isBlank()) null else measurer.measure(AnnotatedString(translation), style = translationStyle,
                constraints = Constraints(maxWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)),
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        val translationGap = with(density) { 12.dp.toPx() }
        val reservedTranslation = with(density) { translationLayout?.let { (it.size.height + translationGap).toDp() } ?: 0.dp }
        val contentBottom = remember(line, frame.primary, appearance, style) { mutableFloatStateOf(0f) }
        AnimatedContent(targetState = frame, modifier = Modifier.fillMaxSize().padding(bottom = reservedTranslation),
            contentKey = { it.line?.startMs to it.primary }, label = "popSentenceTransition",
            transitionSpec = {
                val duration = if (allowMotion) targetState.timing?.transitionMs ?: 320 else 0
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
                    PopLyricCanvas(sentence.timeline, requireNotNull(sentence.timing), frame.upcoming, renderCache,
                        position, appearance, style, allowMotion, foreground, activeColor,
                        cameraPath = if (sentence.timeline === frame.timeline) frame.cameraPath else null,
                        contentBottom = if (sentence.timeline === frame.timeline) contentBottom else null,
                        modifier = Modifier.fillMaxSize().semantics { text = AnnotatedString(sentence.primary) }
                            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                                onClick = { state.seek(currentLine.startMs - state.displayOffsetMs) },
                                onLongClickLabel = "摘句与分享", onLongClick = { state.beginSharing(sentence.index) }))
                }
            }
        }
        if ((frame.interlude != null || frame.line == null) && frame.upcoming != null) {
            // Imminent lyrics also warm during an intro/interlude, where no current canvas exists.
            PopLyricCanvas(frame.upcoming.timeline, frame.upcoming.timing, null, renderCache, position,
                appearance, style, false, foreground, activeColor, null, null,
                Modifier.fillMaxSize(), preheatOnly = true)
        }
        if (translationLayout != null) {
            Text(translation, style = translationStyle,
                maxLines = 3, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
                    .offset { IntOffset(0, (contentBottom.floatValue + translationGap).roundToInt()) }
                    .testTag("home_pop_translation"))
        }

    }
}

private data class PopGlyph(val layout: TextLayoutResult, val slot: PopLyricSlot, val angle: Float, val entranceOffset: Offset)
private data class PopRow(val from: Int, val until: Int, val startMs: Long, val entranceMs: Long,
    val passStartMs: Long, val passMs: Long, val travel: Float)
private data class PopScene(val glyphs: List<PopGlyph>, val rows: List<PopRow>, val scale: Float,
    val cameraPath: PopCameraPath, val bottom: Float)
private data class PopMeasureKey(val timeline: PopLyricTimeline, val textStyle: androidx.compose.ui.text.TextStyle,
    val width: Int, val semantic: Boolean, val density: Float, val fontScale: Float)
private data class PopMeasured(val layouts: List<TextLayoutResult>, val groups: PopLyricGroups)
private data class PopSceneKey(val measured: PopMeasured, val timeline: PopLyricTimeline, val timing: PopLyricRenderTiming,
    val width: Float, val height: Float, val gap: Float, val density: Float, val style: PopLyricsStyle)
private class PopRenderCache {
    val measured = PopLyricCache<PopMeasureKey, PopMeasured>()
    val scenes = PopLyricCache<PopSceneKey, PopScene>()
}

@Composable
private fun PopLyricCanvas(timeline: PopLyricTimeline, timing: PopLyricRenderTiming, upcoming: PopUpcomingSentence?,
    cache: PopRenderCache, position: State<Long>, appearance: HomeLyricAppearance,
    style: PopLyricsStyle, allowMotion: Boolean, foreground: Color, activeColor: Color,
    cameraPath: MutableState<PopCameraPath?>?, contentBottom: MutableState<Float>?, modifier: Modifier, preheatOnly: Boolean = false) {
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
        textAlign = TextAlign.Center, lineBreak = LineBreak.Heading, hyphens = Hyphens.Auto,
    )
    fun measure(candidate: PopLyricTimeline) = cache.measured.getOrPut(PopMeasureKey(candidate, textStyle, shapingWidth,
        style.semanticGrouping, density.density, density.fontScale)) {
        PopMeasured(candidate.tokens.map { token ->
            measurer.measure(AnnotatedString(popLyricPreview(token.text)), style = textStyle,
                constraints = Constraints(maxWidth = shapingWidth), maxLines = 8, overflow = TextOverflow.Ellipsis)
        }, groupPopLyrics(candidate.tokens, style.semanticGrouping))
    }
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val gap = with(density) { (appearance.lineSpacing / 3f + 4f).dp.toPx() }
        fun scene(candidate: PopLyricTimeline, profile: PopLyricRenderTiming): PopScene {
            val measured = measure(candidate)
            return cache.scenes.getOrPut(PopSceneKey(measured, candidate, profile, width, height, gap, density.density, style)) {
                val layouts = measured.layouts
                val reservedScale = if (candidate.wordTimed) style.maxWordScale else 1f
                val reservedRotation = if (candidate.wordTimed) style.rotationDegrees else 0
                val angles = FloatArray(layouts.size) { index ->
                    val seed = Math.floorMod(candidate.tokens[index].text.hashCode().toLong() + index * 17L, 997L).toFloat()
                    if (candidate.wordTimed) ((seed % 7f) - 3f) * style.rotationDegrees / 20f else 0f
                }
                val rotationReach = (sin(reservedRotation * 1.15 * Math.PI / 180) * reservedScale).toFloat()
                val extents = layouts.mapIndexed { index, layout ->
                    if (style.dynamicSpacing) popGlyphExtent(layout.size.width.toFloat(), layout.size.height.toFloat(),
                        reservedScale, maxOf(kotlin.math.abs(angles[index]), kotlin.math.abs(angles[index] + reservedRotation)))
                    else PopGlyphExtent(layout.size.width * reservedScale + layout.size.height * rotationReach,
                        layout.size.height * reservedScale + layout.size.width * rotationReach)
                }
                val gaps = FloatArray(layouts.size + 1) { index ->
                    (if (measured.groups.tightBoundaries[index]) style.innerGapDp else style.groupGapDp) * density.density
                }
                val rowTravel = if (candidate.wordTimed && profile.transitionMs > 0) 12f * density.density * style.rowMotionPercent / 100f else 0f
                val packing = packPopLyrics(FloatArray(layouts.size) { layouts[it].size.width.toFloat() },
                    FloatArray(layouts.size) { layouts[it].size.height.toFloat() }, width * .92f, height * .9f, gap,
                    style.layout, reservedScale, reservedRotation, measured.groups.penalties, extents, gaps,
                    gap * style.rowGapPercent / 100f, style.staggerPercent, style.lineWidthPercent, rowTravel)
                val travelX = 72f * density.density * style.travelPercent / 100f
                val travelY = 48f * density.density * style.travelPercent / 100f
                val glyphs = layouts.mapIndexed { index, layout ->
                    val packed = packing.slots[index]
                    val slot = PopLyricSlot(packed.x + width * .04f, packed.y + height * .05f)
                    val seed = Math.floorMod(candidate.tokens[index].text.hashCode().toLong() + index * 17L, 997L).toFloat()
                    val halfWidth = extents[index].width * packing.scale / 2f
                    val halfHeight = extents[index].height * packing.scale / 2f + rowTravel * packing.scale
                    val dx = (if (style.entrance == "scatter") sin(seed) * travelX else 0f).coerceIn(
                        (halfWidth - slot.x).coerceAtMost(0f), (width - halfWidth - slot.x).coerceAtLeast(0f))
                    val dy = (when (style.entrance) { "scatter" -> cos(seed) * travelY; "rise" -> travelY; else -> 0f }).coerceIn(
                        (halfHeight - slot.y).coerceAtMost(0f), (height - halfHeight - slot.y).coerceAtLeast(0f))
                    PopGlyph(layout, slot, angles[index], Offset(dx, dy))
                }
                val rows = packing.rowStarts.mapIndexed { row, from ->
                    val until = packing.rowStarts.getOrNull(row + 1) ?: glyphs.size
                    val last = (from until until).maxBy { profile.passStartMs[it] }
                    PopRow(from, until, candidate.tokens[from].startMs - profile.lookaheadMs[from],
                        profile.entranceMs[from].coerceAtMost(300L), profile.passStartMs[last],
                        profile.passMs[last], rowTravel * packing.scale)
                }
                fun halfWidth(index: Int) = extents[index].width * packing.scale / 2f
                fun halfHeight(index: Int) = (extents[index].height / 2f + rowTravel) * packing.scale
                val left = glyphs.indices.minOfOrNull { glyphs[it].slot.x - halfWidth(it) } ?: 0f
                val right = glyphs.indices.maxOfOrNull { glyphs[it].slot.x + halfWidth(it) } ?: width
                val top = glyphs.indices.minOfOrNull { glyphs[it].slot.y - halfHeight(it) } ?: 0f
                val bottom = glyphs.indices.maxOfOrNull { glyphs[it].slot.y + halfHeight(it) } ?: height
                val bounds = PopCameraBounds(
                    minOf(0f, (width * .06f - left) / width.coerceAtLeast(1f)) * .8f,
                    maxOf(0f, (width * .94f - right) / width.coerceAtLeast(1f)) * .8f,
                    minOf(0f, (height * .06f - top) / height.coerceAtLeast(1f)) * .7f,
                    maxOf(0f, (height * .94f - bottom) / height.coerceAtLeast(1f)) * .7f,
                )
                PopScene(glyphs, rows, packing.scale, PopStageMotion.path(candidate, glyphs.map {
                    PopCameraPoint(it.slot.x / width.coerceAtLeast(1f) - .5f, it.slot.y / height.coerceAtLeast(1f) - .5f)
                }, style, bounds), bottom)
            }
        }
        val current = remember(timeline, timing, textStyle, density, width, height, gap, style) { scene(timeline, timing) }
        // Preheat only the imminent sentence, off the switching frame. The same bounded
        // measurement/scene caches are used when it becomes current; no page-wide cache.
        LaunchedEffect(upcoming, textStyle, density, width, height, gap, style) {
            if (upcoming != null) {
                withFrameNanos { }
                scene(upcoming.timeline, upcoming.timing)
            }
        }
        if (preheatOnly) return@BoxWithConstraints
        SideEffect {
            if (cameraPath != null) cameraPath.value = current.cameraPath
            if (contentBottom != null) contentBottom.value = current.bottom
        }
        Canvas(Modifier.fillMaxSize().testTag("home_pop_canvas")) {
            val now = position.value
            val animated = timeline.wordTimed && allowMotion
            for (row in current.rows) {
                val enteredRow = if (animated && row.entranceMs > 0L)
                    PopLyricMotion.enter(now - row.startMs, row.entranceMs) else 1f
                val passedRow = if (animated && row.passMs > 0L)
                    PopLyricMotion.pass(now - row.passStartMs, row.passMs) else 0f
                val rowY = if (animated) row.travel * (1f - enteredRow + passedRow * (1f - passedRow) * .3f) else 0f
                withTransform({ translate(0f, rowY) }) {
                    for (index in row.from until row.until) {
                        val token = timeline.tokens[index]
                        val elapsed = now - (token.startMs - timing.lookaheadMs[index] + token.visualDelayMs)
                        if (animated && elapsed < 0L) continue
                        val entered = if (animated && timing.entranceMs[index] > 0L)
                            PopLyricMotion.enter(elapsed, timing.entranceMs[index]) else 1f
                        val passed = if (animated && timing.passMs[index] > 0L)
                            PopLyricMotion.pass(now - timing.passStartMs[index], timing.passMs[index]) else if (animated && now >= timing.passStartMs[index]) 1f else 0f
                        val fadeMs = minOf(100L, timing.entranceMs[index]).coerceAtLeast(1L)
                        val alpha = if (animated) (if (timing.entranceMs[index] == 0L) 1f else (elapsed.toFloat() / fadeMs).coerceIn(0f, 1f)) *
                            (1f - passed * (1f - style.passedOpacityPercent / 100f)) else 1f
                        val scale = if (animated) (.5f + entered * (style.activeScalePercent / 100f - .5f) -
                            passed * (style.activeScalePercent / 100f - 1f)).coerceIn(.5f, style.maxWordScale) else 1f
                        val glyph = current.glyphs[index]
                        withTransform({
                            val remaining = if (animated) (1f - entered).coerceAtLeast(0f) else 0f
                            translate(glyph.slot.x + glyph.entranceOffset.x * remaining, glyph.slot.y + glyph.entranceOffset.y * remaining)
                            scale(current.scale * scale, current.scale * scale, pivot = Offset.Zero)
                            if (animated) rotate(glyph.angle + style.rotationDegrees * (1f - entered), pivot = Offset.Zero)
                            translate(-glyph.layout.size.width / 2f, -glyph.layout.size.height / 2f)
                        }) {
                            drawText(glyph.layout, color = if (animated && now < token.endMs) activeColor else foreground, alpha = alpha)
                        }
                    }
                }
            }
        }
    }
}
