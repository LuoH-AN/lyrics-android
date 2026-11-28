package com.luoh.music.lrc.ui.home.pop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.R
import com.luoh.music.lrc.lyrics.LyricInterlude
import com.luoh.music.lrc.lyrics.LyricLine
import com.luoh.music.lrc.lyrics.LyricTiming
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.home.HomeLyricAppearance
import com.luoh.music.lrc.ui.home.HomePresentationState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal data class PopPlaybackFrame(val position: State<Long>, val index: Int, val line: LyricLine?,
    val primary: String, val timeline: PopLyricTimeline?, val interlude: LyricInterlude?,
    val cameraPath: MutableState<PopCameraPath?>, val cameraPosition: State<Long>,
    val timing: PopLyricRenderTiming?, val upcoming: PopUpcomingSentence?,
    val phraseMotionUntil: MutableState<Long?>, val previous: PopPreviousSentence? = null)

internal data class PopPreviousSentence(val line: LyricLine, val primary: String,
    val timeline: PopLyricTimeline, val timing: PopLyricRenderTiming)
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
    val cameraPath = remember(document, style) { mutableStateOf<PopCameraPath?>(null) }
    val phraseMotionUntil = remember(line, primary, style) { mutableStateOf<Long?>(null) }
    val policy = LocalAgoraMotionPolicy.current
    val allowMotion = policy.allowSpatialTransitions
    PopDisplayRefresh(state.presentationActive && state.playing && allowMotion)
    val nextLine = document.lines.getOrNull(index + 1)
    val nextLineMs = nextLine?.startMs
    val timing = timeline?.let { profile(it, nextLineMs) }
    val previousLine = document.lines.getOrNull(index - 1)
    val previous = remember(previousLine, line, style, state.translationDisplayMode, timelineCache) {
        previousLine?.let {
            val text = if (state.translationDisplayMode == LyricsOverlayService.TRANSLATION_TRANSLATED &&
                it.translation.isNotBlank()) it.translation else it.text
            val prepared = prepare(it, text)
            PopPreviousSentence(it, text, prepared, profile(prepared, line?.startMs))
        }
    }
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
                index > 0 && timing != null && timeline != null &&
                    now - timeline.startMs in 0L until timing.transitionMs.toLong() -> 0L
                phraseMotionUntil.value?.let { now < it } == true -> 0L
                (style.continuousCameraMotion || cameraMoving) && interlude == null -> 0L
                else -> minOf(wordDelay, cameraDelay)
            }
            if (waitMs > 0L) delay((waitMs / state.clock.speed).toLong().coerceIn(1L, 1000L))
            withFrameNanos { update() }
        }
    }
    return PopPlaybackFrame(position, index, line, primary, timeline, interlude, cameraPath, cameraPosition, timing, upcoming,
        phraseMotionUntil, previous)
}

/** Sentence/phrase handoffs use the same playback clock as words and camera. */
@Composable
internal fun PopLyricsStage(state: HomePresentationState, modifier: Modifier, albumBackground: Boolean,
    appearance: HomeLyricAppearance, style: PopLyricsStyle, frame: PopPlaybackFrame,
    onLyricTap: (() -> Unit)? = null, interactive: Boolean = true) {
    val context = LocalContext.current
    val cache = remember(state.document) { PopRenderCache() }
    val position = frame.position
    val allowMotion = LocalAgoraMotionPolicy.current.allowSpatialTransitions
    val foreground = if (albumBackground) Color.White else MaterialTheme.colorScheme.onSurface
    val activeColor = if (albumBackground) Color.White else MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier.testTag("home_pop_stage")) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val bilingual = state.translationDisplayMode == LyricsOverlayService.TRANSLATION_BILINGUAL
        val capacity = remember(width, height, density, appearance, style, bilingual) {
            popStageLayout(width, height, density.density, density.fontScale, appearance, style, bilingual)
        }
        val captionMeasurer = rememberTextMeasurer(cacheSize = 2)
        val captionStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = (appearance.translationSize * capacity.captionScale).sp,
            lineHeight = (appearance.translationSize * capacity.captionScale * 1.4f).sp,
            textAlign = TextAlign.Center, lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto)
        val captionHeight = remember(frame.line?.translation, frame.previous?.line?.translation,
            captionStyle, capacity, captionMeasurer) {
            if (capacity.captionLines == 0) 0f else listOf(frame.line?.translation, frame.previous?.line?.translation)
                .filterNotNull().filter { it.isNotBlank() }.maxOfOrNull { captionMeasurer.measure(AnnotatedString(it),
                    style = captionStyle, constraints = Constraints(maxWidth = capacity.captionWidth.toInt().coerceAtLeast(1),
                        maxHeight = (capacity.captionReserve - capacity.captionGap).toInt().coerceAtLeast(1)),
                    maxLines = capacity.captionLines, overflow = TextOverflow.Ellipsis).size.height.toFloat() } ?: 0f
        }
        val composition = remember(width, height, density, appearance, style, bilingual, captionHeight) {
            popStageLayout(width, height, density.density, density.fontScale, appearance, style, bilingual, captionHeight)
        }
        val builder = rememberPopSceneBuilder(cache, appearance, style, foreground, activeColor,
            composition.bodyWidth, composition.bodyHeight)
        val scenes = remember(builder, frame.timeline, frame.timing) {
            frame.timeline?.let { builder.build(it, requireNotNull(frame.timing)) }
        }
        LaunchedEffect(builder, frame.upcoming) {
            frame.upcoming?.let {
                withFrameNanos { }
                builder.build(it.timeline, it.timing)
            }
        }
        val duration = if (allowMotion && frame.previous != null) frame.timing?.transitionMs ?: 0 else 0
        val retiring by remember(frame.timeline, frame.previous, position, duration, frame.interlude) {
            derivedStateOf {
                frame.interlude == null && frame.timeline != null && frame.previous != null && duration > 0 &&
                    position.value - frame.timeline.startMs in 0L until duration.toLong() &&
                    frame.timeline.startMs > frame.previous.timeline.startMs &&
                    frame.timeline.startMs - frame.previous.timeline.endMs <= 2000L
            }
        }
        val previousScenes = remember(builder, frame.previous, retiring) {
            if (retiring) frame.previous?.let { builder.build(it.timeline, it.timing) } else null
        }
        val plan = remember(builder, scenes, previousScenes, frame.primary, style) {
            val seed = PopVisualMotion.seed(frame.line?.startMs ?: 0L, frame.primary)
            val flow = PopStageMotion.flow(previousScenes?.cameraPath, scenes?.cameraPath,
                frame.line?.startMs ?: 0L, PopMotionOrchestration.cameraStyle(style), builder.width, builder.height)
            PopMotionOrchestration.transition(style, seed, flow)
        }
        Box(Modifier.fillMaxSize().padding(horizontal = with(density) { composition.insetX.toDp() },
            vertical = with(density) { composition.insetY.toDp() })) {
            if (frame.interlude != null || scenes == null) {
                Column(Modifier.fillMaxWidth(.65f).align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (frame.index < 0) context.getString(R.string.intro) else context.getString(R.string.instrumental), color = foreground.copy(alpha = .7f))
                    frame.interlude?.let { gap ->
                        LinearProgressIndicator(progress = { gap.progress(position.value) },
                            modifier = Modifier.fillMaxWidth().testTag("home_pop_interlude"), color = activeColor)
                    }
                }
            } else {
                val start = requireNotNull(frame.timeline).startMs
                val sentenceDuration = if (retiring) duration else 0
                val gesture = if (interactive) Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { if (onLyricTap != null) onLyricTap() else state.seek(start - state.displayOffsetMs) },
                    onLongClickLabel = context.getString(R.string.select_and_share), onLongClick = { state.beginSharing(frame.index) }) else Modifier
                Box(Modifier.fillMaxSize().then(gesture)) {
                    if (previousScenes != null) PopSentenceContent(previousScenes, builder, position,
                        appearance, style, allowMotion, foreground, activeColor, composition,
                        if (bilingual) frame.previous?.line?.translation.orEmpty() else "",
                        plan, start, sentenceDuration, false)
                    PopSentenceContent(scenes, builder, position, appearance, style, allowMotion, foreground, activeColor,
                        composition, if (bilingual) frame.line?.translation.orEmpty() else "", plan, start,
                        sentenceDuration, true, frame.cameraPath, frame.phraseMotionUntil, state.playing)
                }
            }
        }
    }
}

@Composable
private fun PopSentenceContent(scenes: PopScenes, builder: PopSceneBuilder, position: State<Long>,
    appearance: HomeLyricAppearance, style: PopLyricsStyle, allowMotion: Boolean, foreground: Color,
    activeColor: Color, composition: PopStageLayout, translation: String, plan: PopTransitionPlan,
    startMs: Long, durationMs: Int, entering: Boolean, cameraPath: MutableState<PopCameraPath?>? = null,
    phraseMotionUntil: MutableState<Long?>? = null, connectCamera: Boolean = false) {
    val density = LocalDensity.current
    val classic = style.sizingMode == "classic"
    val translationStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = (appearance.translationSize * composition.captionScale).sp,
        lineHeight = (appearance.translationSize * composition.captionScale * 1.4f).sp, color = foreground.copy(alpha = .75f),
        textAlign = TextAlign.Center, lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto)
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val caption = remember(translation, translationStyle, composition, measurer) {
        if (translation.isBlank() || composition.captionLines == 0) null else measurer.measure(AnnotatedString(translation),
            style = translationStyle, constraints = Constraints(maxWidth = composition.captionWidth.toInt().coerceAtLeast(1),
                maxHeight = (composition.captionReserve - composition.captionGap).toInt().coerceAtLeast(1)),
            maxLines = composition.captionLines, overflow = TextOverflow.Ellipsis)
    }
    val joinedCamera = remember(scenes, cameraPath) {
        if (connectCamera) PopStageMotion.connect(scenes.cameraPath, cameraPath?.value, position.value) else scenes.cameraPath
    }
    val buffer = remember(scenes, style, allowMotion, density, joinedCamera, plan, startMs, durationMs, entering) {
        if (classic) null else PopFrameBuffer(scenes, style, allowMotion, builder.width, builder.height, density.density,
            joinedCamera, plan, startMs, durationMs, entering)
    }
    val window by remember(scenes, position, allowMotion) { derivedStateOf {
        popPhraseWindow(scenes.ranges, scenes.timeline, scenes.handoffs, position.value, allowMotion)
    } }
    SideEffect {
        if (cameraPath != null) cameraPath.value = joinedCamera
        if (phraseMotionUntil != null) phraseMotionUntil.value = if (window.retiring == null) null else
            scenes.handoffs[window.current].let { it.startMs + it.durationMs }
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        val fraction = if (durationMs > 0) ((position.value - startMs).toFloat() / durationMs).coerceIn(0f, 1f) else 1f
        val pose = PopMotionOrchestration.pose(plan, fraction, entering, builder.width, builder.height)
        translationX = if (classic) pose.x else 0f
        translationY = if (classic) pose.y else 0f
        scaleX = if (classic) pose.scale else 1f
        scaleY = if (classic) pose.scale else 1f
        transformOrigin = if (classic)
            TransformOrigin(.5f, composition.bodyHeight * composition.anchor / size.height.coerceAtLeast(1f))
            else TransformOrigin.Center
        alpha = pose.alpha
    }.then(if (entering) Modifier else Modifier.clearAndSetSemantics {})) {
        if (classic) ClassicPopLyricCanvas(scenes, builder, position, style, allowMotion, foreground, activeColor,
            Modifier.fillMaxWidth().height(with(density) { composition.bodyHeight.toDp() }),
            startMs, if (entering) durationMs else 0, entering)
        else PopLyricCanvas(scenes, position, allowMotion, foreground, activeColor,
            Modifier.fillMaxWidth().height(with(density) { composition.bodyHeight.toDp() })
                .offset(y = with(density) { composition.bodyTop.toDp() }), requireNotNull(buffer), entering)
        if (caption != null) {
            Text(translation, style = translationStyle, maxLines = composition.captionLines, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(with(density) { composition.captionWidth.toDp() })
                    .height(with(density) { caption.size.height.toDp() }).align(Alignment.TopCenter)
                    .offset {
                        val prepared = buffer?.prepare(position.value)
                        val window = prepared?.window ?: popPhraseWindow(scenes.ranges, scenes.timeline,
                            scenes.handoffs, position.value, allowMotion)
                        val handoff = scenes.handoffs[window.current]
                        val progress = if (window.retiring != null && handoff.durationMs > 0L)
                            (position.value - handoff.startMs).toFloat() / handoff.durationMs else 1f
                        fun bottom(index: Int) = if (classic) scenes.pages[index].bottom
                            else requireNotNull(prepared).bottoms[index] + composition.bodyTop
                        val maxTop = if (classic) composition.bodyHeight + composition.captionReserve - caption.size.height
                            else composition.bodyHeight + composition.bodyTop * 2f - caption.size.height
                        IntOffset(0, popCaptionTop(bottom(window.current), window.retiring?.let { bottom(it) },
                            progress, composition.captionGap, maxTop).roundToInt())
                    }.testTag(if (entering) "home_pop_translation" else "home_pop_retiring_translation"))
        }
    }
}

@Composable
private fun PopLyricCanvas(scenes: PopScenes, position: State<Long>,
    allowMotion: Boolean, foreground: Color, activeColor: Color, modifier: Modifier,
    buffer: PopFrameBuffer, enteringSentence: Boolean) {
    val window by remember(scenes, position, allowMotion) { derivedStateOf {
        popPhraseWindow(scenes.ranges, scenes.timeline, scenes.handoffs, position.value, allowMotion)
    } }
    val current = scenes.pages[window.current]
    Canvas(modifier.testTag(if (enteringSentence) "home_pop_canvas" else "home_pop_retiring_canvas").semantics {
        text = AnnotatedString(current.timeline.tokens.joinToString("") { it.text })
    }) {
        val now = position.value
        val prepared = buffer.prepare(now)
        fun drawPhrase(pageIndex: Int) {
            val alpha = prepared.alphas[pageIndex]
            if (alpha <= 0f) return
            val page = scenes.pages[pageIndex]
            val animated = page.timeline.wordTimed && allowMotion
            val centerX = prepared.centersX[pageIndex]
            val centerY = prepared.centersY[pageIndex]
            val fit = prepared.fits[pageIndex]
            for (index in page.glyphs.indices) {
                val frame = prepared.glyphs[pageIndex][index]
                if (frame[4] <= 0f) continue
                val glyph = page.glyphs[index]
                val token = page.timeline.tokens[index]
                withTransform({
                    translate(size.width / 2f + (frame[0] - centerX) * fit, size.height / 2f + (frame[1] - centerY) * fit)
                    scale(frame[2] * fit, frame[2] * fit, pivot = Offset.Zero)
                    rotate(frame[3], pivot = Offset.Zero)
                    translate(-glyph.ink.center.x, -glyph.ink.center.y)
                }) {
                    drawText(glyph.layout, color = if (animated) lerp(activeColor, foreground, frame[5]) else foreground,
                        alpha = frame[4] * alpha, shadow = if (animated && now >= token.startMs && now < token.endMs)
                            glyph.glow else Shadow.None)
                }
            }
        }
        prepared.window.retiring?.let(::drawPhrase)
        drawPhrase(prepared.window.current)
    }
}
