package com.luoh.music.lrc

import kotlin.math.min

internal data class PopStageLayout(val insetX: Float, val insetY: Float, val bodyWidth: Float,
    val bodyHeight: Float, val captionWidth: Float, val captionGap: Float, val captionLines: Int,
    val captionReserve: Float, val anchor: Float, val captionScale: Float)

/** Physical viewport and font scale define a safe text/subtitle composition, including small previews. */
internal fun popStageLayout(width: Float, height: Float, density: Float, fontScale: Float,
    appearance: HomeLyricAppearance, style: PopLyricsStyle, bilingual: Boolean): PopStageLayout {
    val insetX = min(style.stagePaddingDp * density, width * .065f).coerceAtLeast(0f)
    val insetY = min(12f * density, height * .04f).coerceAtLeast(0f)
    val bodyWidth = (width - insetX * 2f).coerceAtLeast(1f)
    val innerHeight = (height - insetY * 2f).coerceAtLeast(1f)
    val lineHeight = appearance.translationSize * fontScale * density * 1.4f
    val captionGap = min(style.translationGapDp * density, innerHeight * .07f)
    val lines = if (bilingual) min(style.translationMaxLines,
        (innerHeight * .28f / lineHeight.coerceAtLeast(1f)).toInt().coerceAtLeast(1)) else 0
    val reserve = if (bilingual) min(innerHeight * .42f, lineHeight * lines + captionGap) else 0f
    val captionScale = if (bilingual) ((reserve - captionGap) / (lineHeight * lines).coerceAtLeast(1f)).coerceIn(0f, 1f) else 1f
    return PopStageLayout(insetX, insetY, bodyWidth, (innerHeight - reserve).coerceAtLeast(1f),
        min(bodyWidth, appearance.translationSize * fontScale * density * captionScale * 36f), captionGap, lines,
        reserve, style.stageAnchorPercent / 100f, captionScale)
}

/** A taller retiring phrase keeps its subtitle clearance until it is almost transparent. */
internal fun popCaptionTop(currentBottom: Float, retiringBottom: Float?, progress: Float,
    gap: Float, maxTop: Float): Float {
    val start = maxOf(currentBottom, retiringBottom ?: currentBottom)
    val settle = PopVisualMotion.travel((progress - .55f) / .45f)
    return (start + (currentBottom - start) * settle + gap).coerceIn(0f, maxTop.coerceAtLeast(0f))
}
