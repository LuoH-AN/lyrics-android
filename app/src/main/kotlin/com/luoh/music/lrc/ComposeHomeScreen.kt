package com.luoh.music.lrc

import android.graphics.Bitmap
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
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
import kotlin.math.roundToInt

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
    internal var displayOffsetMs by mutableIntStateOf(0)
        private set
    internal var textAlignment by mutableStateOf(HomeLyricsPrefs.LEFT)
        private set
    internal var lyricMotionStyle by mutableStateOf(MotionStyle())
        private set
    internal var presentationActive by mutableStateOf(false)
        private set
    internal var playing by mutableStateOf(false)
        private set
    internal var isOverlayRunning by mutableStateOf(false)
        private set
    internal var playbackRevision by mutableIntStateOf(0)
        private set
    internal var seekRevision by mutableIntStateOf(0)
        private set
    private var lastProgressPlaying = false
    private var lastProgressPositionMs = Long.MIN_VALUE

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
        val wasPlaying = this.playing
        val positionChanged = positionMs != lastProgressPositionMs
        clock.sync(positionMs, playing, speed)
        this.playing = playing
        lastProgressPlaying = playing
        lastProgressPositionMs = positionMs
        // 播放中帧循环自行取值，position 无需作为 effect key；暂停时位置变化
        // （如播放器外部 seek）才需要重启一次让滑杆跟上。此前的 500ms 轮询
        // 每次都 bump，导致两个 LaunchedEffect 每秒被取消重建两次。
        if (playing != wasPlaying || (!playing && positionChanged)) playbackRevision++
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
    fun setLyricOffset(offsetMs: Int) { displayOffsetMs = offsetMs }
    fun setMotionStyle(style: MotionStyle) { lyricMotionStyle = style }
    fun setOverlayState(running: Boolean) { isOverlayRunning = running }
    fun setActive(on: Boolean) { presentationActive = on }

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
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE) }
    var backgroundEnabled by remember(context) { mutableStateOf(HomeLyricsPrefs.albumBackground(context)) }
    var appearance by remember(context) { mutableStateOf(HomeLyricsPrefs.appearance(context)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == HomeLyricsPrefs.KEY_ALBUM_BACKGROUND || key == null)
                backgroundEnabled = HomeLyricsPrefs.albumBackground(context)
            appearance = HomeLyricsPrefs.appearance(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val darkAlbumBackground = backgroundEnabled && state.snapshot.cover?.let { !it.isRecycled } == true
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!state.gateVisible && backgroundEnabled) HomeAlbumBackground(state, Modifier.fillMaxSize().testTag("home_album_background"))
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (state.gateVisible) {
                HomePermissionGate(state.gateStatus, onListenerPermission, onOverlayPermission, onStartOverlay) { state.actions?.openSettings() }
            } else if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    HomeLyricsStage(state, Modifier.weight(1f).fillMaxHeight().homeLyricsFade(), darkAlbumBackground, appearance)
                    HomePlayer(state, Modifier.width(360.dp).align(Alignment.CenterVertically), darkAlbumBackground)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    HomeLyricsStage(state, Modifier.weight(1f).fillMaxWidth().homeLyricsFade(), darkAlbumBackground, appearance)
                    HomePlayer(state, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), darkAlbumBackground)
                }
            }
        }
    }
}

/** Fade the lyric drawing itself so the album background remains continuous. */
private fun Modifier.homeLyricsFade(): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithCache {
    val top = (24.dp.toPx() / size.height.coerceAtLeast(1f)).coerceAtMost(.2f)
    val bottom = (40.dp.toPx() / size.height.coerceAtLeast(1f)).coerceAtMost(.2f)
    val mask = Brush.verticalGradient(0f to Color.Transparent, top to Color.Black,
        (1f - bottom) to Color.Black, 1f to Color.Transparent)
    onDrawWithContent {
        drawContent()
        drawRect(mask, blendMode = BlendMode.DstIn)
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
private fun HomePlayer(state: HomePresentationState, modifier: Modifier = Modifier, albumBackground: Boolean = false) {
    val snapshot = state.snapshot
    var showMore by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(1f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    var detailsHeight by remember(density) { mutableIntStateOf(with(density) { 144.dp.roundToPx() }) }
    val threshold = with(density) { 24.dp.toPx() }
    val flingThreshold = with(density) { 600.dp.toPx() }
    val policy = LocalAgoraMotionPolicy.current
    val settledFraction by animateFloatAsState(
        targetValue = if (dragging) dragFraction else if (expanded) 1f else 0f,
        animationSpec = tween(if (dragging || !policy.allowSpatialTransitions) 0 else 320, easing = FastOutSlowInEasing),
        label = "homePlayerExpansion",
    )
    val fraction = if (dragging) dragFraction else settledFraction
    val dragState = rememberDraggableState { delta ->
        dragDistance += delta
        dragFraction = (dragFraction - delta / detailsHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    val scheme = MaterialTheme.colorScheme
    val playerColors = if (albumBackground) scheme.copy(
        primary = Color.White,
        onPrimary = Color(0xFF18181C),
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = .68f),
        surfaceContainer = Color.White.copy(alpha = .08f),
        surfaceVariant = Color.White.copy(alpha = .12f),
        secondaryContainer = Color.White.copy(alpha = .12f),
        onSecondaryContainer = Color.White,
    ) else scheme
    MaterialTheme(colorScheme = playerColors) {
        Surface(
            modifier.testTag("home_player").semantics {
                stateDescription = if (expanded) "已展开" else "已收起"
                customActions = listOf(CustomAccessibilityAction(if (expanded) "收起播放控制栏" else "展开播放控制栏") {
                    expanded = !expanded
                    true
                })
            }.draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    dragFraction = settledFraction
                    dragDistance = 0f
                    dragging = true
                },
                onDragStopped = { velocity ->
                    expanded = when {
                        abs(velocity) >= flingThreshold -> velocity < 0f
                        abs(dragDistance) >= threshold -> dragDistance < 0f
                        else -> dragFraction >= .5f
                    }
                    dragging = false
                },
            ),
            shape = RoundedCornerShape(28.dp),
            // Elevation shadows show their rectangular interior through this translucent card.
            color = if (albumBackground) Color.Black.copy(alpha = .20f) else MaterialTheme.colorScheme.surface.copy(alpha = .88f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HomePlayerArtwork(state, ringAlpha = 1f - fraction, interactive = !expanded && !dragging && fraction <= .001f)
                    Column(Modifier.weight(1f).clickable(
                        onClickLabel = if (expanded) "收起播放控制栏" else "展开播放控制栏",
                    ) { expanded = !expanded }.semantics {
                        contentDescription = if (expanded) "收起播放控制栏" else "展开播放控制栏"
                    }.padding(start = 12.dp)) {
                        Text(snapshot.track.ifBlank { "暂未播放" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                if (expanded || dragging || fraction > 0f) Column(
                    Modifier.fillMaxWidth().clipToBounds()
                        .semantics { if (!expanded) hideFromAccessibility() }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                            detailsHeight = placeable.height.coerceAtLeast(1)
                            layout(placeable.width, (placeable.height * fraction).roundToInt()) {
                                placeable.placeRelative(0, 0)
                            }
                        },
                ) {
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
                            colors = if (albumBackground) IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = .14f), contentColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = .05f),
                                disabledContentColor = Color.White.copy(alpha = .30f),
                            ) else IconButtonDefaults.filledIconButtonColors(),
                            modifier = Modifier.padding(horizontal = 20.dp).width(88.dp).height(56.dp), shape = CircleShape) {
                            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playing) "暂停" else "播放", modifier = Modifier.size(30.dp))
                        }
                        IconButton(onClick = { state.actions?.skipNext() }, enabled = snapshot.track.isNotBlank() && snapshot.canNext) {
                            Icon(Icons.Default.SkipNext, "下一首")
                        }
                    }
                }
            }
        }
    }
    if (showMore) HomeMoreSheet(state) { showMore = false }
}

@Composable
private fun HomePlayerArtwork(state: HomePresentationState, ringAlpha: Float, interactive: Boolean) {
    val snapshot = state.snapshot
    val position = remember { mutableLongStateOf(state.clock.positionMs()) }
    LaunchedEffect(state.presentationActive, state.playing, state.playbackRevision) {
        position.longValue = state.clock.positionMs()
        while (state.presentationActive && state.playing) {
            delay(100)
            position.longValue = state.clock.positionMs()
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (ringAlpha > 0f) Canvas(Modifier.fillMaxSize().testTag("home_cover_progress")
            .graphicsLayer { alpha = ringAlpha }
            .semantics {
                contentDescription = "封面播放进度"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    if (snapshot.durationMs > 0) (position.longValue.toFloat() / snapshot.durationMs).coerceIn(0f, 1f) else 0f,
                    0f..1f,
                )
            }) {
            val stroke = 2.5.dp.toPx()
            val inset = stroke / 2
            val bounds = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, -90f, 360f, false, Offset(inset, inset), bounds,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
            val progress = if (snapshot.durationMs > 0) (position.longValue.toFloat() / snapshot.durationMs).coerceIn(0f, 1f) else 0f
            if (progress > 0f) drawArc(primary, -90f, 360f * progress, false, Offset(inset, inset), bounds,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
        }
        val artwork = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)
        Box(if (interactive) artwork.clickable(enabled = snapshot.track.isNotBlank(), role = Role.Button,
                onClickLabel = if (state.playing) "暂停播放" else "开始播放") { state.actions?.togglePlay() }
            .semantics {
                contentDescription = if (state.playing) "暂停播放" else "开始播放"
                stateDescription = if (state.playing) "正在播放" else "已暂停"
            } else artwork.semantics { contentDescription = "专辑封面" }, contentAlignment = Alignment.Center) {
            val cover = snapshot.cover
            if (cover != null && !cover.isRecycled) {
                Image(remember(cover) { cover.asImageBitmap() }, null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(26.dp))
            if (interactive && !state.playing && snapshot.track.isNotBlank()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
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
    LaunchedEffect(state.presentationActive, state.playing, state.playbackRevision, duration) {
        position = state.clock.positionMs()
        while (state.presentationActive && state.playing) {
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
private fun HomeLyricsStage(state: HomePresentationState, modifier: Modifier = Modifier, albumBackground: Boolean = false,
    appearance: HomeLyricAppearance = HomeLyricAppearance()) {
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
                    state.snapshot.track.isBlank() -> "暂未播放"
                    state.lyricStatus == "loading" -> "正在获取歌词"
                    else -> "未找到歌词"
                }, style = MaterialTheme.typography.titleLarge, color = secondary)
                if (state.lyricStatus == "empty") {
                    Row {
                        IconButton(onClick = { state.actions?.retryLyrics() }) { Icon(Icons.Default.Refresh, "重新获取歌词", tint = foreground) }
                        IconButton(onClick = { state.actions?.editCustomLyrics() }) { Icon(Icons.Default.Edit, "添加自定义歌词", tint = foreground) }
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
        }
        update()
        // Position is read only during glyph drawing. No page-wide Compose state changes per frame.
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
                    var restingTarget: Int? = null
                    listState.scroll {
                        var previousFrame = 0L
                        while (true) {
                            val frame = withFrameNanos { it }
                            val elapsed = if (previousFrame == 0L) 1f / 60f else (frame - previousFrame) / 1_000_000_000f
                            previousFrame = frame
                            val current = focused.coerceIn(0, document.lines.lastIndex)
                            val layout = listState.layoutInfo
                            val item = layout.visibleItemsInfo.firstOrNull { it.index == current } ?: break
                            val error = item.offset + item.size / 2f - layout.viewportStartOffset - centerPx
                            if (!motion.easedScroll || !policy.allowProgrammaticScrollMotion) {
                                scrollBy(error)
                                follower.reset()
                                scrollVelocity.floatValue = 0f
                                restingTarget = current
                                break
                            }
                            if (follower.settled(error)) {
                                scrollVelocity.floatValue = follower.velocity
                                restingTarget = current
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
                    restingTarget?.let { last -> snapshotFlow { focused }.first { it.coerceAtLeast(0) != last } }
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
                    onClick = { state.seek(line.startMs - state.displayOffsetMs) },
                    activeColor = if (albumBackground) foreground else MaterialTheme.colorScheme.primary,
                    foregroundColor = foreground,
                    appearance = appearance,
                )
            }
        }
        if (document.timed) {
            val dotColor = if (albumBackground) foreground else MaterialTheme.colorScheme.primary
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
    scrollVelocity: State<Float>,
    timed: Boolean,
    onClick: () -> Unit,
    activeColor: Color,
    foregroundColor: Color,
    appearance: HomeLyricAppearance,
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
        }.blur(depth.dp).clickable(
            enabled = timed,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ).padding(vertical = 10.dp),
    ) {
        val primary = if (translationMode == LyricsOverlayService.TRANSLATION_TRANSLATED && line.translation.isNotBlank()) line.translation else line.text
        KaraokeLyricText(primary, line, position, index < focused, index == focused,
            wordTiming = translationMode != LyricsOverlayService.TRANSLATION_TRANSLATED && line.words.isNotEmpty(),
            color = color, textAlign = textAlignment, appearance = appearance)
        if (translationMode == LyricsOverlayService.TRANSLATION_BILINGUAL && line.translation.isNotBlank()) {
            Text(line.translation, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = color,
                fontSize = appearance.translationSize.sp, lineHeight = (appearance.translationSize * 1.4f).sp,
                textAlign = textAlignment, fontWeight = FontWeight(appearance.fontWeight))
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
    appearance: HomeLyricAppearance,
) {
    var layout by remember(text, textAlign) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        color = if (wordTiming) color.copy(alpha = .32f) else color,
        fontSize = appearance.fontSize.sp,
        lineHeight = (appearance.fontSize * 1.3f).sp,
        fontWeight = FontWeight(appearance.fontWeight),
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
