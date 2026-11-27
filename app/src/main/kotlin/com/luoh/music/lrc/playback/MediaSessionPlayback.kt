package com.luoh.music.lrc.playback

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock

internal fun firstMetadataString(metadata: MediaMetadata?, vararg keys: String): String {
    if (metadata == null) return ""
    for (key in keys) {
        metadata.getString(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return ""
}

internal fun mediaSessionScore(controller: MediaController, hasTitle: Boolean): Int {
    val stateScore = when (controller.playbackState?.state) {
        PlaybackState.STATE_PLAYING -> 1000
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> 800
        PlaybackState.STATE_PAUSED -> 600
        else -> 100
    }
    return stateScore + if (hasTitle) 100 else 0
}

internal fun playbackPositionMs(
    state: PlaybackState?,
    durationMs: Long,
    nowMs: Long = SystemClock.elapsedRealtime(),
): Long {
    if (state == null) return 0L
    var position = state.position.coerceAtLeast(0L)
    val updateTime = state.lastPositionUpdateTime
    if (state.state == PlaybackState.STATE_PLAYING && state.playbackSpeed > 0f && updateTime > 0L) {
        val elapsed = (nowMs - updateTime).coerceAtLeast(0L)
        position += (elapsed * state.playbackSpeed).toLong()
    }
    return if (durationMs > 0L) position.coerceAtMost(durationMs) else position
}
