package com.luoh.music.lrc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import kotlin.math.cos
import kotlin.math.sin

private data class StackGlyph(val layout: TextLayoutResult, val slot: PopLyricSlot,
    val angle: Float, val sourceIndex: Int, val rowStart: Int, val entranceOffset: Offset)
private data class StackBlock(val lineIndex: Int, val timeline: PopLyricTimeline,
    val timing: PopLyricRenderTiming, val glyphs: List<StackGlyph>, val scale: Float,
    val height: Float, val translation: TextLayoutResult?, val translationTop: Float)

/** Retain measured phrases across source lines. The viewport clips them only after
 * new phrases push them out; no lyric-end timer removes completed text. */
@Composable
internal fun PopStackLyricsStage(state: HomePresentationState, modifier: Modifier, albumBackground: Boolean,
    appearance: HomeLyricAppearance, style: PopLyricsStyle, frame: PopPlaybackFrame,
    onLyricTap: (() -> Unit)? = null, interactive: Boolean = true) {
    val density = LocalDensity.current
    val allowMotion = LocalAgoraMotionPolicy.current.allowSpatialTransitions
    val foreground = if (albumBackground) Color.White else MaterialTheme.colorScheme.onSurface
    val activeColor = if (albumBackground) Color.White else MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val fontSize = (appearance.fontSize + 10).coerceIn(24, 64)
    val textStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.15f).sp, fontWeight = FontWeight((appearance.fontWeight + 200).coerceAtMost(900)),
        color = foreground, textAlign = TextAlign.Center, lineBreak = LineBreak.Heading, hyphens = Hyphens.Auto)
    val translationStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = appearance.translationSize.sp,
        lineHeight = (appearance.translationSize * 1.4f).sp, color = foreground.copy(alpha = .75f), textAlign = TextAlign.Center)
    val document = state.document
    BoxWithConstraints(modifier.testTag("home_pop_stage").padding(horizontal = 24.dp, vertical = 12.dp)) {
        val width = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        // Keep the gap indicator's space stable, including when an interlude starts.
        val height = with(density) { (maxHeight - 36.dp).toPx() }.coerceAtLeast(1f)
        val blockGap = with(density) { style.stackGapDp.dp.toPx() }
        val wordGap = with(density) { (appearance.lineSpacing / 3f + 4f).dp.toPx() }
        val translationGap = with(density) { 12.dp.toPx() }
        val rowTravel = with(density) { (20f * style.rowMotionPercent / 100f).dp.toPx() }
        val cache = remember(document, style, textStyle, translationStyle, width, height, density,
            state.translationDisplayMode) { PopLyricCache<Int, List<StackBlock>>(32) }

        fun lineBlocks(lineIndex: Int): List<StackBlock> = cache.getOrPut(lineIndex) {
            val line = document.lines[lineIndex]
            val primary = if (state.translationDisplayMode == LyricsOverlayService.TRANSLATION_TRANSLATED &&
                line.translation.isNotBlank()) line.translation else line.text
            if (primary.isBlank()) return@getOrPut emptyList()
            val source = PopLyricTimeline.prepare(line, primary, style.reveal)
            val profile = popLyricRenderTiming(source, style, document.lines.getOrNull(lineIndex + 1)?.startMs)
            val expanded = popLayoutTokens(source)
            val timeline = PopLyricTimeline(expanded.tokens, source.wordTimed, source.startMs, source.endMs)
            val timing = PopLyricRenderTiming(profile.transitionMs,
                expanded.sourceIndices.map { profile.entranceMs[it] }.toLongArray(),
                expanded.sourceIndices.map { profile.lookaheadMs[it] }.toLongArray(),
                expanded.sourceIndices.map { profile.passStartMs[it] }.toLongArray(),
                expanded.sourceIndices.map { profile.passMs[it] }.toLongArray(),
                expanded.sourceIndices.map { profile.sustain.getOrNull(it) })
            val groups = groupPopLyrics(timeline.tokens, style.semanticGrouping)
            val layouts = timeline.tokens.map { token -> measurer.measure(AnnotatedString(popLyricPreview(token.text)),
                style = textStyle, constraints = Constraints(maxWidth = (width * .92f).toInt().coerceAtLeast(1)),
                maxLines = 8, overflow = TextOverflow.Ellipsis) }
            val angles = FloatArray(layouts.size) { index ->
                if (timeline.wordTimed) (Math.floorMod(timeline.tokens[index].text.hashCode() + index * 17, 7) - 3) *
                    style.rotationDegrees / 20f else 0f
            }
            val reservedScale = if (timeline.wordTimed) style.maxWordScale else 1f
            val extents = layouts.mapIndexed { index, layout ->
                val shake = timing.sustain[index]
                val maxAngle = if (style.dynamicSpacing) kotlin.math.abs(angles[index]) +
                    (if (timeline.wordTimed) style.rotationDegrees else 0) + (shake?.reachDegrees ?: 0f) else 0f
                val extent = popGlyphExtent(layout.size.width.toFloat(), layout.size.height.toFloat(), reservedScale, maxAngle)
                PopGlyphExtent(extent.width + (shake?.reachXDp ?: 0f) * density.density * 2f * reservedScale,
                    extent.height + (shake?.reachYDp ?: 0f) * density.density * 2f * reservedScale)
            }
            val gaps = FloatArray(layouts.size + 1) { index ->
                (if (groups.tightBoundaries[index]) style.innerGapDp else style.groupGapDp) * density.density
            }
            val typicalHeight = extents.map { it.height + rowTravel * 2f }.sorted()[extents.size / 2]
            val rowLimit = popLyricRowLimit(width * .92f, typicalHeight, style.layout, style.lineWidthPercent)
            val ranges = if (timeline.wordTimed) paginatePopLyrics(timeline.tokens, groups.penalties,
                FloatArray(extents.size) { extents[it].width + gaps[it] },
                rowLimit * style.stackRows, style.pageLength) else listOf(PopLyricPage(0, timeline.tokens.size))
            val translation = if (style.stackTranslations && state.translationDisplayMode == LyricsOverlayService.TRANSLATION_BILINGUAL &&
                line.translation.isNotBlank()) measurer.measure(AnnotatedString(popLyricPreview(line.translation)),
                    style = translationStyle, constraints = Constraints(maxWidth = width.toInt().coerceAtLeast(1)),
                    maxLines = 3, overflow = TextOverflow.Ellipsis) else null
            ranges.mapIndexed { pageIndex, range ->
                val pageLayouts = layouts.subList(range.from, range.until)
                val pageExtents = extents.subList(range.from, range.until)
                val packing = packPopLyrics(FloatArray(pageLayouts.size) { pageLayouts[it].size.width.toFloat() },
                    FloatArray(pageLayouts.size) { pageLayouts[it].size.height.toFloat() }, width * .92f, height * .85f,
                    wordGap, style.layout, reservedScale, style.rotationDegrees,
                    groups.penalties.copyOfRange(range.from, range.until + 1), pageExtents,
                    gaps.copyOfRange(range.from, range.until + 1), wordGap * style.rowGapPercent / 100f,
                    style.staggerPercent, style.lineWidthPercent, rowTravel, maxFitScale = 1f)
                val top = packing.slots.indices.minOf { packing.slots[it].y -
                    (pageExtents[it].height / 2f + rowTravel) * packing.scale }
                val bottom = packing.slots.indices.maxOf { packing.slots[it].y +
                    (pageExtents[it].height / 2f + rowTravel) * packing.scale }
                val caption = translation.takeIf { pageIndex == ranges.lastIndex }
                val captionTop = bottom - top + translationGap
                val glyphs = pageLayouts.mapIndexed { index, layout ->
                    val rowStart = packing.rowStarts.last { it <= index }
                    val seed = tokensSeed(timeline.tokens[range.from + index], range.from + index)
                    val entranceX = if (style.entrance == "scatter") sin(seed) * 72f * density.density * style.travelPercent / 100f else 0f
                    val entranceY = if (style.entrance == "zoom") 0f else
                        (if (style.entrance == "scatter") cos(seed) else 1f) * 48f * density.density * style.travelPercent / 100f
                    StackGlyph(layout, PopLyricSlot(packing.slots[index].x + width * .04f, packing.slots[index].y - top),
                        angles[range.from + index], range.from + index, range.from + rowStart, Offset(entranceX, entranceY))
                }
                val tokens = timeline.tokens.subList(range.from, range.until)
                StackBlock(lineIndex, PopLyricTimeline(tokens, timeline.wordTimed, tokens.first().startMs,
                    tokens.maxOf { it.endMs }), timing, glyphs, packing.scale,
                    (if (caption == null) bottom - top else captionTop + caption.size.height).coerceAtLeast(1f),
                    caption, captionTop)
            }
        }

        val currentBlocks = remember(frame.index, cache) { if (frame.index < 0) emptyList() else lineBlocks(frame.index) }
        val phraseIndex by remember(currentBlocks, frame.position) { derivedStateOf {
            currentBlocks.indexOfLast { it.timeline.startMs <= frame.position.value }
        } }
        val blocks = remember(currentBlocks, phraseIndex, cache, height) {
            val history = currentBlocks.toMutableList()
            var extent = currentBlocks.take(phraseIndex + 1).sumOf { it.height.toDouble() + blockGap }.toFloat()
            val cutoff = currentBlocks.getOrNull(phraseIndex)?.timeline?.startMs
                ?: document.lines.getOrNull(frame.index)?.startMs ?: Long.MIN_VALUE
            var previous = frame.index - 1
            // The retained strip is bounded by measured screen space, not a lyric count or timeout.
            while (previous >= 0 && extent < height * 2f) {
                val prior = lineBlocks(previous--)
                history.addAll(0, prior)
                extent += prior.filter { it.timeline.startMs <= cutoff }.sumOf { it.height.toDouble() + blockGap }.toFloat()
            }
            // Knowing the next real onset lets a fast phrase finish its push before
            // the next one starts. Future blocks stay invisible in PopStackFlow.
            if (frame.index + 1 in document.lines.indices) lineBlocks(frame.index + 1).firstOrNull()?.let(history::add)
            history.sortedWith(compareBy<StackBlock> { it.timeline.startMs }.thenBy { it.lineIndex })
        }
        val flow = remember(blocks, height, blockGap, style.stackAnchorPercent, style.stackScrollMs, style.stackDirection) {
            PopStackFlow(blocks.map { PopStackMetric(it.timeline.startMs, it.height) }, height, blockGap,
                style.stackAnchorPercent, style.stackScrollMs, style.stackDirection == "down")
        }
        // Floats change in drawing; composition only follows new phrases or clipping boundaries.
        val visibleIndices by remember(flow, frame.position, allowMotion) { derivedStateOf {
            flow.at(frame.position.value, allowMotion).visible.map { it.index }
        } }
        SideEffect {
            frame.cameraPath.value = null
            val wordMotionEnd = blocks.filter { it.timeline.startMs <= frame.position.value }.maxOfOrNull { block -> block.glyphs.indices.maxOf { index ->
                val glyph = block.glyphs[index]
                val token = block.timeline.tokens[index]
                maxOf(token.startMs - block.timing.lookaheadMs[glyph.sourceIndex] + token.visualDelayMs +
                    block.timing.entranceMs[glyph.sourceIndex],
                    block.timing.passStartMs[glyph.sourceIndex] + block.timing.passMs[glyph.sourceIndex])
            } }
            frame.phraseMotionUntil.value = listOfNotNull(wordMotionEnd,
                flow.at(frame.position.value, allowMotion).motionUntilMs).maxOrNull()
        }
        LaunchedEffect(frame.upcoming, cache) {
            if (frame.upcoming != null && frame.index + 1 in document.lines.indices) {
                withFrameNanos { }
                lineBlocks(frame.index + 1)
            }
        }
        fun lineAt(point: Offset): Int = flow.at(frame.position.value, allowMotion).visible.lastOrNull {
            point.y >= it.top && point.y < it.bottom
        }?.let { blocks[it.index].lineIndex } ?: frame.index
        val interaction = if (!interactive) Modifier else Modifier.pointerInput(flow, blocks, onLyricTap) {
            detectTapGestures(onTap = { point ->
                val index = lineAt(point)
                if (onLyricTap != null) onLyricTap() else document.lines.getOrNull(index)?.let {
                    state.seek(it.startMs - state.displayOffsetMs)
                }
            }, onLongPress = { point -> lineAt(point).takeIf { it >= 0 }?.let(state::beginSharing) })
        }
        val glow = remember(activeColor, density) { Shadow(activeColor.copy(alpha = .45f), Offset.Zero, 7f * density.density) }
        Canvas(Modifier.fillMaxSize().then(interaction).graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }.testTag("home_pop_stack_canvas").semantics {
            text = AnnotatedString(visibleIndices.joinToString("\n") { index ->
                val block = blocks[index]
                block.timeline.tokens.joinToString("") { it.text } +
                    (block.translation?.let { "\n" + it.layoutInput.text.text } ?: "")
            })
            if (interactive) {
                onClick { if (onLyricTap != null) onLyricTap() else frame.line?.let { state.seek(it.startMs - state.displayOffsetMs) }; true }
                onLongClick("摘句与分享") { if (frame.index >= 0) state.beginSharing(frame.index); true }
            }
        }) {
            val now = frame.position.value
            val window = flow.at(now, allowMotion)
            clipRect(bottom = height) {
                for (placement in window.visible) {
                    val block = blocks[placement.index]
                    val old = if (placement.index < window.current)
                        PopLyricMotion.pass(now - block.timeline.endMs) else 0f
                    val historyAlpha = 1f - old * (1f - style.stackHistoryOpacityPercent / 100f)
                    for ((index, glyph) in block.glyphs.withIndex()) {
                        val token = block.timeline.tokens[index]
                        val sourceIndex = glyph.sourceIndex
                        val elapsed = now - (token.startMs - block.timing.lookaheadMs[sourceIndex] + token.visualDelayMs)
                        if (elapsed < 0L) continue
                        val animated = block.timeline.wordTimed && allowMotion
                        val entered = if (animated) PopLyricMotion.enter(elapsed, block.timing.entranceMs[sourceIndex]) else 1f
                        val passed = PopLyricMotion.pass(now - block.timing.passStartMs[sourceIndex], block.timing.passMs[sourceIndex])
                        val alpha = (if (animated) (elapsed.toFloat() / minOf(65L, block.timing.entranceMs[sourceIndex]).coerceAtLeast(1L))
                            .coerceIn(0f, 1f) else 1f) * (1f - passed * (1f - style.passedOpacityPercent / 100f)) * historyAlpha
                        val entranceScale = .5f + entered * (style.activeScalePercent / 100f - .5f)
                        val scale = if (animated) (entranceScale + (1f - entranceScale) * passed).coerceIn(.5f, style.maxWordScale) else 1f
                        val shake = PopSustainMotion.sample(if (animated && state.playing) block.timing.sustain[sourceIndex] else null, now)
                        val rowToken = block.timeline.tokens[glyph.rowStart - block.glyphs.first().sourceIndex]
                        val rowElapsed = now - (rowToken.startMs - block.timing.lookaheadMs[glyph.rowStart])
                        val rowOffset = if (animated) rowTravel * block.scale *
                            (1f - PopLyricMotion.enter(rowElapsed, block.timing.entranceMs[glyph.rowStart].coerceAtMost(300L))) else 0f
                        withTransform({
                            translate(glyph.slot.x + glyph.entranceOffset.x * (1f - entered),
                                placement.top + glyph.slot.y + rowOffset + glyph.entranceOffset.y * (1f - entered))
                            scale(block.scale * scale, block.scale * scale, Offset.Zero)
                            translate(shake.xDp * density.density, shake.yDp * density.density)
                            if (animated) rotate(glyph.angle * (1f - passed * .65f) + style.rotationDegrees * (1f - entered) + shake.degrees, Offset.Zero)
                            translate(-glyph.layout.size.width / 2f, -glyph.layout.size.height / 2f)
                        }) {
                            drawText(glyph.layout, color = lerp(activeColor, foreground, passed), alpha = alpha,
                                shadow = if (animated && now >= token.startMs && now < token.endMs) glow else Shadow.None)
                        }
                    }
                    block.translation?.let { caption -> drawText(caption,
                        topLeft = Offset((width - caption.size.width) / 2f, placement.top + block.translationTop),
                        alpha = historyAlpha) }
                }
                val edge = (style.stackEdgeFadeDp * density.density / height).coerceIn(0f, .49f)
                if (edge > 0f) drawRect(Brush.verticalGradient(0f to Color.Transparent, edge to Color.Black,
                    (1f - edge) to Color.Black, 1f to Color.Transparent, endY = height), blendMode = BlendMode.DstIn)
            }
        }
        frame.interlude?.let { gap ->
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(.65f),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (frame.index < 0) "前奏" else "间奏", color = foreground.copy(alpha = .7f),
                    style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { gap.progress(frame.position.value) }, modifier = Modifier.fillMaxWidth(), color = activeColor)
            }
        }
    }
}

private fun tokensSeed(token: PopLyricToken, index: Int): Float = token.text.hashCode().toFloat() + index * 17f
