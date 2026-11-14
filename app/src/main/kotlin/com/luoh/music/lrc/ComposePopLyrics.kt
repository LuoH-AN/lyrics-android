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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
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
    val timing: PopLyricRenderTiming?, val upcoming: PopUpcomingSentence?,
    val phraseMotionUntil: MutableState<Long?>)

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
    val phraseMotionUntil = remember(line, primary, style) { mutableStateOf<Long?>(null) }
    val policy = LocalAgoraMotionPolicy.current
    val allowMotion = policy.allowSpatialTransitions
    PopDisplayRefresh(state.presentationActive && state.playing && allowMotion)
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
        state.displayOffsetMs, allowMotion, style, cameraPath, timing, phraseMotionUntil) {
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
            // Words and camera share the same display frame. A nanosecond threshold would
            // accidentally skip frames on 120 Hz panels whose vsync intervals vary slightly.
            val waitMs = when {
                interlude != null -> 0L
                !allowMotion || timeline == null -> {
                    val onset = timeline?.tokens?.firstOrNull { it.startMs > now }?.startMs
                    (minOf(nextBoundary ?: state.clock.durationMs, onset ?: Long.MAX_VALUE) - now).coerceIn(1L, 1000L)
                }
                phraseMotionUntil.value?.let { now < it } == true -> 0L
                (style.continuousCameraMotion || cameraMoving) && interlude == null -> 0L
                else -> minOf(wordDelay, cameraDelay)
            }
            if (waitMs > 0L) delay((waitMs / state.clock.speed).toLong().coerceIn(1L, 1000L))
            withFrameNanos { update() }
        }
    }
    return PopPlaybackFrame(position, index, line, primary, timeline, interlude, cameraPath, cameraPosition, timing, upcoming,
        phraseMotionUntil)
}

/** Clock reads only invalidate native drawing and the shared camera's graphics layers. */
@Composable
internal fun PopLyricsStage(state: HomePresentationState, modifier: Modifier, albumBackground: Boolean,
    appearance: HomeLyricAppearance, style: PopLyricsStyle, frame: PopPlaybackFrame,
    onLyricTap: (() -> Unit)? = null, interactive: Boolean = true) {
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
                    "zoom" -> fadeIn(tween((duration / 2).coerceAtLeast(0))) + scaleIn(tween(duration), initialScale = .94f)
                    "slide" -> fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { it / 3 }
                    else -> fadeIn(tween(duration))
                }
                val exit = when (style.transition) {
                    "zoom" -> fadeOut(tween(duration)) + scaleOut(tween(duration), targetScale = 1.06f)
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
                        modifier = Modifier.fillMaxSize().then(if (interactive) Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                                onClick = { if (onLyricTap != null) onLyricTap() else state.seek(currentLine.startMs - state.displayOffsetMs) },
                                onLongClickLabel = "摘句与分享", onLongClick = { state.beginSharing(sentence.index) }) else Modifier),
                        playing = state.playing,
                        phraseMotionUntil = if (sentence.timeline === frame.timeline) frame.phraseMotionUntil else null)
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

private data class PopGlyph(val layout: TextLayoutResult, val slot: PopLyricSlot, val angle: Float,
    val entranceOffset: Offset, val baseScale: Float, val glow: Shadow)
private data class PopRow(val from: Int, val until: Int, val startMs: Long, val entranceMs: Long,
    val passStartMs: Long, val passMs: Long, val travel: Float, val travelX: Float, val center: Offset)
private data class PopScene(val timeline: PopLyricTimeline, val timing: PopLyricRenderTiming,
    val glyphs: List<PopGlyph>, val rows: List<PopRow>, val scale: Float, val bounds: PopCameraBounds, val bottom: Float)
private data class PopMeasureKey(val timeline: PopLyricTimeline, val textStyle: androidx.compose.ui.text.TextStyle,
    val width: Int, val semantic: Boolean, val density: Float, val fontScale: Float)
private data class PopMeasured(val layouts: List<TextLayoutResult>, val groups: PopLyricGroups,
    val timeline: PopLyricTimeline, val sourceIndices: List<Int>)
private data class PopScenes(val timeline: PopLyricTimeline, val ranges: List<PopLyricPage>, val pages: List<PopScene>,
    val handoffs: List<PopPhraseHandoff>, val cameraPath: PopCameraPath)
private data class PopSceneKey(val measured: PopMeasured, val timeline: PopLyricTimeline, val timing: PopLyricRenderTiming,
    val width: Float, val height: Float, val gap: Float, val density: Float, val style: PopLyricsStyle, val accent: Color)
private class PopRenderCache {
    val measured = PopLyricCache<PopMeasureKey, PopMeasured>()
    val scenes = PopLyricCache<PopSceneKey, PopScenes>()
}

@Composable
private fun PopLyricCanvas(timeline: PopLyricTimeline, timing: PopLyricRenderTiming, upcoming: PopUpcomingSentence?,
    cache: PopRenderCache, position: State<Long>, appearance: HomeLyricAppearance,
    style: PopLyricsStyle, allowMotion: Boolean, foreground: Color, activeColor: Color,
    cameraPath: MutableState<PopCameraPath?>?, contentBottom: MutableState<Float>?, modifier: Modifier,
    preheatOnly: Boolean = false, playing: Boolean = false, phraseMotionUntil: MutableState<Long?>? = null) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Shaping width is stable while player controls resize the stage; only packing repeats.
    val shapingWidth = with(density) {
        (minOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(160) * .85f *
            (style.lineWidthPercent / 100f).coerceAtMost(1f)).dp.roundToPx()
    }.coerceAtLeast(1)
    val textStyle = MaterialTheme.typography.headlineLarge.copy(
        fontSize = (appearance.fontSize + 10).coerceIn(24, 64).sp,
        lineHeight = ((appearance.fontSize + 10).coerceIn(24, 64) * 1.15f).sp,
        fontWeight = FontWeight((appearance.fontWeight + 200).coerceAtMost(900)), color = foreground,
        textAlign = TextAlign.Center, lineBreak = LineBreak.Heading, hyphens = Hyphens.Auto,
    )
    fun measure(candidate: PopLyricTimeline) = cache.measured.getOrPut(PopMeasureKey(candidate, textStyle, shapingWidth,
        style.semanticGrouping, density.density, density.fontScale)) {
        val expanded = popLayoutTokens(candidate)
        val renderTimeline = PopLyricTimeline(expanded.tokens, candidate.wordTimed, candidate.startMs, candidate.endMs)
        PopMeasured(expanded.tokens.map { token ->
            measurer.measure(AnnotatedString(popLyricPreview(token.text)), style = textStyle,
                constraints = Constraints(maxWidth = shapingWidth), maxLines = 8, overflow = TextOverflow.Ellipsis)
        }, groupPopLyrics(expanded.tokens, style.semanticGrouping), renderTimeline, expanded.sourceIndices)
    }

    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val gap = with(density) { (appearance.lineSpacing / 3f + 4f).dp.toPx() }
        fun scene(candidate: PopLyricTimeline, profile: PopLyricRenderTiming): PopScenes {
            val measured = measure(candidate)
            return cache.scenes.getOrPut(PopSceneKey(measured, candidate, profile, width, height, gap, density.density, style, activeColor)) {
                val renderTimeline = measured.timeline
                val renderTiming = PopLyricRenderTiming(profile.transitionMs,
                    measured.sourceIndices.map { profile.entranceMs[it] }.toLongArray(),
                    measured.sourceIndices.map { profile.lookaheadMs[it] }.toLongArray(),
                    measured.sourceIndices.map { profile.passStartMs[it] }.toLongArray(),
                    measured.sourceIndices.map { profile.passMs[it] }.toLongArray(),
                    measured.sourceIndices.map { profile.sustain.getOrNull(it) })
                val layouts = measured.layouts
                val reservedScale = if (renderTimeline.wordTimed) style.maxWordScale else 1f
                val reservedRotation = if (renderTimeline.wordTimed) style.rotationDegrees else 0
                // Plan every glyph once. Phrase boundaries must not reseed its size or angle.
                val emphasis = FloatArray(layouts.size) { 1f }
                if (renderTimeline.wordTimed) for ((groupIndex, group) in measured.groups.units.withIndex()) {
                    val seed = Math.floorMod(renderTimeline.startMs + groupIndex * 137L +
                        renderTimeline.tokens[group.from].text.hashCode(), 997L) / 997f
                    val sustain = (group.from until group.until).maxOf {
                        renderTimeline.tokens[it].endMs - renderTimeline.tokens[it].startMs
                    }.coerceIn(0L, 1000L) / 1000f
                    val variation = if (style.layout == "centered") .6f else 1f
                    val phraseScale = 1f + (-.08f + seed * .27f + sustain * .12f) * variation
                    for (index in group.from until group.until) emphasis[index] = phraseScale
                }
                val angles = FloatArray(layouts.size) { index ->
                    val seed = Math.floorMod(renderTimeline.tokens[index].text.hashCode().toLong() + index * 17L, 997L).toFloat()
                    if (renderTimeline.wordTimed) ((seed % 7f) - 3f) * style.rotationDegrees / 20f else 0f
                }
                val extents = layouts.mapIndexed { index, layout ->
                    val shake = renderTiming.sustain[index]
                    val glyphScale = reservedScale * emphasis[index] * 1.015f
                    val maxAngle = maxOf(kotlin.math.abs(angles[index]) + reservedRotation * .1f,
                        kotlin.math.abs(angles[index] + reservedRotation)) + (shake?.reachDegrees ?: 0f)
                    val extent = if (style.dynamicSpacing) popGlyphExtent(layout.size.width.toFloat(),
                        layout.size.height.toFloat(), glyphScale, maxAngle) else {
                        val reach = (sin((reservedRotation + (shake?.reachDegrees ?: 0f)) * 1.15 * Math.PI / 180) * reservedScale).toFloat()
                        PopGlyphExtent((layout.size.width * reservedScale + layout.size.height * reach) * emphasis[index] * 1.015f,
                            (layout.size.height * reservedScale + layout.size.width * reach) * emphasis[index] * 1.015f)
                    }
                    PopGlyphExtent(extent.width + (shake?.reachXDp ?: 0f) * density.density * 2f * glyphScale,
                        extent.height + (shake?.reachYDp ?: 0f) * density.density * 2f * glyphScale)
                }
                val gaps = FloatArray(layouts.size + 1) { index ->
                    (if (measured.groups.tightBoundaries[index]) style.innerGapDp else style.groupGapDp) * density.density
                }
                val rowTravel = if (renderTimeline.wordTimed && profile.transitionMs > 0)
                    20f * density.density * style.rowMotionPercent / 100f else 0f
                val rowTravelX = rowTravel * 1.6f
                val typicalHeight = extents.map { it.height + rowTravel * 2f }.sorted()[extents.size / 2]
                val rowLimit = popLyricRowLimit((width * .92f).coerceAtLeast(1f), typicalHeight, style.layout, style.lineWidthPercent)
                val availableRows = (height * .9f / (typicalHeight + gap * style.rowGapPercent / 100f)).coerceIn(1f, 2f)
                // Pagination needs widths, not an expensive layout of the entire long sentence.
                val ranges = if (style.paginate && renderTimeline.wordTimed) paginatePopLyrics(
                    renderTimeline.tokens, measured.groups.penalties,
                    FloatArray(extents.size) { extents[it].width + gaps[it] },
                    (rowLimit - rowTravelX * 2f).coerceAtLeast(1f) * availableRows, style.pageLength)
                    else listOf(PopLyricPage(0, renderTimeline.tokens.size))
                fun buildPage(page: PopLyricPage): PopScene {
                    val tokens = renderTimeline.tokens.subList(page.from, page.until)
                    val timeline = PopLyricTimeline(tokens, renderTimeline.wordTimed,
                        if (page.from == 0) renderTimeline.startMs else tokens.first().startMs,
                        renderTimeline.tokens.getOrNull(page.until)?.startMs ?: renderTimeline.endMs)
                    val timing = PopLyricRenderTiming(renderTiming.transitionMs,
                        renderTiming.entranceMs.copyOfRange(page.from, page.until),
                        renderTiming.lookaheadMs.copyOfRange(page.from, page.until),
                        renderTiming.passStartMs.copyOfRange(page.from, page.until),
                        renderTiming.passMs.copyOfRange(page.from, page.until),
                        renderTiming.sustain.subList(page.from, page.until))
                    val pageLayouts = layouts.subList(page.from, page.until)
                    val packing = packPopLyrics(FloatArray(tokens.size) { pageLayouts[it].size.width.toFloat() },
                        FloatArray(tokens.size) { pageLayouts[it].size.height.toFloat() }, (width * .92f).coerceAtLeast(1f), height * .9f, gap,
                        style.layout, reservedScale, reservedRotation, measured.groups.penalties.copyOfRange(page.from, page.until + 1),
                        extents.subList(page.from, page.until), gaps.copyOfRange(page.from, page.until + 1),
                        gap * style.rowGapPercent / 100f, style.staggerPercent, style.lineWidthPercent, rowTravel,
                        maxFitScale = 1.8f, horizontalMotionReach = rowTravelX)
                    val travelX = 72f * density.density * style.travelPercent / 100f
                    val travelY = 48f * density.density * style.travelPercent / 100f
                    val glyphs = pageLayouts.mapIndexed { index, layout ->
                        val source = page.from + index
                        val packed = packing.slots[index]
                        val slot = PopLyricSlot(packed.x + width * .04f, packed.y + height * .05f)
                        val seed = Math.floorMod(tokens[index].text.hashCode().toLong() + source * 17L, 997L).toFloat()
                        val halfWidth = (extents[source].width / 2f + rowTravelX) * packing.scale
                        val halfHeight = (extents[source].height / 2f + rowTravel) * packing.scale
                        val dx = (if (style.entrance == "scatter") sin(seed) * travelX else 0f).coerceIn(
                            (halfWidth - slot.x).coerceAtMost(0f), (width - halfWidth - slot.x).coerceAtLeast(0f))
                        val dy = (when (style.entrance) { "scatter" -> cos(seed) * travelY; "rise" -> travelY; else -> 0f }).coerceIn(
                            (halfHeight - slot.y).coerceAtMost(0f), (height - halfHeight - slot.y).coerceAtLeast(0f))
                        PopGlyph(layout, slot, angles[source], Offset(dx, dy), emphasis[source],
                            Shadow(activeColor.copy(alpha = .45f), Offset.Zero, 7f * density.density))
                    }
                    val rows = packing.rowStarts.mapIndexed { row, from ->
                        val until = packing.rowStarts.getOrNull(row + 1) ?: glyphs.size
                        val last = (from until until).maxBy { timing.passStartMs[it] }
                        PopRow(from, until, tokens[from].startMs - timing.lookaheadMs[from],
                            timing.entranceMs[from].coerceAtMost(300L), timing.passStartMs[last],
                            timing.passMs[last], rowTravel * packing.scale,
                            rowTravelX * packing.scale * (if (row % 2 == 0) -1f else 1f),
                            Offset((from until until).map { glyphs[it].slot.x }.average().toFloat(), glyphs[from].slot.y))
                    }
                    fun halfWidth(index: Int) = (extents[page.from + index].width / 2f + rowTravelX) * packing.scale
                    fun halfHeight(index: Int) = (extents[page.from + index].height / 2f + rowTravel) * packing.scale
                    val left = glyphs.indices.minOfOrNull { glyphs[it].slot.x - halfWidth(it) } ?: 0f
                    val right = glyphs.indices.maxOfOrNull { glyphs[it].slot.x + halfWidth(it) } ?: width
                    val top = glyphs.indices.minOfOrNull { glyphs[it].slot.y - halfHeight(it) } ?: 0f
                    val bottom = glyphs.indices.maxOfOrNull { glyphs[it].slot.y + halfHeight(it) } ?: height
                    val bounds = PopCameraBounds(
                        minOf(0f, (width * .06f - left) / width.coerceAtLeast(1f)) * .8f,
                        maxOf(0f, (width * .94f - right) / width.coerceAtLeast(1f)) * .8f,
                        minOf(0f, (height * .06f - top) / height.coerceAtLeast(1f)) * .7f,
                        maxOf(0f, (height * .94f - bottom) / height.coerceAtLeast(1f)) * .7f)
                    return PopScene(timeline, timing, glyphs, rows, packing.scale, bounds, bottom)
                }
                val pages = ranges.map(::buildPage)
                // One C1 trajectory spans all phrases; switching text never resets the camera.
                val bounds = PopCameraBounds(pages.maxOf { it.bounds.minX }, pages.minOf { it.bounds.maxX },
                    pages.maxOf { it.bounds.minY }, pages.minOf { it.bounds.maxY })
                val camera = PopStageMotion.path(renderTimeline, pages.flatMap { page -> page.glyphs.map {
                    PopCameraPoint(it.slot.x / width.coerceAtLeast(1f) - .5f, it.slot.y / height.coerceAtLeast(1f) - .5f)
                } }, style, bounds, renderTiming)
                PopScenes(renderTimeline, ranges, pages, popPhraseHandoffs(ranges, renderTimeline), camera)
            }
        }
        val scenes = remember(timeline, timing, textStyle, density, width, height, gap, style, activeColor) { scene(timeline, timing) }
        val animateHandoff = allowMotion && playing
        val window by remember(scenes, position, animateHandoff) { derivedStateOf {
            popPhraseWindow(scenes.ranges, scenes.timeline, scenes.handoffs, position.value, animateHandoff)
        } }
        val current = scenes.pages[window.current]
        // Preheat only the imminent sentence, off the switching frame. The same bounded
        // measurement/scene caches are used when it becomes current; no page-wide cache.
        LaunchedEffect(upcoming, textStyle, density, width, height, gap, style, activeColor) {
            if (upcoming != null) {
                withFrameNanos { }
                scene(upcoming.timeline, upcoming.timing)
            }
        }
        if (preheatOnly) return@BoxWithConstraints
        SideEffect {
            if (cameraPath != null) cameraPath.value = scenes.cameraPath
            if (contentBottom != null) contentBottom.value = maxOf(current.bottom,
                window.retiring?.let { scenes.pages[it].bottom } ?: 0f)
            if (phraseMotionUntil != null) phraseMotionUntil.value = if (window.retiring == null) null else
                scenes.handoffs[window.current].let { it.startMs + it.durationMs }
        }
        Canvas(Modifier.fillMaxSize().testTag("home_pop_canvas").semantics {
            text = AnnotatedString(current.timeline.tokens.joinToString("") { it.text })
        }) {
            val now = position.value
            fun drawPhrase(page: PopScene, phraseAlpha: Float, offsetY: Float) {
                if (phraseAlpha <= 0f) return
                val timeline = page.timeline
                val timing = page.timing
                val animated = timeline.wordTimed && allowMotion
                withTransform({ translate(0f, offsetY) }) {
                    for (row in page.rows) {
                        val enteredRow = if (animated && row.entranceMs > 0L)
                            PopLyricMotion.enter(now - row.startMs, row.entranceMs) else 1f
                        val passedRow = if (animated && row.passMs > 0L)
                            PopLyricMotion.pass(now - row.passStartMs, row.passMs) else 0f
                        val rowY = if (animated) row.travel * (1f - enteredRow + passedRow * (1f - passedRow) * .3f) else 0f
                        withTransform({
                            translate(if (animated) row.travelX * (1f - enteredRow) else 0f, rowY)
                            val rowScale = if (animated && row.travel > 0f) .90f + enteredRow * .10f else 1f
                            scale(rowScale, rowScale, row.center)
                        }) {
                            for (index in row.from until row.until) {
                                val token = timeline.tokens[index]
                                val elapsed = now - (token.startMs - timing.lookaheadMs[index] + token.visualDelayMs)
                                if (animated && elapsed < 0L) continue
                                val entered = if (animated && timing.entranceMs[index] > 0L)
                                    PopLyricMotion.enter(elapsed, timing.entranceMs[index]) else 1f
                                val passed = if (animated && timing.passMs[index] > 0L)
                                    PopLyricMotion.pass(now - timing.passStartMs[index], timing.passMs[index]) else if (animated && now >= timing.passStartMs[index]) 1f else 0f
                                val fadeMs = minOf(65L, timing.entranceMs[index]).coerceAtLeast(1L)
                                val wordAlpha = if (animated) (if (timing.entranceMs[index] == 0L) 1f else (elapsed.toFloat() / fadeMs).coerceIn(0f, 1f)) *
                                    (1f - passed * (1f - style.passedOpacityPercent / 100f)) else 1f
                                val entranceScale = .5f + entered * (style.activeScalePercent / 100f - .5f)
                                val scale = if (animated) (entranceScale + (1f - entranceScale) * passed)
                                    .coerceIn(.5f, style.maxWordScale) else 1f
                                val glyph = page.glyphs[index]
                                val shake = PopSustainMotion.sample(if (animated && playing) timing.sustain.getOrNull(index) else null, now)
                                withTransform({
                                    // Preserve the negative overshoot: the word lands, rebounds, then settles.
                                    val remaining = if (animated) 1f - entered else 0f
                                    translate(glyph.slot.x + glyph.entranceOffset.x * remaining, glyph.slot.y + glyph.entranceOffset.y * remaining)
                                    scale(page.scale * scale * glyph.baseScale, page.scale * scale * glyph.baseScale, pivot = Offset.Zero)
                                    translate(shake.xDp * density.density, shake.yDp * density.density)
                                    if (animated) rotate(glyph.angle * (1f - passed * .65f) + style.rotationDegrees * (1f - entered) + shake.degrees, pivot = Offset.Zero)
                                    translate(-glyph.layout.size.width / 2f, -glyph.layout.size.height / 2f)
                                }) {
                                    drawText(glyph.layout, color = if (animated) lerp(activeColor, foreground, passed) else foreground,
                                        alpha = wordAlpha * phraseAlpha, shadow = if (animated && now >= token.startMs && now < token.endMs)
                                            glyph.glow else Shadow.None)
                                }
                            }
                        }
                    }
                }
            }
            val handoff = scenes.handoffs[window.current]
            val progress = if (window.retiring != null) PopLyricMotion.pass(now - handoff.startMs, handoff.durationMs) else 1f
            val travel = (height * .025f).coerceAtMost(12f * density.density)
            window.retiring?.let { drawPhrase(scenes.pages[it], 1f - progress, -travel * progress) }
            drawPhrase(current, progress, travel * (1f - progress))
        }
    }
}
