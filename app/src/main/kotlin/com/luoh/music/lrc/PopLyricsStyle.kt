package com.luoh.music.lrc

import android.content.Context

/** Direct native visual parameters, independent of scrolling mode. The preset field only reads legacy preferences. */
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
) {
    fun normalized() = copy(
        preset = preset.takeIf { it in listOf("calm", "flow", "dynamic", "custom") } ?: "custom",
        reveal = if (reveal == "character") reveal else "word",
        layout = layout.takeIf { it in listOf("staggered", "centered", "stairs") } ?: "centered",
        entrance = entrance.takeIf { it in listOf("scatter", "rise", "zoom") } ?: "rise",
        entranceMs = entranceMs.coerceIn(200, 1000), lookaheadMs = lookaheadMs.coerceIn(0, 250),
        travelPercent = travelPercent.coerceIn(0, 200), activeScalePercent = activeScalePercent.coerceIn(100, 180),
        rotationDegrees = rotationDegrees.coerceIn(0, 30),
        camera = camera.takeIf { it in listOf("off", "drift", "follow") } ?: "follow",
        cameraPercent = cameraPercent.coerceIn(0, 200),
        groupGapDp = groupGapDp.coerceIn(0, 24), innerGapDp = innerGapDp.coerceIn(0, 12),
        rowGapPercent = rowGapPercent.coerceIn(0, 250), lineWidthPercent = lineWidthPercent.coerceIn(60, 140),
        staggerPercent = staggerPercent.coerceIn(0, 200), rowMotionPercent = rowMotionPercent.coerceIn(0, 100),
        cameraSpeedPercent = cameraSpeedPercent.coerceIn(50, 180), textHoldMs = textHoldMs.coerceIn(0, 800),
        passedOpacityPercent = passedOpacityPercent.coerceIn(0, 100),
        pageLength = pageLength.coerceIn(4, 24),
        transition = transition.takeIf { it in listOf("fade", "slide", "zoom") } ?: "fade",
    )
    val maxWordScale get() = activeScalePercent / 100f + (activeScalePercent / 100f - .5f) * .10f
    val continuousCameraMotion get() = camera != "off" && cameraPercent > 0 && breathing
    val frameIntervalNs get() = PopLyricMotion.FRAME_INTERVAL_NS
}

internal object PopLyricsPrefs {
    private const val KEY = "pop_"
    fun style(context: Context): PopLyricsStyle {
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        val defaults = PopLyricsStyle()
        if (prefs.getInt(KEY + "defaults_revision", 0) < 3) {
            // Apply this motion overhaul once on upgrade; subsequent edits remain user-owned.
            save(context, defaults)
            return defaults
        }
        fun string(name: String, fallback: String) = prefs.getString(KEY + name, fallback) ?: fallback
        return PopLyricsStyle(string("preset", defaults.preset), string("reveal", defaults.reveal),
            string("layout", defaults.layout), string("entrance", defaults.entrance),
            prefs.getInt(KEY + "entrance_ms", defaults.entranceMs), prefs.getInt(KEY + "lookahead_ms", defaults.lookaheadMs),
            prefs.getInt(KEY + "travel", defaults.travelPercent), prefs.getInt(KEY + "scale", defaults.activeScalePercent),
            prefs.getInt(KEY + "rotation", defaults.rotationDegrees), string("camera", defaults.camera),
            prefs.getInt(KEY + "camera_amount", defaults.cameraPercent), prefs.getBoolean(KEY + "breathing", defaults.breathing),
            string("transition", defaults.transition),
            prefs.getBoolean(KEY + "semantic_grouping", defaults.semanticGrouping),
            prefs.getBoolean(KEY + "dynamic_spacing", defaults.dynamicSpacing),
            prefs.getInt(KEY + "group_gap", defaults.groupGapDp), prefs.getInt(KEY + "inner_gap", defaults.innerGapDp),
            prefs.getInt(KEY + "row_gap", defaults.rowGapPercent), prefs.getInt(KEY + "line_width", defaults.lineWidthPercent),
            prefs.getInt(KEY + "stagger", defaults.staggerPercent), prefs.getInt(KEY + "row_motion", defaults.rowMotionPercent),
            prefs.getInt(KEY + "camera_speed", defaults.cameraSpeedPercent),
            prefs.getBoolean(KEY + "adaptive_timing", defaults.adaptiveTiming),
            prefs.getInt(KEY + "text_hold", defaults.textHoldMs), prefs.getInt(KEY + "passed_opacity", defaults.passedOpacityPercent),
            prefs.getBoolean(KEY + "paginate", defaults.paginate), prefs.getInt(KEY + "page_length", defaults.pageLength)).normalized()
    }
    fun save(context: Context, value: PopLyricsStyle) {
        val style = value.normalized()
        context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY + "defaults_revision", 3)
            .putString(KEY + "preset", style.preset).putString(KEY + "reveal", style.reveal)
            .putString(KEY + "layout", style.layout).putString(KEY + "entrance", style.entrance)
            .putInt(KEY + "entrance_ms", style.entranceMs).putInt(KEY + "lookahead_ms", style.lookaheadMs)
            .putInt(KEY + "travel", style.travelPercent).putInt(KEY + "scale", style.activeScalePercent)
            .putInt(KEY + "rotation", style.rotationDegrees).putString(KEY + "camera", style.camera)
            .putInt(KEY + "camera_amount", style.cameraPercent).putBoolean(KEY + "breathing", style.breathing)
            .putString(KEY + "transition", style.transition)
            .putBoolean(KEY + "semantic_grouping", style.semanticGrouping).putBoolean(KEY + "dynamic_spacing", style.dynamicSpacing)
            .putInt(KEY + "group_gap", style.groupGapDp).putInt(KEY + "inner_gap", style.innerGapDp)
            .putInt(KEY + "row_gap", style.rowGapPercent).putInt(KEY + "line_width", style.lineWidthPercent)
            .putInt(KEY + "stagger", style.staggerPercent).putInt(KEY + "row_motion", style.rowMotionPercent)
            .putInt(KEY + "camera_speed", style.cameraSpeedPercent).putBoolean(KEY + "adaptive_timing", style.adaptiveTiming)
            .putInt(KEY + "text_hold", style.textHoldMs).putInt(KEY + "passed_opacity", style.passedOpacityPercent)
            .putBoolean(KEY + "paginate", style.paginate).putInt(KEY + "page_length", style.pageLength).apply()
    }
}
