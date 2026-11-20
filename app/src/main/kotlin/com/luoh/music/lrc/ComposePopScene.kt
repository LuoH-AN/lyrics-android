package com.luoh.music.lrc

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

internal data class PopGlyph(val layout: TextLayoutResult, val slot: PopLyricSlot, val angle: Float,
    val entranceOffset: Offset, val baseScale: Float, val glow: Shadow, val dynamics: PopWordDynamics)
internal data class PopRow(val from: Int, val until: Int, val startMs: Long, val entranceMs: Long,
    val passStartMs: Long, val passMs: Long, val travel: Float, val travelX: Float, val center: Offset)
internal data class PopScene(val timeline: PopLyricTimeline, val timing: PopLyricRenderTiming,
    val glyphs: List<PopGlyph>, val rows: List<PopRow>, val scale: Float, val bounds: PopCameraBounds, val bottom: Float) {
    val renderedBottom = mutableFloatStateOf(bottom)
}
internal data class PopMeasureKey(val timeline: PopLyricTimeline, val textStyle: androidx.compose.ui.text.TextStyle,
    val width: Int, val semantic: Boolean, val density: Float, val fontScale: Float)
internal data class PopMeasured(val layouts: List<TextLayoutResult>, val groups: PopLyricGroups,
    val timeline: PopLyricTimeline, val sourceIndices: List<Int>)
internal data class PopScenes(val timeline: PopLyricTimeline, val ranges: List<PopLyricPage>, val pages: List<PopScene>,
    val handoffs: List<PopPhraseHandoff>, val cameraPath: PopCameraPath)
internal data class PopSceneKey(val measured: PopMeasured, val timeline: PopLyricTimeline, val timing: PopLyricRenderTiming,
    val width: Float, val height: Float, val gap: Float, val density: Float, val style: PopLyricsStyle, val accent: Color)
internal class PopRenderCache {
    val measured = PopLyricCache<PopMeasureKey, PopMeasured>()
    val scenes = PopLyricCache<PopSceneKey, PopScenes>()
}

@Composable
internal fun rememberPopSceneBuilder(cache: PopRenderCache, appearance: HomeLyricAppearance,
    style: PopLyricsStyle, foreground: Color, activeColor: Color, width: Float, height: Float): PopSceneBuilder {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val shapingWidth = with(density) {
        (minOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(160) * .85f *
            (style.lineWidthPercent / 100f).coerceAtMost(1f)).dp.roundToPx()
    }.coerceIn(1, width.toInt().coerceAtLeast(1))
    val textStyle = MaterialTheme.typography.headlineLarge.copy(
        fontSize = (appearance.fontSize + 10).coerceIn(24, 64).sp,
        lineHeight = ((appearance.fontSize + 10).coerceIn(24, 64) * 1.15f).sp,
        fontWeight = FontWeight((appearance.fontWeight + 200).coerceAtMost(900)), color = foreground,
        textAlign = TextAlign.Center, lineBreak = LineBreak.Heading, hyphens = Hyphens.Auto)
    val gap = with(density) { (appearance.lineSpacing / 3f + 4f).dp.toPx() }
    return remember(cache, measurer, density, textStyle, shapingWidth, width, height, gap, style, activeColor) {
        PopSceneBuilder(cache, measurer, density, textStyle, shapingWidth, width, height, gap, style, activeColor)
    }
}

internal class PopSceneBuilder(private val cache: PopRenderCache, private val measurer: TextMeasurer,
    private val density: Density, private val textStyle: TextStyle, private val shapingWidth: Int,
    val width: Float, val height: Float, private val gap: Float, private val style: PopLyricsStyle,
    private val activeColor: Color) {
    private fun measure(candidate: PopLyricTimeline) = cache.measured.getOrPut(PopMeasureKey(candidate, textStyle, shapingWidth,
        style.semanticGrouping, density.density, density.fontScale)) {
        val expanded = popLayoutTokens(candidate)
        val renderTimeline = PopLyricTimeline(expanded.tokens, candidate.wordTimed, candidate.startMs, candidate.endMs)
        PopMeasured(expanded.tokens.map { token ->
            measurer.measure(AnnotatedString(popLyricPreview(token.text)), style = textStyle,
                constraints = Constraints(maxWidth = shapingWidth), maxLines = 8, overflow = TextOverflow.Ellipsis)
        }, groupPopLyrics(expanded.tokens, style.semanticGrouping), renderTimeline, expanded.sourceIndices)
    }

    fun build(candidate: PopLyricTimeline, profile: PopLyricRenderTiming): PopScenes {
        val measured = measure(candidate)
        return cache.scenes.getOrPut(PopSceneKey(measured, candidate, profile, width, height, gap, density.density, style, activeColor)) {
            val renderTimeline = measured.timeline
            val sceneSeed = PopVisualMotion.seed(renderTimeline.startMs, renderTimeline.tokens.joinToString("") { it.text })
            val layoutMode = PopVisualMotion.layout(style.layout, sceneSeed)
            val entranceMode = PopVisualMotion.entrance(style.entrance, sceneSeed)
            val typicalGlyphHeight = measured.layouts.map { it.size.height.toFloat() }.sorted()
                .let { it.getOrElse(it.size / 2) { 1f } }
            val spatial = PopMotionOrchestration.spatial(style, typicalGlyphHeight, height)
            val sourceDynamics = PopMotionOrchestration.words(candidate, style)
                .map { PopMotionOrchestration.fitWord(it, spatial) }
            val dynamics = measured.sourceIndices.map { sourceDynamics[it] }
            val balance = PopMotionOrchestration.balance(style)
            val renderTiming = PopLyricRenderTiming(profile.transitionMs,
                measured.sourceIndices.map { profile.entranceMs[it] }.toLongArray(),
                measured.sourceIndices.map { profile.lookaheadMs[it] }.toLongArray(),
                measured.sourceIndices.map { profile.passStartMs[it] }.toLongArray(),
                measured.sourceIndices.map { profile.passMs[it] }.toLongArray(),
                measured.sourceIndices.mapIndexed { index, source -> profile.sustain.getOrNull(source)?.let {
                    it.copy(amount = it.amount * dynamics[index].sustain)
                } })
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
                val variation = if (layoutMode == "centered") .6f else 1f
                val hierarchy = (group.from until group.until).map { dynamics[it].hierarchy }.average().toFloat()
                val phraseScale = (1f + (-.06f + seed * .20f + sustain * .09f) * variation * spatial.hierarchy) * hierarchy
                for (index in group.from until group.until) emphasis[index] = phraseScale
            }
            val angles = FloatArray(layouts.size) { index ->
                val seed = Math.floorMod(renderTimeline.tokens[index].text.hashCode().toLong() + index * 17L, 997L).toFloat()
                if (renderTimeline.wordTimed) ((seed % 7f) - 3f) * style.rotationDegrees / 20f * dynamics[index].rotation else 0f
            }
            val extents = layouts.mapIndexed { index, layout ->
                val shake = renderTiming.sustain[index]
                val wordScale = if (renderTimeline.wordTimed) dynamics[index].maxScale else 1f
                val wordRotation = if (renderTimeline.wordTimed) style.rotationDegrees * dynamics[index].rotation else 0f
                val glyphScale = wordScale * emphasis[index] * 1.015f
                val maxAngle = maxOf(kotlin.math.abs(angles[index]) + wordRotation * .1f,
                    kotlin.math.abs(angles[index] + wordRotation)) + (shake?.reachDegrees ?: 0f)
                val extent = if (style.dynamicSpacing) popGlyphExtent(layout.size.width.toFloat(),
                    layout.size.height.toFloat(), glyphScale, maxAngle) else {
                    val reach = (sin((wordRotation + (shake?.reachDegrees ?: 0f)) * 1.15 * Math.PI / 180) * wordScale).toFloat()
                    PopGlyphExtent((layout.size.width * wordScale + layout.size.height * reach) * emphasis[index] * 1.015f,
                        (layout.size.height * wordScale + layout.size.width * reach) * emphasis[index] * 1.015f)
                }
                PopGlyphExtent(extent.width + (shake?.reachXDp ?: 0f) * density.density * 2f * glyphScale,
                    extent.height + (shake?.reachYDp ?: 0f) * density.density * 2f * glyphScale)
            }
            val gaps = FloatArray(layouts.size + 1) { index ->
                (if (measured.groups.tightBoundaries[index]) style.innerGapDp else style.groupGapDp) * density.density
            }
            val rowTravel = if (renderTimeline.wordTimed && profile.transitionMs > 0)
                20f * density.density * style.rowMotionPercent / 100f *
                    PopMotionOrchestration.strength(style) * balance.row * spatial.row else 0f
            val rowTravelX = rowTravel * 1.6f
            val typicalHeight = extents.map { it.height + rowTravel * 2f }.sorted()[extents.size / 2]
            val fill = style.stageFillPercent / 100f
            val packingWidth = (width * fill).coerceAtLeast(1f)
            val packingHeight = (height * fill).coerceAtLeast(1f)
            val rowLimit = (packingWidth * style.lineWidthPercent / 100f).coerceIn(1f, packingWidth)
            val availableRows = (packingHeight / (typicalHeight + gap * style.rowGapPercent / 100f)).coerceIn(1f, 2f)
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
                    FloatArray(tokens.size) { pageLayouts[it].size.height.toFloat() }, packingWidth, packingHeight, gap,
                    layoutMode, reservedScale, reservedRotation, measured.groups.penalties.copyOfRange(page.from, page.until + 1),
                    extents.subList(page.from, page.until), gaps.copyOfRange(page.from, page.until + 1),
                    gap * style.rowGapPercent / 100f, style.staggerPercent, style.lineWidthPercent, rowTravel,
                    maxFitScale = 16f, horizontalMotionReach = rowTravelX,
                    layoutSeed = sceneSeed, fillViewport = true)
                val travelX = 72f * density.density * style.travelPercent / 100f
                val travelY = 48f * density.density * style.travelPercent / 100f
                val glyphs = pageLayouts.mapIndexed { index, layout ->
                    val source = page.from + index
                    val packed = packing.slots[index]
                    val slot = PopLyricSlot(packed.x + (width - packingWidth) / 2f,
                        packed.y + (height - packingHeight) / 2f)
                    val seed = PopVisualMotion.seed(renderTimeline.startMs, tokens[index].text, source)
                    val halfWidth = (extents[source].width / 2f + rowTravelX) * packing.scale
                    val halfHeight = (extents[source].height / 2f + rowTravel) * packing.scale
                    val entry = PopVisualMotion.entranceOffset(entranceMode, seed, slot.x, slot.y,
                        width, height, travelX * dynamics[source].travel, travelY * dynamics[source].travel,
                        style.travelPercent / 100f * dynamics[source].travel)
                    val dx = entry.x.coerceIn(
                        (halfWidth - slot.x).coerceAtMost(0f), (width - halfWidth - slot.x).coerceAtLeast(0f))
                    val dy = entry.y.coerceIn(
                        (halfHeight - slot.y).coerceAtMost(0f), (height - halfHeight - slot.y).coerceAtLeast(0f))
                    PopGlyph(layout, slot, angles[source], Offset(dx, dy), emphasis[source],
                        Shadow(activeColor.copy(alpha = .35f), Offset.Zero, 6f * density.density), dynamics[source])
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
            // One C2 trajectory spans all phrases; switching text never resets the camera.
            val bounds = PopCameraBounds(pages.maxOf { it.bounds.minX }, pages.minOf { it.bounds.maxX },
                pages.maxOf { it.bounds.minY }, pages.minOf { it.bounds.maxY })
            val camera = PopStageMotion.path(renderTimeline, pages.flatMap { page -> page.glyphs.map {
                PopCameraPoint(it.slot.x / width.coerceAtLeast(1f) - .5f, it.slot.y / height.coerceAtLeast(1f) - .5f)
            } }, PopMotionOrchestration.cameraStyle(style), bounds, renderTiming)
            PopScenes(renderTimeline, ranges, pages, popPhraseHandoffs(ranges, renderTimeline, profile.transitionMs), camera)
        }
    }
}
