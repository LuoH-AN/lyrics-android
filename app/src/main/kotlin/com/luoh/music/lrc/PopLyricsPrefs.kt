package com.luoh.music.lrc

import android.content.Context
import android.content.SharedPreferences

internal object PopLyricsPrefs {
    private const val KEY = "pop_"
    fun style(context: Context): PopLyricsStyle {
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        removeLegacyPresentation(prefs)
        val defaults = PopLyricsStyle()
        if (prefs.getInt(KEY + "defaults_revision", 0) < 3 && prefs.getString(KEY + "preset", null) != "custom") {
            // Apply this motion overhaul once on upgrade; subsequent edits remain user-owned.
            save(context, defaults)
            return defaults
        }
        if (prefs.getInt(KEY + "phrase_revision", 0) < 1) {
            // Only shorten the previous default; preserve other edits and saved snapshots.
            val edit = prefs.edit().putInt(KEY + "phrase_revision", 1)
            if (prefs.getInt(KEY + "page_length", 10) == 10) edit.putInt(KEY + "page_length", defaults.pageLength)
            edit.apply()
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
            prefs.getBoolean(KEY + "paginate", defaults.paginate), prefs.getInt(KEY + "page_length", defaults.pageLength),
            prefs.getInt(KEY + "sustain_shake", defaults.sustainShakePercent),
            pushDirection = string("push_direction", defaults.pushDirection),
            flowTransitions = prefs.getBoolean(KEY + "flow_transitions", defaults.flowTransitions),
            motionStrength = string("motion_strength", defaults.motionStrength),
            adaptiveEmphasis = prefs.getBoolean(KEY + "adaptive_emphasis", defaults.adaptiveEmphasis),
            coordinateMotion = prefs.getBoolean(KEY + "coordinate_motion", defaults.coordinateMotion),
            stageAnchorPercent = prefs.getInt(KEY + "stage_anchor", defaults.stageAnchorPercent),
            stagePaddingDp = prefs.getInt(KEY + "stage_padding", defaults.stagePaddingDp),
            shortPhraseScalePercent = prefs.getInt(KEY + "short_phrase_scale", defaults.shortPhraseScalePercent),
            translationGapDp = prefs.getInt(KEY + "translation_gap", defaults.translationGapDp),
            translationMaxLines = prefs.getInt(KEY + "translation_max_lines", defaults.translationMaxLines),
            stageFillPercent = prefs.getInt(KEY + "stage_fill", defaults.stageFillPercent),
            sizingMode = string("sizing_mode", defaults.sizingMode),
            classicScalePercent = prefs.getInt(KEY + "classic_scale", defaults.classicScalePercent),
        ).normalized()
    }
    fun save(context: Context, value: PopLyricsStyle) {
        val style = value.normalized()
        val prefs = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
        removeLegacyPresentation(prefs)
        prefs.edit()
            .putInt(KEY + "defaults_revision", 3)
            .putInt(KEY + "phrase_revision", 1)
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
            .putBoolean(KEY + "paginate", style.paginate).putInt(KEY + "page_length", style.pageLength)
            .putInt(KEY + "sustain_shake", style.sustainShakePercent)
            .putString(KEY + "push_direction", style.pushDirection)
            .putBoolean(KEY + "flow_transitions", style.flowTransitions)
            .putString(KEY + "motion_strength", style.motionStrength)
            .putBoolean(KEY + "adaptive_emphasis", style.adaptiveEmphasis)
            .putBoolean(KEY + "coordinate_motion", style.coordinateMotion)
            .putInt(KEY + "stage_anchor", style.stageAnchorPercent)
            .putInt(KEY + "stage_padding", style.stagePaddingDp)
            .putInt(KEY + "short_phrase_scale", style.shortPhraseScalePercent)
            .putInt(KEY + "translation_gap", style.translationGapDp)
            .putInt(KEY + "translation_max_lines", style.translationMaxLines)
            .putInt(KEY + "stage_fill", style.stageFillPercent)
            .putString(KEY + "sizing_mode", style.sizingMode)
            .putInt(KEY + "classic_scale", style.classicScalePercent).apply()
    }

    private fun removeLegacyPresentation(prefs: SharedPreferences) {
        val keys = prefs.all.keys.filter { it == KEY + "presentation" || it.startsWith(KEY + "stack_") }
        if (keys.isNotEmpty()) prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }
}
