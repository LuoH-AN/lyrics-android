package com.luoh.music.lrc

/** A display mode as exposed by WindowManager, kept free of Android types for testing. */
data class OverlayDisplayMode(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

/**
 * The overlay follows the lyric clock every frame, so ask for the highest refresh rate that
 * keeps the current resolution. Switching resolution would re-layout the window mid-animation,
 * so modes with a different physical size are ignored.
 */
fun preferredOverlayRefreshRate(
    modes: List<OverlayDisplayMode>,
    current: OverlayDisplayMode,
): Float {
    val sameSize = modes.filter { it.width == current.width && it.height == current.height }
    val candidates = sameSize.ifEmpty { modes }
    val fastest = candidates.maxOfOrNull { it.refreshRate } ?: current.refreshRate
    return fastest.takeIf { it > 0f } ?: 0f
}
