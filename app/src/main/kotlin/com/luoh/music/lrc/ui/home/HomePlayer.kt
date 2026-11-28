package com.luoh.music.lrc.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.R
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.icons.filled.Lyrics
import com.luoh.music.lrc.ui.icons.filled.MusicNote
import com.luoh.music.lrc.ui.icons.filled.Pause
import com.luoh.music.lrc.ui.icons.filled.SkipNext
import com.luoh.music.lrc.ui.icons.filled.SkipPrevious
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val HOME_PLAYER_COVER = 0
private const val HOME_PLAYER_COMPACT = 1
internal const val HOME_PLAYER_EXPANDED = 2

@Composable
internal fun HomePlayer(state: HomePresentationState, mode: Int, onModeChange: (Int) -> Unit, dockRight: Boolean,
    onDockChange: (Boolean) -> Unit, onDockDrag: (Float?) -> Unit, tall: Boolean = false,
    modifier: Modifier = Modifier, albumBackground: Boolean = false,
    onEnterImmersive: () -> Unit = {}, onBusyChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val snapshot = state.snapshot
    var showMore by rememberSaveable { mutableStateOf(false) }
    var correctionSong by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(showMore, correctionSong) { onBusyChange(showMore || correctionSong != null) }
    DisposableEffect(Unit) { onDispose { onBusyChange(false) } }
    val expanded = mode == HOME_PLAYER_EXPANDED
    fun setExpanded(value: Boolean) = onModeChange(if (value) HOME_PLAYER_EXPANDED else HOME_PLAYER_COMPACT)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(1f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var verticalStartMode by remember { mutableIntStateOf(mode) }
    var releaseFraction by remember { mutableStateOf<Float?>(null) }
    val density = LocalDensity.current
    var detailsHeight by remember(density) { mutableIntStateOf(with(density) { 144.dp.roundToPx() }) }
    var verticalRangePx by remember(density) { mutableIntStateOf(with(density) { 144.dp.roundToPx() }) }
    val threshold = with(density) { 24.dp.toPx() }
    val flingThreshold = with(density) { 600.dp.toPx() }
    val policy = LocalAgoraMotionPolicy.current
    val expansion = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded, dragging, policy.allowSpatialTransitions) {
        if (!dragging) {
            releaseFraction?.let { expansion.snapTo(it) }
            releaseFraction = null
            expansion.animateTo(if (expanded) 1f else 0f,
                tween(if (policy.allowSpatialTransitions) 420 else 0, easing = FastOutSlowInEasing))
        }
    }
    val settledFraction = expansion.value
    val fraction = if (dragging) dragFraction else releaseFraction ?: settledFraction
    val dragState = rememberDraggableState { delta ->
        dragDistance += delta
        if (verticalStartMode != HOME_PLAYER_COVER)
            dragFraction = (dragFraction - delta / (if (tall) verticalRangePx else detailsHeight).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    var horizontalDragging by remember { mutableStateOf(false) }
    var horizontalFraction by remember { mutableFloatStateOf(1f) }
    var horizontalDistance by remember { mutableFloatStateOf(0f) }
    var horizontalStartMode by remember { mutableIntStateOf(HOME_PLAYER_COMPACT) }
    var horizontalStartDock by remember { mutableFloatStateOf(0f) }
    val settledHorizontal by animateFloatAsState(
        targetValue = if (horizontalDragging) horizontalFraction else if (mode == HOME_PLAYER_COVER) 0f else 1f,
        animationSpec = tween(if (horizontalDragging || !policy.allowSpatialTransitions) 0 else 320, easing = FastOutSlowInEasing),
        label = "homePlayerRetraction",
    )
    val reveal = if (horizontalDragging) horizontalFraction else settledHorizontal
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
        BoxWithConstraints(modifier) {
            val compactWidth = if (tall) minOf(300.dp, maxWidth) else maxWidth
            val fullWidth = (compactWidth + (maxWidth - compactWidth) * fraction).coerceAtLeast(80.dp)
            val bodyWidthPx = with(density) { (fullWidth - 80.dp).roundToPx().coerceAtLeast(1) }
            val retractWidthPx = with(density) { (fullWidth - 56.dp).toPx().coerceAtLeast(1f) }
            val horizontalDragState = rememberDraggableState { delta ->
                horizontalDistance += delta
                if (horizontalStartMode == HOME_PLAYER_COMPACT) {
                    horizontalFraction = (1f - abs(horizontalDistance) / retractWidthPx).coerceIn(0f, 1f)
                    val targetDock = if (horizontalDistance >= 0f) 1f else 0f
                    onDockDrag(horizontalStartDock + (targetDock - horizontalStartDock) * (1f - horizontalFraction))
                } else {
                    val inward = horizontalDistance * if (dockRight) -1f else 1f
                    horizontalFraction = (inward / retractWidthPx).coerceIn(0f, 1f)
                }
            }
            Surface(
                Modifier.width(56.dp + (fullWidth - 56.dp) * reveal)
                    .testTag("home_player").semantics {
                    stateDescription = when (mode) {
                        HOME_PLAYER_COVER -> context.getString(R.string.album_art_only)
                        HOME_PLAYER_COMPACT -> context.getString(R.string.collapsed)
                        else -> context.getString(R.string.expanded)
                    }
                    customActions = buildList {
                        add(CustomAccessibilityAction(if (expanded) context.getString(R.string.collapse_playback_controls) else context.getString(R.string.expand_playback_controls)) {
                            setExpanded(!expanded)
                            true
                        })
                        if (mode != HOME_PLAYER_EXPANDED) {
                            add(CustomAccessibilityAction(context.getString(R.string.move_album_art_to_bottom_left)) {
                                onDockChange(false); onModeChange(HOME_PLAYER_COVER); true
                            })
                            add(CustomAccessibilityAction(context.getString(R.string.move_album_art_to_bottom_right)) {
                                onDockChange(true); onModeChange(HOME_PLAYER_COVER); true
                            })
                        }
                    }
                }.draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    enabled = !horizontalDragging,
                    onDragStarted = {
                        verticalStartMode = mode
                        dragFraction = releaseFraction ?: settledFraction
                        releaseFraction = null
                        dragDistance = 0f
                        dragging = true
                    },
                    onDragStopped = { velocity ->
                        val direction = when {
                            abs(velocity) >= flingThreshold -> velocity
                            abs(dragDistance) >= threshold -> dragDistance
                            else -> 0f
                        }
                        onModeChange(when {
                            direction < 0f -> if (verticalStartMode == HOME_PLAYER_COVER) HOME_PLAYER_COMPACT else HOME_PLAYER_EXPANDED
                            direction > 0f -> if (verticalStartMode == HOME_PLAYER_EXPANDED) HOME_PLAYER_COMPACT else HOME_PLAYER_COVER
                            verticalStartMode == HOME_PLAYER_COVER -> HOME_PLAYER_COVER
                            dragFraction >= .5f -> HOME_PLAYER_EXPANDED
                            else -> HOME_PLAYER_COMPACT
                        })
                        releaseFraction = dragFraction
                        dragging = false
                    },
                ).then(if (tall) Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    verticalRangePx = (constraints.maxHeight - with(density) { 72.dp.roundToPx() }).coerceAtLeast(1)
                    val compactHeight = with(density) { (56.dp + 16.dp * reveal).toPx() }
                    val height = (compactHeight + (constraints.maxHeight - compactHeight) * fraction)
                        .roundToInt().coerceAtLeast(placeable.height)
                    layout(placeable.width, height) { placeable.placeRelative(0, (height - placeable.height) / 2) }
                } else Modifier),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(Modifier.padding(horizontal = 12.dp * reveal, vertical = 8.dp * reveal),
                    verticalArrangement = Arrangement.spacedBy(if (tall) 12.dp * fraction else 0.dp)) {
                    Row(Modifier.fillMaxWidth().testTag("home_player_header").draggable(
                        state = horizontalDragState, orientation = Orientation.Horizontal,
                        enabled = !expanded && !dragging,
                        onDragStarted = {
                            horizontalFraction = settledHorizontal
                            horizontalDistance = 0f
                            horizontalStartMode = mode
                            horizontalStartDock = if (dockRight) 1f else 0f
                            horizontalDragging = true
                        },
                        onDragStopped = { velocity ->
                            if (horizontalStartMode == HOME_PLAYER_COMPACT) {
                                val committed = abs(horizontalDistance) >= threshold || abs(velocity) >= flingThreshold
                                if (committed) {
                                    onDockChange(if (abs(velocity) >= flingThreshold) velocity > 0f else horizontalDistance > 0f)
                                    onModeChange(HOME_PLAYER_COVER)
                                } else onModeChange(HOME_PLAYER_COMPACT)
                            } else {
                                val inwardDistance = horizontalDistance * if (dockRight) -1f else 1f
                                val inwardVelocity = velocity * if (dockRight) -1f else 1f
                                onModeChange(if (inwardDistance >= threshold || inwardVelocity >= flingThreshold)
                                    HOME_PLAYER_COMPACT else HOME_PLAYER_COVER)
                            }
                            onDockDrag(null)
                            horizontalDragging = false
                        },
                    ), verticalAlignment = Alignment.CenterVertically) {
                        HomePlayerArtwork(state, ringAlpha = 1f - fraction,
                            interactive = !expanded && !dragging && !horizontalDragging && fraction <= .001f,
                            onExpand = { onModeChange(HOME_PLAYER_COMPACT) })
                        if (mode != HOME_PLAYER_COVER || horizontalDragging || reveal > 0f) Row(
                            Modifier.clipToBounds().graphicsLayer { alpha = reveal }
                                .semantics { if (mode == HOME_PLAYER_COVER) hideFromAccessibility() }
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints.copy(minWidth = bodyWidthPx, maxWidth = bodyWidthPx))
                                    layout((bodyWidthPx * reveal).roundToInt(), placeable.height) {
                                        // The trailing controls slide into the fixed album artwork.
                                        placeable.placeRelative(((reveal - 1f) * bodyWidthPx * if (horizontalDistance > 0f) -1f else 1f).roundToInt(), 0)
                                    }
                                }, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).clickable(
                                onClickLabel = if (expanded) context.getString(R.string.collapse_playback_controls) else context.getString(R.string.expand_playback_controls),
                            ) { setExpanded(!expanded) }.semantics {
                                contentDescription = if (expanded) context.getString(R.string.collapse_playback_controls) else context.getString(R.string.expand_playback_controls)
                            }.padding(start = 12.dp)) {
                                Text(snapshot.track.ifBlank { context.getString(R.string.nothing_playing) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val artist = if (state.custom) listOf(snapshot.artist, context.getString(R.string.custom_lyrics)).filter(String::isNotBlank).joinToString(" · ") else snapshot.artist
                                if (artist.isNotBlank()) Text(artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { state.actions?.toggleOverlay() }) {
                                Icon(Icons.Default.Lyrics, if (state.isOverlayRunning) context.getString(R.string.close_lyrics_overlay) else context.getString(R.string.open_lyrics_overlay),
                                    tint = if (state.isOverlayRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { showMore = true }) { Icon(Icons.Default.MoreVert, context.getString(R.string.more)) }
                        }
                    }
                    if (expanded || dragging || fraction > 0f) Column(
                        Modifier.fillMaxWidth().testTag("home_player_details").clipToBounds()
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
                                trailingContent = { Text(context.getString(R.string.view_settings), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { state.onTranslationSettings?.invoke() },
                            )
                        }
                        HomeProgress(state)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            IconButton(onClick = { state.actions?.skipPrev() }, enabled = snapshot.track.isNotBlank() && snapshot.canPrevious) {
                                Icon(Icons.Default.SkipPrevious, context.getString(R.string.previous_track))
                            }
                            FilledIconButton(onClick = { state.actions?.togglePlay() }, enabled = snapshot.track.isNotBlank(),
                                colors = if (albumBackground) IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = .14f), contentColor = Color.White,
                                    disabledContainerColor = Color.White.copy(alpha = .05f),
                                    disabledContentColor = Color.White.copy(alpha = .30f),
                                ) else IconButtonDefaults.filledIconButtonColors(),
                                modifier = Modifier.padding(horizontal = 20.dp).width(88.dp).height(56.dp), shape = CircleShape) {
                                Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playing) context.getString(R.string.pause) else context.getString(R.string.play), modifier = Modifier.size(30.dp))
                            }
                            IconButton(onClick = { state.actions?.skipNext() }, enabled = snapshot.track.isNotBlank() && snapshot.canNext) {
                                Icon(Icons.Default.SkipNext, context.getString(R.string.next_track))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showMore) HomeMoreSheet(state, onEnterImmersive, onCorrectSong = {
        correctionSong = state.snapshot.track to state.snapshot.artist
    }) { showMore = false }
    correctionSong?.let { song ->
        SongCorrectionDialog(song.first, song.second, onDismiss = { correctionSong = null }) { title, artist ->
            state.actions?.correctSongSearch(song.first, song.second, title, artist)
            correctionSong = null
        }
    }
}

@Composable
private fun HomePlayerArtwork(state: HomePresentationState, ringAlpha: Float, interactive: Boolean, onExpand: () -> Unit) {
    val context = LocalContext.current
    val snapshot = state.snapshot
    val position = remember { mutableLongStateOf(state.clock.positionMs()) }
    val ringVisible = ringAlpha > 0f
    LaunchedEffect(state.presentationActive, state.playing, state.playbackRevision, ringVisible) {
        position.longValue = state.clock.positionMs()
        while (state.presentationActive && state.playing && ringVisible) {
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
                contentDescription = context.getString(R.string.album_art_playback_progress)
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
        Box(if (interactive) artwork.combinedClickable(enabled = snapshot.track.isNotBlank(), role = Role.Button,
                onClickLabel = if (state.playing) context.getString(R.string.pause_playback) else context.getString(R.string.start_playback),
                onClick = { state.actions?.togglePlay() }, onLongClickLabel = context.getString(R.string.expand_playback_controls), onLongClick = onExpand)
            .semantics {
                contentDescription = if (state.playing) context.getString(R.string.pause_playback) else context.getString(R.string.start_playback)
                stateDescription = if (state.playing) context.getString(R.string.now_playing) else context.getString(R.string.paused)
            } else artwork.semantics { contentDescription = context.getString(R.string.album_art) }, contentAlignment = Alignment.Center) {
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
    val context = LocalContext.current
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
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = context.getString(R.string.playback_position, homeFormatTime(displayed), homeFormatTime(duration)) },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(homeFormatTime(displayed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(homeFormatTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun homeFormatTime(value: Long): String {
    val seconds = value.coerceAtLeast(0L) / 1000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
