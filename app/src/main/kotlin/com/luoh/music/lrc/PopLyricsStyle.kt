package com.luoh.music.lrc

import android.content.Context

/** Direct native visual parameters, independent of scrolling mode. The preset field only reads legacy preferences. */
internal data class PopLyricsStyle(
    val preset: String = "custom",
    val reveal: String = "word",
    val layout: String = "centered",
    val entrance: String = "rise",
    val entranceMs: Int = 600,
    val lookaheadMs: Int = 150,
    val travelPercent: Int = 65,
    val activeScalePercent: Int = 130,
    val rotationDegrees: Int = 0,
    val camera: String = "follow",
    val cameraPercent: Int = 70,
    val breathing: Boolean = false,
    val transition: String = "fade",
    val semanticGrouping: Boolean = true,
    val dynamicSpacing: Boolean = true,
    val groupGapDp: Int = 8,
    val innerGapDp: Int = 0,
    val rowGapPercent: Int = 100,
    val lineWidthPercent: Int = 100,
    val staggerPercent: Int = 100,
    val rowMotionPercent: Int = 35,
    val cameraSpeedPercent: Int = 100,
    val adaptiveTiming: Boolean = true,
    val textHoldMs: Int = 0,
    val passedOpacityPercent: Int = 82,
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
        transition = transition.takeIf { it in listOf("fade", "slide", "zoom") } ?: "fade",
    )
    val maxWordScale get() = activeScalePercent / 100f + .06f
    val continuousCameraMotion get() = camera != "off" && cameraPercent > 0 && breathing
    val frameIntervalNs get() = if (continuousCameraMotion) 50_000_000L else PopLyricMotion.FRAME_INTERVAL_NS
}

internal object PopLyricsPrefs {
    private const val KEY = "pop_"
    fun style(context: Context): PopLyricsStyle {
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        val defaults = PopLyricsStyle()
        if (prefs.getInt(KEY + "defaults_revision", 0) < 2) {
            // Replace old named presets with the new direct defaults; preserve explicitly edited values.
            if (prefs.getString(KEY + "preset", null) != "custom") {
                save(context, defaults)
                return defaults
            }
            prefs.edit().putInt(KEY + "defaults_revision", 2).apply()
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
            prefs.getInt(KEY + "text_hold", defaults.textHoldMs), prefs.getInt(KEY + "passed_opacity", defaults.passedOpacityPercent)).normalized()
    }
    fun save(context: Context, value: PopLyricsStyle) {
        val style = value.normalized()
        context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY + "defaults_revision", 2)
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
            .putInt(KEY + "text_hold", style.textHoldMs).putInt(KEY + "passed_opacity", style.passedOpacityPercent).apply()
    }
}
