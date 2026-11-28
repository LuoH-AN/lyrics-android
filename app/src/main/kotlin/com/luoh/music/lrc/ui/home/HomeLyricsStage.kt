package com.luoh.music.lrc.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.R
import com.luoh.music.lrc.lyrics.LyricInterlude
import com.luoh.music.lrc.lyrics.LyricLine
import com.luoh.music.lrc.lyrics.LyricTiming
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.home.pop.PopLyricsStage
import com.luoh.music.lrc.ui.home.pop.PopLyricsStyle
import com.luoh.music.lrc.ui.home.pop.PopPlaybackFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.min

@Composable
internal fun HomeLyricsStage(state: HomePresentationState, modifier: Modifier = Modifier, albumBackground: Boolean = false,
    appearance: HomeLyricAppearance = HomeLyricAppearance(), displayMode: String = HomeLyricsPrefs.SCROLL,
    popStyle: PopLyricsStyle = PopLyricsStyle(), popPlayback: PopPlaybackFrame? = null, onLyricTap: (() -> Unit)? = null) {
    val pop = displayMode == HomeLyricsPrefs.POP && state.document.timed && state.document.lines.isNotEmpty()
    if (pop) {
        PopLyricsStage(state, modifier, albumBackground, appearance, popStyle, requireNotNull(popPlayback), onLyricTap)
    } else {
        HomeScrollingLyricsStage(state, modifier.homeLyricsFade(), albumBackground, appearance, onLyricTap)
    }
}

@Composable
private fun HomeScrollingLyricsStage(state: HomePresentationState, modifier: Modifier = Modifier, albumBackground: Boolean = false,
    appearance: HomeLyricAppearance = HomeLyricAppearance(), onLyricTap: (() -> Unit)? = null) {
    val context = LocalContext.current
    val document = state.document
    val foreground = if (albumBackground) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = if (albumBackground) Color.White.copy(alpha = .72f) else MaterialTheme.colorScheme.onSurfaceVariant
    if (document.lines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.snapshot.track.isNotBlank() && state.lyricStatus == "loading") {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp,
                        color = if (albumBackground) foreground else MaterialTheme.colorScheme.primary)
                }
                Text(when {
                    state.snapshot.track.isBlank() -> context.getString(R.string.nothing_playing)
                    state.lyricStatus == "loading" -> context.getString(R.string.finding_lyrics)
                    else -> context.getString(R.string.no_lyrics_found)
                }, style = MaterialTheme.typography.titleLarge, color = secondary)
                if (state.lyricStatus == "empty") {
                    Row {
                        IconButton(onClick = { state.actions?.retryLyrics() }) { Icon(Icons.Default.Refresh, context.getString(R.string.find_lyrics_again), tint = foreground) }
                        IconButton(onClick = { state.actions?.editCustomLyrics() }) { Icon(Icons.Default.Edit, context.getString(R.string.add_custom_lyrics), tint = foreground) }
                    }
                }
            }
        }
        return
    }
    val listState = rememberLazyListState()
    val timeline = remember(document) { document.lines.map { it.startMs } }
    val position = remember(timeline) { mutableLongStateOf(state.clock.positionMs() + state.displayOffsetMs) }
    var focused by remember(timeline) { mutableIntStateOf(homeActiveLine(document, state.clock.positionMs() + state.displayOffsetMs, state.clock.durationMs)) }
    var interlude by remember(timeline) { mutableStateOf(LyricInterlude.at(document, position.longValue, state.clock.durationMs)) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var manualUntil by remember(timeline) { mutableLongStateOf(0L) }
    val scrollVelocity = remember { mutableFloatStateOf(0f) }
    val motion = state.lyricMotionStyle
    val policy = LocalAgoraMotionPolicy.current

    LaunchedEffect(document, state.presentationActive, state.playing, state.playbackRevision, state.displayOffsetMs) {
        fun update() {
            val now = state.clock.positionMs() + state.displayOffsetMs
            position.longValue = now
            val next = homeActiveLine(document, now, state.clock.durationMs)
            if (next != focused) focused = next
            interlude = LyricInterlude.at(document, now, state.clock.durationMs)
        }
        update()
        // Only glyph drawing and the progress indicator read position each frame.
        while (state.presentationActive && state.playing) withFrameNanos { update() }
    }
    LaunchedEffect(dragged, timeline) {
        if (dragged) manualUntil = Long.MAX_VALUE
        else if (manualUntil != 0L) {
            manualUntil = System.nanoTime() / 1_000_000L + 4000L
            delay(4000)
            manualUntil = 0L
        }
    }
    LaunchedEffect(state.seekRevision, state.presentationActive) {
        manualUntil = 0L
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val centerPx = with(density) { (maxHeight * .45f).toPx() }
        val interludeHeightPx = with(density) { HomeInterludeRowHeight.toPx() }
        LaunchedEffect(timeline, centerPx, state.presentationActive, dragged, manualUntil, state.seekRevision,
            motion, policy.allowProgrammaticScrollMotion, appearance) {
            if (!state.presentationActive || dragged || manualUntil != 0L) return@LaunchedEffect
            val follower = HomeLyricScrollFollower()
            try {
                while (true) {
                    val target = focused.coerceIn(0, document.lines.lastIndex)
                    if (listState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
                        listState.scrollToItem(target, -centerPx.toInt())
                        follower.reset()
                    }
                    var restingTarget: Pair<Int, LyricInterlude?>? = null
                    listState.scroll {
                        var previousFrame = 0L
                        while (true) {
                            val frame = withFrameNanos { it }
                            val elapsed = if (previousFrame == 0L) 1f / 60f else (frame - previousFrame) / 1_000_000_000f
                            previousFrame = frame
                            val current = focused.coerceIn(0, document.lines.lastIndex)
                            val layout = listState.layoutInfo
                            val item = layout.visibleItemsInfo.firstOrNull { it.index == current } ?: break
                            val gap = interlude
                            val anchor = when {
                                gap == null -> item.size / 2f
                                gap.afterLine < 0 -> interludeHeightPx / 2f
                                else -> item.size - interludeHeightPx / 2f
                            }
                            val error = item.offset + anchor - layout.viewportStartOffset - centerPx
                            if (!motion.easedScroll || !policy.allowProgrammaticScrollMotion) {
                                scrollBy(error)
                                follower.reset()
                                scrollVelocity.floatValue = 0f
                                restingTarget = current to gap
                                break
                            }
                            if (follower.settled(error)) {
                                scrollVelocity.floatValue = follower.velocity
                                restingTarget = current to gap
                                break
                            }
                            val interval = timeline.getOrNull(current + 1)?.minus(timeline[current])
                                ?.takeIf { it > 0L } ?: motion.scrollDurationMs.toLong()
                            val move = follower.advance(error, elapsed, motion.scrollDurationMs, interval)
                            val consumed = scrollBy(move)
                            if (abs(consumed - move) > .5f) follower.reset()
                            scrollVelocity.floatValue = follower.velocity
                        }
                    }
                    restingTarget?.let { last -> snapshotFlow { focused.coerceAtLeast(0) to interlude }.first { it != last } }
                }
            } finally {
                // Only leaving the page, seeking or manual scrolling resets the follower.
                scrollVelocity.floatValue = 0f
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = maxHeight * .45f),
            verticalArrangement = Arrangement.spacedBy(appearance.lineSpacing.dp),
        ) {
            itemsIndexed(document.lines, key = { index, line -> "$index:${line.startMs}" }) { index, line ->
                // The gap is measured with the lyrics, never painted on top of another row.
                Column(Modifier.fillMaxWidth()) {
                    val gap = interlude
                    val progressColor = if (albumBackground) foreground else MaterialTheme.colorScheme.primary
                    if (index == 0 && gap?.afterLine == -1) HomeInterludeProgress(gap, position, progressColor)
                    HomeLyricRow(
                        line = line,
                        index = index,
                        focused = focused,
                        translationMode = state.translationDisplayMode,
                        alignment = state.textAlignment,
                        motion = motion,
                        allowMotion = state.presentationActive && policy.allowSpatialTransitions,
                        position = position,
                        scrollVelocity = scrollVelocity,
                        timed = document.timed,
                        nextLineMs = timeline.getOrNull(index + 1),
                        onClick = { if (onLyricTap != null) onLyricTap() else state.seek(line.startMs - state.displayOffsetMs) },
                        onLongClick = { state.beginSharing(index) },
                        activeColor = if (albumBackground) foreground else MaterialTheme.colorScheme.primary,
                        foregroundColor = foreground,
                        appearance = appearance,
                    )
                    if (gap?.afterLine == index) HomeInterludeProgress(gap, position, progressColor)
                }
            }
        }
    }
}

private val HomeInterludeRowHeight = 88.dp

@Composable
private fun HomeInterludeProgress(interlude: LyricInterlude, position: State<Long>, color: Color) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().height(HomeInterludeRowHeight).testTag("home_interlude_row"),
        contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { interlude.progress(position.value) },
            modifier = Modifier.size(32.dp).testTag("home_interlude_progress")
                .semantics { contentDescription = context.getString(R.string.instrumental_progress) },
            color = color,
            trackColor = color.copy(alpha = .16f),
            strokeWidth = 3.dp,
        )
    }
}

@Composable
private fun HomeLyricRow(
    line: LyricLine,
    index: Int,
    focused: Int,
    translationMode: String,
    alignment: String,
    motion: MotionStyle,
    allowMotion: Boolean,
    position: State<Long>,
    scrollVelocity: State<Float>,
    timed: Boolean,
    nextLineMs: Long?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    activeColor: Color,
    foregroundColor: Color,
    appearance: HomeLyricAppearance,
) {
    val context = LocalContext.current
    val distance = abs(index - focused)
    val duration = if (allowMotion && motion.easedScroll) motion.scrollDurationMs else 0
    val delayMs = if (allowMotion && motion.stagger) min(distance, 4) * motion.staggerDelayMs else 0
    val resting = when (distance) { 0 -> 1f; 1 -> .95f; 2 -> .92f; else -> .9f }
    val scale by animateFloatAsState(if (motion.scale) 1f - (1f - resting) * motion.scalePercent / 100f else 1f,
        tween(duration, delayMillis = delayMs, easing = FastOutSlowInEasing), label = "lyricScale$index")
    val alpha by animateFloatAsState(when (distance) { 0 -> 1f; 1 -> .78f; 2 -> .62f; 3 -> .48f; else -> .36f },
        tween(if (allowMotion) 300 else 0, delayMillis = delayMs), label = "lyricAlpha$index")
    val depth by animateFloatAsState(if (motion.depthBlur) (distance - 1).coerceIn(0, 3) * motion.blurPercent / 100f else 0f,
        tween(duration, delayMillis = delayMs), label = "lyricBlur$index")
    val lagSeconds by animateFloatAsState(
        if (motion.stagger && allowMotion) min(distance, 4) * motion.staggerDelayMs / 1000f else 0f,
        tween(if (allowMotion) 160 else 0), label = "lyricLag$index",
    )
    val textAlignment = when (alignment) { HomeLyricsPrefs.CENTER -> TextAlign.Center; HomeLyricsPrefs.RIGHT -> TextAlign.End; else -> TextAlign.Start }
    val origin = TransformOrigin(when (alignment) { HomeLyricsPrefs.CENTER -> .5f; HomeLyricsPrefs.RIGHT -> 1f; else -> 0f }, .5f)
    val color = if (index == focused) activeColor else foregroundColor
    Column(
        Modifier.fillMaxWidth().graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
            transformOrigin = origin
            translationY = if (motion.stagger && allowMotion) (scrollVelocity.value * lagSeconds).coerceIn(-48.dp.toPx(), 48.dp.toPx()) else 0f
        }.blur(depth.dp).combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { if (timed) onClick() },
            onLongClickLabel = context.getString(R.string.select_and_share),
            onLongClick = onLongClick,
        ).padding(vertical = 10.dp),
    ) {
        val primary = if (translationMode == LyricsOverlayService.TRANSLATION_TRANSLATED && line.translation.isNotBlank()) line.translation else line.text
        val characterMotion = motion.characterMotion
        val wordTiming = translationMode != LyricsOverlayService.TRANSLATION_TRANSLATED && line.words.isNotEmpty()
        val floatCharacters = allowMotion && timed && wordTiming && index == focused && characterMotion.visible
        KaraokeLyricText(primary, line, position, index < focused, index == focused,
            wordTiming = wordTiming, color = if (floatCharacters) foregroundColor else color,
            textAlign = textAlignment, appearance = appearance,
            characterMotion = characterMotion.takeIf { floatCharacters }, nextLineMs = nextLineMs)
        if (translationMode == LyricsOverlayService.TRANSLATION_BILINGUAL && line.translation.isNotBlank()) {
            Text(line.translation, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = color,
                fontSize = appearance.translationSize.sp, lineHeight = (appearance.translationSize * 1.4f).sp,
                textAlign = textAlignment, fontWeight = FontWeight(appearance.fontWeight))
        }
    }
}
