package com.luoh.music.lrc

/** Direct native visual parameters, independent of scrolling mode. */
internal data class PopLyricsStyle(
    val preset: String = "custom",
    val reveal: String = "word",
    val layout: String = "staggered",
    val entrance: String = "scatter",
    val entranceMs: Int = 420,
    val lookaheadMs: Int = 90,
    val travelPercent: Int = 100,
    val activeScalePercent: Int = 145,
    val rotationDegrees: Int = 10,
    val camera: String = "follow",
    val cameraPercent: Int = 80,
    val breathing: Boolean = true,
    val transition: String = "zoom",
    val semanticGrouping: Boolean = true,
    val dynamicSpacing: Boolean = true,
    val groupGapDp: Int = 6,
    val innerGapDp: Int = 0,
    val rowGapPercent: Int = 85,
    val lineWidthPercent: Int = 100,
    val staggerPercent: Int = 150,
    val rowMotionPercent: Int = 65,
    val cameraSpeedPercent: Int = 120,
    val adaptiveTiming: Boolean = true,
    val textHoldMs: Int = 0,
    val passedOpacityPercent: Int = 72,
    val paginate: Boolean = true,
    val pageLength: Int = 10,
    val sustainShakePercent: Int = 100,
    val pushDirection: String = "left",
) {
    fun normalized() = copy(
        preset = preset.takeIf { it in listOf("calm", "flow", "dynamic", "custom") } ?: "custom",
        reveal = if (reveal == "character") reveal else "word",
        layout = layout.takeIf { it in listOf("staggered", "centered", "stairs", "wave", "random") } ?: "centered",
        entrance = entrance.takeIf { it in listOf("scatter", "rise", "zoom", "converge", "disperse", "random") } ?: "rise",
        entranceMs = entranceMs.coerceIn(200, 1000), lookaheadMs = lookaheadMs.coerceIn(0, 250),
        travelPercent = travelPercent.coerceIn(0, 200), activeScalePercent = activeScalePercent.coerceIn(100, 180),
        rotationDegrees = rotationDegrees.coerceIn(0, 30),
        camera = camera.takeIf { it in listOf("off", "drift", "follow", "orbit") } ?: "follow",
        cameraPercent = cameraPercent.coerceIn(0, 200),
        groupGapDp = groupGapDp.coerceIn(0, 24), innerGapDp = innerGapDp.coerceIn(0, 12),
        rowGapPercent = rowGapPercent.coerceIn(0, 250), lineWidthPercent = lineWidthPercent.coerceIn(60, 140),
        staggerPercent = staggerPercent.coerceIn(0, 200), rowMotionPercent = rowMotionPercent.coerceIn(0, 100),
        cameraSpeedPercent = cameraSpeedPercent.coerceIn(50, 180), textHoldMs = textHoldMs.coerceIn(0, 800),
        passedOpacityPercent = passedOpacityPercent.coerceIn(0, 100),
        pageLength = pageLength.coerceIn(4, 24),
        sustainShakePercent = sustainShakePercent.coerceIn(0, 200),
        pushDirection = pushDirection.takeIf { it in listOf("left", "right", "up", "down", "horizontal", "vertical", "random") } ?: "left",
        transition = transition.takeIf { it in listOf("fade", "slide", "zoom", "converge", "disperse", "random") } ?: "fade",
    )
    val maxWordScale get() = activeScalePercent / 100f + (activeScalePercent / 100f - .5f) * .10f
    val continuousCameraMotion get() = camera != "off" && cameraPercent > 0 && (breathing || camera in listOf("drift", "orbit"))
    val frameIntervalNs get() = PopLyricMotion.FRAME_INTERVAL_NS
}
