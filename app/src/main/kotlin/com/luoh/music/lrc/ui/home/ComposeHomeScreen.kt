package com.luoh.music.lrc.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.home.pop.PopLyricsPrefs
import com.luoh.music.lrc.ui.home.pop.popStageCamera
import com.luoh.music.lrc.ui.home.pop.rememberPopPlayback
import com.luoh.music.lrc.ui.icons.filled.Lyrics
import com.luoh.music.lrc.ui.share.LyricShareSheet
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    var popStyle by remember(context) { mutableStateOf(PopLyricsPrefs.style(context)) }
    var displayMode by remember(context) { mutableStateOf(HomeLyricsPrefs.displayMode(context)) }
    var keepAwake by remember(context) { mutableStateOf(HomeLyricsPrefs.immersiveKeepAwake(context)) }
    var immersive by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    var controlsBusy by remember { mutableStateOf(false) }
    val immersiveActive = immersive && state.presentationActive && !state.gateVisible
    HomeImmersiveWindow(immersiveActive && state.shareDraft == null, keepAwake && state.playing)
    BackHandler(immersiveActive && state.shareDraft == null) { immersive = false }
    LaunchedEffect(immersiveActive, controlsVisible, touching, controlsBusy, state.playing, state.shareDraft) {
        if (immersiveActive && controlsVisible && !touching && !controlsBusy && state.playing && state.shareDraft == null) {
            delay(4000)
            controlsVisible = false
        }
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == HomeLyricsPrefs.KEY_ALBUM_BACKGROUND || key == null)
                backgroundEnabled = HomeLyricsPrefs.albumBackground(context)
            popStyle = PopLyricsPrefs.style(context)
            appearance = HomeLyricsPrefs.appearance(context)
            displayMode = HomeLyricsPrefs.displayMode(context)
            keepAwake = HomeLyricsPrefs.immersiveKeepAwake(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var portraitMode by rememberSaveable { mutableIntStateOf(HOME_PLAYER_EXPANDED) }
    var portraitDockRight by rememberSaveable { mutableStateOf(false) }
    var landscapeMode by rememberSaveable { mutableIntStateOf(HOME_PLAYER_COVER) }
    var landscapeDockRight by rememberSaveable { mutableStateOf(true) }
    var lastLandscape by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var draggedDock by remember { mutableStateOf<Float?>(null) }
    var playerSize by remember { mutableStateOf(IntSize.Zero) }
    val policy = LocalAgoraMotionPolicy.current
    val popPlayback = if (!state.gateVisible && displayMode == HomeLyricsPrefs.POP && state.document.timed && state.document.lines.isNotEmpty())
        rememberPopPlayback(state, popStyle) else null
    val darkAlbumBackground = backgroundEnabled && state.snapshot.cover?.let { !it.isRecycled } == true
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .then(if (immersiveActive) Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { controlsVisible = true })
        } else Modifier)
        .then(if (immersiveActive && controlsVisible) Modifier.pointerInput(Unit) {
            try {
                awaitPointerEventScope {
                    while (true) touching = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                }
            } finally { touching = false }
        } else Modifier)) {
        // Keep artwork independent of word tracking so detailed covers cannot amplify lyric motion.
        if (!state.gateVisible && backgroundEnabled) HomeAlbumBackground(state,
            Modifier.fillMaxSize().testTag("home_album_background"),
            continuousMotion = displayMode != HomeLyricsPrefs.POP)
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val landscape = maxWidth > maxHeight
            LaunchedEffect(landscape) {
                if (landscape && lastLandscape != true) {
                    landscapeMode = HOME_PLAYER_COVER
                    landscapeDockRight = true
                }
                draggedDock = null
                lastLandscape = landscape
            }
            val enteringLandscape = landscape && lastLandscape != true
            val playerMode = if (enteringLandscape) HOME_PLAYER_COVER else if (landscape) landscapeMode else portraitMode
            val playerDockRight = enteringLandscape || (if (landscape) landscapeDockRight else portraitDockRight)
            val dockFraction by key(landscape) {
                animateFloatAsState(
                    targetValue = draggedDock?.takeIf { lastLandscape == landscape } ?: if (playerDockRight) 1f else 0f,
                    animationSpec = tween(if (draggedDock != null || !policy.allowSpatialTransitions) 0 else 360, easing = FastOutSlowInEasing),
                    label = "homePlayerDock",
                )
            }
            if (state.gateVisible) {
                HomePermissionGate(state.gateStatus, onListenerPermission, onOverlayPermission, onStartOverlay) { state.actions?.openSettings() }
            } else {
                val density = LocalDensity.current
                val playerLimit = if (landscape) minOf(340.dp, maxWidth * .42f)
                    else (maxWidth - 32.dp).coerceAtLeast(80.dp)
                // Keep lyric packing stable while the landscape controls follow a vertical gesture.
                val sideSpace = if (!immersive && landscape && playerMode != HOME_PLAYER_COVER) playerLimit + 28.dp else 0.dp
                val bottomSpace = if (immersive) 0.dp else if (!landscape || playerMode == HOME_PLAYER_COVER)
                    with(density) { playerSize.height.toDp() } + 20.dp else 12.dp
                HomeLyricsStage(state,
                    Modifier.fillMaxSize().padding(
                        start = if (!playerDockRight) sideSpace else 0.dp,
                        end = if (playerDockRight) sideSpace else 0.dp, bottom = bottomSpace)
                        .popStageCamera(popPlayback, popStyle, policy.allowSpatialTransitions),
                    darkAlbumBackground, appearance, displayMode, popStyle, popPlayback,
                    onLyricTap = if (immersive) ({ controlsVisible = true }) else null)
                val screenWidthPx = with(density) { maxWidth.roundToPx() }
                val insetPx = with(density) { 16.dp.roundToPx() }
                if (!immersive || controlsVisible) key(landscape) {
                    HomePlayer(state, playerMode,
                        onEnterImmersive = { immersive = true; controlsVisible = false },
                        onBusyChange = { controlsBusy = it },
                        onModeChange = { if (landscape) landscapeMode = it else portraitMode = it }, dockRight = playerDockRight,
                        onDockChange = { if (landscape) landscapeDockRight = it else portraitDockRight = it },
                        onDockDrag = { draggedDock = it }, tall = landscape,
                        modifier = Modifier.align(Alignment.BottomStart).widthIn(max = playerLimit)
                            .then(if (landscape) Modifier.heightIn(max = (maxHeight - 32.dp).coerceAtLeast(56.dp)) else Modifier)
                            .onSizeChanged { playerSize = it }
                            .offset {
                                IntOffset(insetPx + ((screenWidthPx - playerSize.width - insetPx * 2).coerceAtLeast(0) * dockFraction).roundToInt(), -insetPx)
                            }, albumBackground = darkAlbumBackground)
                }
                if (immersive && controlsVisible) TextButton(onClick = { immersive = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Text("退出沉浸", color = if (darkAlbumBackground) Color.White else MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    state.shareDraft?.let { draft -> LyricShareSheet(draft) { state.shareDraft = null } }
}

/** Fade the lyric drawing itself so the album background remains continuous. */
internal fun Modifier.homeLyricsFade(): Modifier = graphicsLayer {
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
                Text(status.ifBlank { "通知使用权用于获取歌曲信息；开启桌面歌词还需悬浮窗权限。" },
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onListenerPermission, modifier = Modifier.fillMaxWidth()) { Text("通知使用权") }
                OutlinedButton(onClick = onOverlayPermission, modifier = Modifier.fillMaxWidth()) { Text("悬浮窗权限") }
                Button(onClick = onStartOverlay, modifier = Modifier.fillMaxWidth()) { Text("开启桌面歌词") }
                TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("设置") }
            }
        }
    }
}
