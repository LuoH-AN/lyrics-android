package com.luoh.music.lrc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import kotlinx.coroutines.delay

/** A silent example using exactly the home renderer and its playback clock. */
@Composable
internal fun PopLyricPreview(style: PopLyricsStyle, appearance: HomeLyricAppearance, compact: Boolean = false,
    onPin: (() -> Unit)? = null) {
    val state = remember {
        fun line(start: Long, text: String, step: Long, lastHold: Long): LyricLine {
            val words = text.mapIndexed { index, char ->
                LyricWord(start + index * step, if (index == text.lastIndex) lastHold else step, char.toString())
            }
            return LyricLine(start, text, endMs = words.last().let { it.startMs + it.durationMs }, words = words)
        }
        val first = line(0, "让音乐陪着你穿过城市的灯火到天亮", 230, 1800)
        val quick = line(requireNotNull(first.endMs), "跟着节奏向前奔跑不要停下脚步", 120, 120)
        val last = line(requireNotNull(quick.endMs), "把这一刻留下", 320, 1600)
        HomePresentationState().apply {
            setSnapshot(HomePresentationState.Snapshot(track = "歌词预览", durationMs = requireNotNull(last.endMs) + 400))
            setLyrics(LyricDocument(listOf(first, quick, last), true))
            setTranslationMode(LyricsOverlayService.TRANSLATION_ORIGINAL)
        }
    }
    var play by rememberSaveable { mutableStateOf(true) }
    var visible by remember { mutableStateOf(false) }
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); state.setActive(false); state.setProgress(state.clock.positionMs(), false) }
    }
    val pageActive = LocalSettingsScrollRetentionActive.current
    val active = visible && resumed && pageActive
    LaunchedEffect(active, play) {
        state.setActive(active)
        state.setProgress(state.clock.positionMs(), active && play)
        while (active && play) {
            delay(100)
            if (state.clock.positionMs() >= state.clock.durationMs) state.setProgress(0, true)
        }
    }
    val frame = rememberPopPlayback(state, style)
    val allowMotion = LocalAgoraMotionPolicy.current.allowSpatialTransitions
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Box(Modifier.fillMaxWidth().height(if (compact) 156.dp else 260.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("pop_lyric_preview")
            .onGloballyPositioned {
                val bounds = it.boundsInWindow()
                visible = bounds.width > 0f && bounds.height > 0f && bounds.bottom > 0f && bounds.top < view.height
            }) {
            PopLyricsStage(state, Modifier.fillMaxSize().popStageCamera(frame, style, allowMotion),
                false, appearance, style, frame, interactive = false)
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { play = !play }) { Text(if (play) "暂停" else "继续") }
            TextButton(onClick = { state.seek(0) }) { Text("重播") }
            if (onPin != null) {
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onPin) { Text("固定") }
            }
        }
    }
}
