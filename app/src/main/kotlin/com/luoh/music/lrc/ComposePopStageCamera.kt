package com.luoh.music.lrc

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

internal fun Modifier.popStageCamera(frame: PopPlaybackFrame?, style: PopLyricsStyle, allowMotion: Boolean): Modifier {
    if (frame == null || !allowMotion || style.camera == "off" || style.cameraPercent == 0) return this
    val cameraStyle = PopMotionOrchestration.cameraStyle(style)
    return graphicsLayer {
        val pose = PopStageMotion.sample(frame.cameraPosition.value, frame.cameraPath.value, cameraStyle)
        // Pan/tilt is centered inside the glyph renderer; only breathing scales the whole composition.
        scaleX = pose.zoom
        scaleY = pose.zoom
    }
}
