package com.luoh.music.lrc

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.agora.SmoothBottomSheet
import com.luoh.music.lrc.ui.agora.rememberSmoothBottomSheetState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** Playback state independent of Android Views, page hosts, and Activity animation rules. */
@Stable
internal class HomePresentationState {
    data class Snapshot(
        val track: String = "",
        val artist: String = "",
        val cover: Bitmap? = null,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val playing: Boolean = false,
        val speed: Float = 1f,
        val canPrevious: Boolean = false,
        val canNext: Boolean = false,
    )

    interface Actions {
        fun toggleOverlay()
        fun openSettings()
        fun seekTo(positionMs: Long)
        fun togglePlay()
        fun skipPrev()
        fun skipNext()
        fun retryLyrics()
        fun editCustomLyrics()
        fun manageCustomLyrics()
        fun manageLyricSources()
        fun exportLrc()
    }

    var actions: Actions? = null
    var onTranslationSettings: (() -> Unit)? = null
    var gateVisible by mutableStateOf(false)
    var gateStatus by mutableStateOf("")
    internal val clock = LyricClock()
    internal var snapshot by mutableStateOf(Snapshot())
        private set
    internal var document by mutableStateOf(LyricDocument(emptyList(), true))
        private set
    internal var custom by mutableStateOf(false)
        private set
    internal var lyricStatus by mutableStateOf("loading")
        private set
    internal var translationDisplayMode by mutableStateOf(LyricsOverlayService.TRANSLATION_BILINGUAL)
        private set
    internal var translationMessage by mutableStateOf("")
        private set
    internal var lyricOffset by mutableIntStateOf(0)
        private set
    internal var textAlignment by mutableStateOf(HomeLyricsPrefs.LEFT)
        private set
    internal var lyricMotionStyle by mutableStateOf(MotionStyle())
        private set
    internal var isActive by mutableStateOf(false)
        private set
    internal var playing by mutableStateOf(false)
        private set
    internal var isOverlayRunning by mutableStateOf(false)
        private set
    internal var playbackRevision by mutableIntStateOf(0)
        private set
    internal var seekRevision by mutableIntStateOf(0)
        private set

    fun setSnapshot(value: Snapshot, forcePosition: Boolean = false) {
        val trackChanged = value.track != snapshot.track || value.artist != snapshot.artist
        snapshot = value
        clock.durationMs = value.durationMs.coerceAtLeast(0L)
        clock.sync(value.positionMs, value.playing, value.speed, forcePosition || trackChanged)
        playing = value.playing
        if (value.track.isBlank()) {
            clock.reset()
            playing = false
            showLoading()
        } else if (forcePosition || trackChanged) showLoading()
        playbackRevision++
    }

    fun setProgress(positionMs: Long, playing: Boolean, speed: Float = 1f) {
        clock.sync(positionMs, playing, speed)
        this.playing = playing
        playbackRevision++
    }

    fun setLyrics(value: LyricDocument, isCustom: Boolean = false) {
        document = value
        custom = isCustom
        lyricStatus = if (value.lines.isEmpty()) "empty" else "ready"
    }

    fun showLoading() {
        document = LyricDocument(emptyList(), true)
        custom = false
        lyricStatus = "loading"
    }

    fun setTranslationMode(mode: String) {
        translationDisplayMode = when (mode) {
            LyricsOverlayService.TRANSLATION_ORIGINAL, LyricsOverlayService.TRANSLATION_TRANSLATED -> mode
            else -> LyricsOverlayService.TRANSLATION_BILINGUAL
        }
    }

    fun setTranslationStatus(message: String) { translationMessage = message }
    fun setAlignment(value: String) {
        textAlignment = when (value) {
            HomeLyricsPrefs.CENTER, HomeLyricsPrefs.RIGHT -> value
            else -> HomeLyricsPrefs.LEFT
        }
    }
    fun setLyricOffset(offsetMs: Int) { lyricOffset = offsetMs }
    fun setMotionStyle(style: MotionStyle) { lyricMotionStyle = style }
    fun setOverlayState(running: Boolean) { isOverlayRunning = running }
    fun setActive(on: Boolean) { isActive = on }

    internal fun seek(positionMs: Long) {
        if (snapshot.track.isBlank()) return
        val target = positionMs.coerceAtLeast(0L).let {
            if (snapshot.durationMs > 0L) it.coerceAtMost(snapshot.durationMs) else it
        }
        actions?.seekTo(target)
        clock.sync(target, playing, clock.speed, force = true)
        seekRevision++
        playbackRevision++
    }
}

@Composable
internal fun ComposeHomeScreen(
    state: HomePresentationState,
    onListenerPermission: () -> Unit,
    onOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (state.gateVisible) {
            HomePermissionGate(state.gateStatus, onListenerPermission, onOverlayPermission, onStartOverlay) { state.actions?.openSettings() }
        } else if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HomeLyricsStage(state, Modifier.weight(1f).fillMaxHeight())
                HomePlayer(state, Modifier.width(360.dp).align(Alignment.CenterVertically))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                HomeLyricsStage(state, Modifier.weight(1f).fillMaxWidth())
                HomePlayer(state, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun HomePermissionGate(
    status: String,
    onListenerPermission: () -> Unit,
    onOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Lyrics, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Text("桌面歌词", style = MaterialTheme.typography.headlineMedium)
                Text(status.ifBlank { "授予通知使用权和悬浮窗权限后，即可获取正在播放的歌曲并显示歌词。" },
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onListenerPermission, modifier = Modifier.fillMaxWidth()) { Text("通知使用权") }
                OutlinedButton(onClick = onOverlayPermission, modifier = Modifier.fillMaxWidth()) { Text("悬浮窗权限") }
                Button(onClick = onStartOverlay, modifier = Modifier.fillMaxWidth()) { Text("开启桌面歌词") }
                TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("设置") }
            }
        }
    }
}

@Composable
private fun HomePlayer(state: HomePresentationState, modifier: Modifier = Modifier) {
    val snapshot = state.snapshot
    var showMore by rememberSaveable { mutableStateOf(false) }
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.size(56.dp)) {
                    val cover = snapshot.cover
                    if (cover != null && !cover.isRecycled) {
                        val bitmap = remember(cover) { cover.asImageBitmap() }
                        Image(bitmap, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(snapshot.track.ifBlank { "暂未播放" }, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val artist = if (state.custom) listOf(snapshot.artist, "自定义歌词").filter(String::isNotBlank).joinToString(" · ") else snapshot.artist
                    if (artist.isNotBlank()) Text(artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { state.actions?.toggleOverlay() }) {
                    Icon(Icons.Default.Lyrics, if (state.isOverlayRunning) "关闭桌面歌词" else "显示桌面歌词",
                        tint = if (state.isOverlayRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showMore = true }) { Icon(Icons.Default.MoreVert, "更多") }
            }
            if (state.translationMessage.isNotBlank()) {
                SettingsItem(
                    headlineContent = { Text(state.translationMessage, style = MaterialTheme.typography.bodySmall) },
                    trailingContent = { Text("去设置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { state.onTranslationSettings?.invoke() },
                )
            }
            HomeProgress(state)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { state.actions?.skipPrev() }, enabled = snapshot.track.isNotBlank() && snapshot.canPrevious) {
                    Icon(Icons.Default.SkipPrevious, "上一首")
                }
                FilledIconButton(onClick = { state.actions?.togglePlay() }, enabled = snapshot.track.isNotBlank(),
                    modifier = Modifier.padding(horizontal = 20.dp).width(88.dp).height(56.dp), shape = CircleShape) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playing) "暂停" else "播放", modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { state.actions?.skipNext() }, enabled = snapshot.track.isNotBlank() && snapshot.canNext) {
                    Icon(Icons.Default.SkipNext, "下一首")
                }
            }
        }
    }
    if (showMore) HomeMoreSheet(state) { showMore = false }
}

@Composable
private fun HomeProgress(state: HomePresentationState) {
    val duration = state.snapshot.durationMs.coerceAtLeast(0L)
    var position by remember { mutableLongStateOf(state.clock.positionMs()) }
    var draft by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.snapshot.track) {
        dragging = false
        draft = 0f
        position = state.clock.positionMs()
    }
    LaunchedEffect(state.isActive, state.playing, state.playbackRevision, duration) {
        position = state.clock.positionMs()
        while (state.isActive && state.playing) {
            delay(250)
            if (!dragging) position = state.clock.positionMs()
        }
    }
    val displayed = if (dragging) draft.toLong() else position
    Slider(
        value = displayed.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
        onValueChange = { dragging = true; draft = it },
        onValueChangeFinished = { dragging = false; state.seek(draft.toLong()); position = state.clock.positionMs() },
        valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
        enabled = state.snapshot.track.isNotBlank() && duration > 0L,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "播放进度 ${homeFormatTime(displayed)} / ${homeFormatTime(duration)}" },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(homeFormatTime(displayed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(homeFormatTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeLyricsStage(state: HomePresentationState, modifier: Modifier = Modifier) {
    val document = state.document
    if (document.lines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.snapshot.track.isNotBlank() && state.lyricStatus == "loading") {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
                Text(when {
                    state.snapshot.track.isBlank() -> "暂未播放"
                    state.lyricStatus == "loading" -> "正在获取歌词"
                    else -> "未找到歌词"
                }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.lyricStatus == "empty") {
                    Row {
                        IconButton(onClick = { state.actions?.retryLyrics() }) { Icon(Icons.Default.Refresh, "重新获取歌词") }
                        IconButton(onClick = { state.actions?.editCustomLyrics() }) { Icon(Icons.Default.Edit, "添加自定义歌词") }
                    }
                }
            }
        }
        return
    }
    val listState = rememberLazyListState()
    val position = remember(document) { mutableLongStateOf(state.clock.positionMs() + state.lyricOffset) }
    var focused by remember(document) { mutableIntStateOf(homeActiveLine(document, state.clock.positionMs() + state.lyricOffset, state.clock.durationMs)) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var manualUntil by remember(document) { mutableLongStateOf(0L) }
    val scrollProgress = remember { mutableFloatStateOf(1f) }
    val scrollTravel = remember { mutableFloatStateOf(0f) }
    val motion = state.lyricMotionStyle
    val policy = LocalAgoraMotionPolicy.current

    LaunchedEffect(document, state.isActive, state.playing, state.playbackRevision, state.lyricOffset) {
        fun update() {
            val now = state.clock.positionMs() + state.lyricOffset
            position.longValue = now
            val next = homeActiveLine(document, now, state.clock.durationMs)
            if (next != focused) focused = next
        }
        update()
        // Position is read only during glyph drawing. No page-wide Compose state changes per frame.
        while (state.isActive && state.playing) withFrameNanos { update() }
    }
    LaunchedEffect(dragged, document) {
        if (dragged) manualUntil = Long.MAX_VALUE
        else if (manualUntil != 0L) {
            manualUntil = System.nanoTime() / 1_000_000L + 4000L
            delay(4000)
            manualUntil = 0L
        }
    }
    LaunchedEffect(state.seekRevision, state.isActive) {
        manualUntil = 0L
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val centerPx = with(density) { (maxHeight * .45f).toPx() }
        LaunchedEffect(focused, manualUntil, document, state.isActive, motion, policy.allowProgrammaticScrollMotion) {
            if (!state.isActive || dragged || manualUntil != 0L) return@LaunchedEffect
            val target = focused.coerceIn(0, document.lines.lastIndex)
            val centerOffset = listState.layoutInfo.viewportStartOffset + centerPx
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            if (item == null) {
                listState.scrollToItem(target, -centerOffset.toInt())
            }
            val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            val distance = visible?.let { it.offset + it.size / 2f - centerOffset } ?: 0f
            if (abs(distance) <= 1f) return@LaunchedEffect
            val animateScroll = motion.easedScroll && policy.allowProgrammaticScrollMotion && abs(distance) < centerPx * 2f
            if (!animateScroll) listState.scrollBy(distance)
            else {
                scrollTravel.floatValue = distance
                scrollProgress.floatValue = 0f
                try {
                    listState.scroll {
                        var previous = 0f
                        animate(0f, 1f, animationSpec = tween(motion.scrollDurationMs, easing = FastOutSlowInEasing)) { value, _ ->
                            scrollBy(distance * (value - previous))
                            previous = value
                            scrollProgress.floatValue = value
                        }
                    }
                } finally {
                    scrollTravel.floatValue = 0f
                    scrollProgress.floatValue = 1f
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = maxHeight * .45f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(document.lines, key = { index, line -> "$index:${line.startMs}" }) { index, line ->
                HomeLyricRow(
                    line = line,
                    index = index,
                    focused = focused,
                    translationDisplayMode = state.translationDisplayMode,
                    textAlignment = state.textAlignment,
                    motion = motion,
                    allowMotion = state.isActive && policy.allowSpatialTransitions,
                    position = position,
                    scrollProgress = scrollProgress,
                    scrollTravel = scrollTravel,
                    timed = document.timed,
                    onClick = { state.seek(line.startMs - state.lyricOffset) },
                )
            }
        }
        if (document.timed) {
            val dotColor = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize()) {
                val now = position.longValue
                val gapStart = document.lines.getOrNull(focused)?.let(LyricTiming::lineEnd) ?: 0L
                val gapEnd = document.lines.getOrNull(focused + 1)?.startMs ?: state.clock.durationMs
                if (gapEnd - gapStart > (if (focused < 0) 4000L else 4500L) &&
                    now > gapStart + (if (focused < 0) 0L else 300L) && now < gapEnd - 250L) {
                    val anchor = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focused }
                    val y = anchor?.let { it.offset - listState.layoutInfo.viewportStartOffset + it.size + 16.dp.toPx() }
                        ?: centerPx
                    val x = when (state.textAlignment) {
                        HomeLyricsPrefs.CENTER -> size.width / 2f
                        HomeLyricsPrefs.RIGHT -> size.width - 52.dp.toPx()
                        else -> 52.dp.toPx()
                    }
                    val progress = ((now - gapStart).toFloat() / (gapEnd - gapStart)).coerceIn(0f, 1f)
                    for (dot in -1..1) {
                        drawCircle(dotColor.copy(alpha = .35f + progress * .65f), radius = 3.dp.toPx(),
                            center = Offset(x + dot * 14.dp.toPx(), y))
                    }
                }
            }
        }
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
    scrollProgress: State<Float>,
    scrollTravel: State<Float>,
    timed: Boolean,
    onClick: () -> Unit,
) {
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
    val rowProgress = remember { Animatable(1f) }
    LaunchedEffect(focused, motion.stagger, duration, delayMs) {
        if (motion.stagger && duration > 0) {
            rowProgress.snapTo(0f)
            rowProgress.animateTo(1f, tween(duration, delayMillis = delayMs, easing = FastOutSlowInEasing))
        } else rowProgress.snapTo(1f)
    }
    val textAlignment = when (alignment) { HomeLyricsPrefs.CENTER -> TextAlign.Center; HomeLyricsPrefs.RIGHT -> TextAlign.End; else -> TextAlign.Start }
    val origin = TransformOrigin(when (alignment) { HomeLyricsPrefs.CENTER -> .5f; HomeLyricsPrefs.RIGHT -> 1f; else -> 0f }, .5f)
    val color = if (index == focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.fillMaxWidth().graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
            transformOrigin = origin
            translationY = if (motion.stagger && allowMotion) scrollTravel.value * (scrollProgress.value - rowProgress.value) else 0f
        }.blur(depth.dp).clickable(enabled = timed, onClick = onClick).padding(vertical = 10.dp),
    ) {
        val primary = if (translationMode == LyricsOverlayService.TRANSLATION_TRANSLATED && line.translation.isNotBlank()) line.translation else line.text
        KaraokeLyricText(primary, line, position, index < focused, index == focused,
            wordTiming = translationMode != LyricsOverlayService.TRANSLATION_TRANSLATED && line.words.isNotEmpty(),
            color = color, textAlign = textAlignment)
        if (translationMode == LyricsOverlayService.TRANSLATION_BILINGUAL && line.translation.isNotBlank()) {
            Text(line.translation, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = color,
                fontSize = 18.sp, lineHeight = 25.sp, textAlign = textAlignment, fontWeight = FontWeight.Medium)
        }
    }
}

/** Layout once, then redraw only the active line's glyph clip from the monotonic clock. */
@Composable
private fun KaraokeLyricText(
    text: String,
    line: LyricLine,
    position: State<Long>,
    fullySung: Boolean,
    active: Boolean,
    wordTiming: Boolean,
    color: Color,
    textAlign: TextAlign,
) {
    var layout by remember(text, textAlign) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        color = if (wordTiming) color.copy(alpha = .32f) else color,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Medium,
        textAlign = textAlign,
        onTextLayout = { layout = it },
        modifier = Modifier.fillMaxWidth().drawWithCache {
            val result = layout
            onDrawWithContent {
                drawContent()
                if (wordTiming && result != null && text.isNotEmpty()) {
                    val sung = if (fullySung) text.length.toFloat() else if (active) LyricTiming.sungCharacters(line, position.value) else 0f
                    val complete = floor(sung.toDouble()).toInt().coerceIn(0, text.length)
                    val highlight = Path()
                    if (complete > 0) highlight.addPath(result.getPathForRange(0, complete))
                    if (complete < text.length) {
                        val fraction = sung - complete
                        if (fraction > 0f) {
                            val bounds = result.getBoundingBox(complete)
                            val rtl = result.getBidiRunDirection(complete) == ResolvedTextDirection.Rtl
                            highlight.addRect(if (rtl) Rect(bounds.right - bounds.width * fraction, bounds.top, bounds.right, bounds.bottom)
                                else Rect(bounds.left, bounds.top, bounds.left + bounds.width * fraction, bounds.bottom))
                        }
                    }
                    clipPath(highlight) { drawText(result, color = color) }
                }
            }
        },
    )
}

@Composable
private fun HomeMoreSheet(state: HomePresentationState, onDismiss: () -> Unit) {
    val sheet = rememberSmoothBottomSheetState()
    val scroll = rememberLazyListState()
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun select(action: () -> Unit) {
        if (sheet.dismissing || pendingAction != null) return
        pendingAction = action
        sheet.requestDismiss()
    }
    SmoothBottomSheet(
        state = sheet,
        onDismissRequest = {
            val action = pendingAction
            pendingAction = null
            onDismiss()
            action?.invoke()
        },
        contentAtTop = { !scroll.canScrollBackward },
        header = {
            Text("更多", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(state.snapshot.track.ifBlank { "歌词与显示设置" }, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 16.dp))
        },
    ) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { HomeSheetOption(if (state.custom) "编辑自定义歌词" else "添加自定义歌词", Icons.Default.Edit) { select { state.actions?.editCustomLyrics() } } }
            item { HomeSheetOption("管理自定义歌词", Icons.Default.LibraryMusic) { select { state.actions?.manageCustomLyrics() } } }
            item { HomeSheetOption("歌词源管理", Icons.Default.Layers) { select { state.actions?.manageLyricSources() } } }
            item { HomeSheetOption("补充翻译", Icons.Default.Translate) { select { state.onTranslationSettings?.invoke() } } }
            item { HomeSheetOption("下载当前歌词为 .lrc", Icons.Default.Download) { select { state.actions?.exportLrc() } } }
            item { HomeSheetOption("设置", Icons.Default.Settings) { select { state.actions?.openSettings() } } }
            item { TextButton(onClick = { sheet.requestDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("取消") } }
        }
    }
}

@Composable
private fun HomeSheetOption(title: String, icon: ImageVector, onClick: () -> Unit) {
    SettingsItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
    )
}

private fun homeActiveLine(document: LyricDocument, position: Long, duration: Long): Int {
    if (document.lines.isEmpty()) return -1
    return if (document.timed) LyricTiming.activeIndex(document.lines, position)
    else if (duration <= 0L) 0
    else ((position.coerceAtLeast(0L).toDouble() / duration) * document.lines.size).toInt().coerceIn(0, document.lines.lastIndex)
}

private fun homeFormatTime(value: Long): String {
    val seconds = value.coerceAtLeast(0L) / 1000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
