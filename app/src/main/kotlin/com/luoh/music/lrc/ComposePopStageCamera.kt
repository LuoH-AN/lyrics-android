package com.luoh.music.lrc

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

internal fun Modifier.popStageCamera(frame: PopPlaybackFrame?, style: PopLyricsStyle, allowMotion: Boolean): Modifier {
    if (frame == null || !allowMotion || style.camera == "off" || style.cameraPercent == 0) return this
    val cameraStyle = PopMotionOrchestration.cameraStyle(style)
    return graphicsLayer {
        val pose = PopStageMotion.sample(frame.cameraPosition.value, frame.cameraPath.value, cameraStyle)
        val classic = style.sizingMode == "classic"
        // Restore the camera's actual pan only for fixed phrase slots.
        translationX = if (classic) pose.x * size.width else 0f
        translationY = if (classic) pose.y * size.height else 0f
        rotationZ = if (classic) pose.rotation else 0f
        // Focus renders pan/tilt as centered parallax and only scales the composition here.
        scaleX = pose.zoom
        scaleY = pose.zoom
    }
}
