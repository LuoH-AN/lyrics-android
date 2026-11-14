package com.luoh.music.lrc

import android.content.Context

/** Independent native visual settings; presets are editable, never coupled to scrolling mode. */
internal data class PopLyricsStyle(
    val preset: String = "flow",
    val reveal: String = "word",
    val layout: String = "staggered",
    val entrance: String = "scatter",
    val entranceMs: Int = 600,
    val lookaheadMs: Int = 150,
    val travelPercent: Int = 100,
    val activeScalePercent: Int = 140,
    val rotationDegrees: Int = 20,
    val camera: String = "follow",
    val cameraPercent: Int = 100,
    val breathing: Boolean = true,
    val transition: String = "slide",
) {
    fun normalized() = copy(
        preset = preset.takeIf { it in listOf("calm", "flow", "dynamic", "custom") } ?: "flow",
        reveal = if (reveal == "character") reveal else "word",
        layout = layout.takeIf { it in listOf("staggered", "centered", "stairs") } ?: "staggered",
        entrance = entrance.takeIf { it in listOf("scatter", "rise", "zoom") } ?: "scatter",
        entranceMs = entranceMs.coerceIn(200, 1000), lookaheadMs = lookaheadMs.coerceIn(0, 250),
        travelPercent = travelPercent.coerceIn(0, 200), activeScalePercent = activeScalePercent.coerceIn(100, 180),
        rotationDegrees = rotationDegrees.coerceIn(0, 30),
        camera = camera.takeIf { it in listOf("off", "drift", "follow") } ?: "follow",
        cameraPercent = cameraPercent.coerceIn(0, 200),
        transition = transition.takeIf { it in listOf("fade", "slide", "zoom") } ?: "slide",
    )
    val maxWordScale get() = activeScalePercent / 100f + .06f
    val continuousCameraMotion get() = camera != "off" && cameraPercent > 0 && breathing
    val frameIntervalNs get() = if (continuousCameraMotion) 50_000_000L else PopLyricMotion.FRAME_INTERVAL_NS

    companion object {
        fun preset(name: String) = when (name) {
            "calm" -> PopLyricsStyle(preset = "calm", layout = "centered", entrance = "rise", entranceMs = 800,
                travelPercent = 45, activeScalePercent = 115, rotationDegrees = 0, camera = "drift",
                cameraPercent = 35, transition = "fade")
            "dynamic" -> PopLyricsStyle(preset = "dynamic", reveal = "character", layout = "stairs", entranceMs = 450,
                travelPercent = 160, activeScalePercent = 165, rotationDegrees = 28, cameraPercent = 160, transition = "zoom")
            else -> PopLyricsStyle()
        }
    }
}

internal object PopLyricsPrefs {
    private const val KEY = "pop_"
    fun style(context: Context): PopLyricsStyle {
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        val defaults = PopLyricsStyle()
        fun string(name: String, fallback: String) = prefs.getString(KEY + name, fallback) ?: fallback
        return PopLyricsStyle(string("preset", defaults.preset), string("reveal", defaults.reveal),
            string("layout", defaults.layout), string("entrance", defaults.entrance),
            prefs.getInt(KEY + "entrance_ms", defaults.entranceMs), prefs.getInt(KEY + "lookahead_ms", defaults.lookaheadMs),
            prefs.getInt(KEY + "travel", defaults.travelPercent), prefs.getInt(KEY + "scale", defaults.activeScalePercent),
            prefs.getInt(KEY + "rotation", defaults.rotationDegrees), string("camera", defaults.camera),
            prefs.getInt(KEY + "camera_amount", defaults.cameraPercent), prefs.getBoolean(KEY + "breathing", defaults.breathing),
            string("transition", defaults.transition)).normalized()
    }
    fun save(context: Context, value: PopLyricsStyle) {
        val style = value.normalized()
        context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY + "preset", style.preset).putString(KEY + "reveal", style.reveal)
            .putString(KEY + "layout", style.layout).putString(KEY + "entrance", style.entrance)
            .putInt(KEY + "entrance_ms", style.entranceMs).putInt(KEY + "lookahead_ms", style.lookaheadMs)
            .putInt(KEY + "travel", style.travelPercent).putInt(KEY + "scale", style.activeScalePercent)
            .putInt(KEY + "rotation", style.rotationDegrees).putString(KEY + "camera", style.camera)
            .putInt(KEY + "camera_amount", style.cameraPercent).putBoolean(KEY + "breathing", style.breathing)
            .putString(KEY + "transition", style.transition).apply()
    }
}
