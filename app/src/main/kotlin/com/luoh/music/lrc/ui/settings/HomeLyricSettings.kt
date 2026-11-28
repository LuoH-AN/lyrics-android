package com.luoh.music.lrc.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.R
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.home.HomeLyricAppearance
import com.luoh.music.lrc.ui.home.HomeLyricsPrefs
import com.luoh.music.lrc.ui.home.pop.PopLyricPreview
import com.luoh.music.lrc.ui.home.pop.PopLyricSchemes
import com.luoh.music.lrc.ui.home.pop.PopLyricsPrefs
import com.luoh.music.lrc.ui.home.pop.PopLyricsStyle
import com.luoh.music.lrc.ui.icons.filled.BlurOn
import com.luoh.music.lrc.ui.icons.filled.Contrast
import com.luoh.music.lrc.ui.icons.filled.FormatAlignLeft
import com.luoh.music.lrc.ui.icons.filled.FormatSize
import com.luoh.music.lrc.ui.icons.filled.Layers
import com.luoh.music.lrc.ui.icons.filled.Lyrics
import com.luoh.music.lrc.ui.icons.filled.MotionPhotosOff
import com.luoh.music.lrc.ui.icons.filled.SettingsBrightness
import com.luoh.music.lrc.ui.icons.filled.Speed
import com.luoh.music.lrc.ui.icons.filled.Style
import com.luoh.music.lrc.ui.icons.filled.Sync
import com.luoh.music.lrc.ui.icons.filled.TextFields
import com.luoh.music.lrc.ui.icons.filled.Timeline
import com.luoh.music.lrc.ui.icons.filled.Translate

@Composable
internal fun HomeAlignmentSettings(context: Context, revision: Int, previewPinned: Boolean = false,
    onPinPreview: () -> Unit = {}) {
    val alignment = remember(revision) { HomeLyricsPrefs.alignment(context) }
    val albumBackground = remember(revision) { HomeLyricsPrefs.albumBackground(context) }
    val appearance = remember(revision) { HomeLyricsPrefs.appearance(context) }
    val displayMode = remember(revision) { HomeLyricsPrefs.displayMode(context) }
    val keepAwake = remember(revision) { HomeLyricsPrefs.immersiveKeepAwake(context) }
    fun saveStyle(change: (HomeLyricAppearance) -> HomeLyricAppearance) {
        HomeLyricsPrefs.saveAppearance(context, change(HomeLyricsPrefs.appearance(context)))
    }
    SettingsGroup(title = context.getString(R.string.display_mode), items = listOf({
        SettingsChoiceItem(context.getString(R.string.lyrics_display_mode), displayMode,
            listOf(HomeLyricsPrefs.SCROLL to context.getString(R.string.scrolling), HomeLyricsPrefs.POP to context.getString(R.string.animated)), Icons.Default.Lyrics) {
            HomeLyricsPrefs.saveDisplayMode(context, it)
        }
    }))
    if (displayMode == HomeLyricsPrefs.POP) PopLyricSettings(context, revision, previewPinned, onPinPreview)
    else ScrollingLyricSettings(context, revision, appearance)
    SettingsGroup(title = context.getString(R.string.screen), items = listOf({
        SettingsSwitchItem(context.getString(R.string.keep_screen_on_during_playback), null,
            Icons.Default.SettingsBrightness, keepAwake) { HomeLyricsPrefs.saveImmersiveKeepAwake(context, it) }
    }))
    SettingsGroup(title = context.getString(R.string.background), items = listOf({
        SettingsSwitchItem(context.getString(R.string.album_art_background), context.getString(R.string.show_blurred_album_art), Icons.Default.BlurOn, albumBackground) {
            HomeLyricsPrefs.saveAlbumBackground(context, it)
        }
    }))
    SettingsGroup(title = context.getString(R.string.lyrics_layout), items = listOf({
        SettingsChoiceItem(context.getString(R.string.alignment), alignment, listOf(HomeLyricsPrefs.LEFT to context.getString(R.string.left), HomeLyricsPrefs.CENTER to context.getString(R.string.centered), HomeLyricsPrefs.RIGHT to context.getString(R.string.right)),
            Icons.Default.FormatAlignLeft) { HomeLyricsPrefs.save(context, it) }
    }))
    SettingsGroup(title = context.getString(R.string.lyrics_text), items = listOf(
        { SettingsSliderItem(context.getString(R.string.original_text_size), appearance.fontSize, 16..48, unit = "sp", icon = Icons.Default.FormatSize) {
            value -> saveStyle { it.copy(fontSize = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.translation_text_size), appearance.translationSize, 12..32, unit = "sp", icon = Icons.Default.Translate) {
            value -> saveStyle { it.copy(translationSize = value) }
        } },
        { SettingsChoiceItem(context.getString(R.string.font_weight), appearance.fontWeight.toString(), listOf("300" to context.getString(R.string.font_weight_light), "400" to context.getString(R.string.regular),
            "500" to context.getString(R.string.medium), "600" to context.getString(R.string.semi_bold), "700" to context.getString(R.string.bold), "800" to context.getString(R.string.extra_bold)), Icons.Default.TextFields) {
            value -> saveStyle { it.copy(fontWeight = value.toInt()) }
        } },
        { SettingsSliderItem(context.getString(R.string.line_spacing), appearance.lineSpacing, 0..28, step = 2, unit = "dp", icon = Icons.Default.FormatAlignLeft) {
            value -> saveStyle { it.copy(lineSpacing = value) }
        } },
    ))
    SettingsGroup(title = context.getString(R.string.text_preview), items = listOf({
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(context.getString(R.string.home_text_preview_line), fontSize = appearance.fontSize.sp, fontWeight = FontWeight(appearance.fontWeight))
            Text("Let the music stay with you", fontSize = appearance.translationSize.sp,
                fontWeight = FontWeight(appearance.fontWeight), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }))
}

@Composable
private fun PopLyricSettings(context: Context, revision: Int, previewPinned: Boolean = false,
    onPinPreview: () -> Unit = {}) {
    val style = remember(revision) { PopLyricsPrefs.style(context) }
    val appearance = remember(revision) { HomeLyricsPrefs.appearance(context) }
    fun save(change: (PopLyricsStyle) -> PopLyricsStyle) {
        PopLyricsPrefs.save(context, change(PopLyricsPrefs.style(context)).copy(preset = "custom"))
    }
    if (!previewPinned) SettingsGroup(title = context.getString(R.string.preview), items = listOf({ PopLyricPreview(style, appearance, onPin = onPinPreview) }))
    PopSchemeSettings(context, revision, style, appearance)
    SettingsGroup(title = context.getString(R.string.display_and_layout), items = listOf(
        { SettingsChoiceItem(context.getString(R.string.text_sizing), style.sizingMode,
            listOf("focus" to context.getString(R.string.adaptive), "classic" to context.getString(R.string.fixed)), Icons.Default.FormatSize) {
            value -> save { it.copy(sizingMode = value) }
        } },
        { if (style.sizingMode == "classic") SettingsSliderItem(context.getString(R.string.text_size_multiplier), style.classicScalePercent,
            80..200, 5, "%", Icons.Default.FormatSize) {
            value -> save { it.copy(classicScalePercent = value) }
        } },
        { SettingsChoiceItem(context.getString(R.string.reveal_unit), style.reveal, listOf("word" to context.getString(R.string.word_by_word), "character" to context.getString(R.string.character_by_character)), Icons.Default.TextFields) {
            value -> save { it.copy(reveal = value) }
        } },
        { SettingsChoiceItem(context.getString(R.string.layout), style.layout,
            listOf("staggered" to context.getString(R.string.staggered), "centered" to context.getString(R.string.centered), "stairs" to context.getString(R.string.stepped), "wave" to context.getString(R.string.wave), "random" to context.getString(R.string.random)), Icons.Default.FormatAlignLeft) {
            value -> save { it.copy(layout = value) }
        } },
    ))
    SettingsGroup(title = context.getString(R.string.paging), items = listOf(
        { SettingsSwitchItem(context.getString(R.string.split_lyrics_into_pages), context.getString(R.string.fade_out_the_previous_page_on), Icons.Default.Lyrics, style.paginate) {
            value -> save { it.copy(paginate = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.characters_per_page), style.pageLength, 4..24, 1, context.getString(R.string.characters_unit), Icons.Default.TextFields,
            style.paginate) { value -> save { it.copy(pageLength = value) } } },
    ))
    SettingsGroup(title = context.getString(R.string.layout_spacing), items = listOf(
        { SettingsSwitchItem(context.getString(R.string.group_phrases), context.getString(R.string.group_text_by_phrases_and_pauses), Icons.Default.TextFields, style.semanticGrouping) {
            value -> save { it.copy(semanticGrouping = value) }
        } },
        { SettingsSwitchItem(context.getString(R.string.dynamic_spacing), context.getString(R.string.reduce_overlapping_text), Icons.Default.FormatSize, style.dynamicSpacing) {
            value -> save { it.copy(dynamicSpacing = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.spacing_within_phrases), style.innerGapDp, 0..12, 1, "dp", Icons.Default.TextFields) {
            value -> save { it.copy(innerGapDp = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.spacing_between_phrases), style.groupGapDp, 0..24, 1, "dp", Icons.Default.TextFields) {
            value -> save { it.copy(groupGapDp = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.line_spacing_multiplier), style.rowGapPercent, 0..250, 10, "%", Icons.Default.FormatAlignLeft) {
            value -> save { it.copy(rowGapPercent = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.line_width), style.lineWidthPercent, 60..140, 5, "%", Icons.Default.FormatAlignLeft) {
            value -> save { it.copy(lineWidthPercent = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.stagger_amount), style.staggerPercent, 0..200, 10, "%", Icons.Default.Layers,
            style.layout != "centered") { value -> save { it.copy(staggerPercent = value) } } },
    ))
    SettingsGroup(title = context.getString(R.string.animation_controls), items = listOf(
        { SettingsChoiceItem(context.getString(R.string.motion_intensity), style.motionStrength,
            listOf("gentle" to context.getString(R.string.low), "balanced" to context.getString(R.string.motion_intensity_medium), "strong" to context.getString(R.string.high)), Icons.Default.Speed) {
            value -> save { it.copy(motionStrength = value) }
        } },
        { SettingsSwitchItem(context.getString(R.string.adaptive_intensity), context.getString(R.string.use_less_motion_on_fast_lines), Icons.Default.Timeline, style.adaptiveEmphasis) {
            value -> save { it.copy(adaptiveEmphasis = value) }
        } },
        { SettingsSwitchItem(context.getString(R.string.limit_combined_effects), context.getString(R.string.reduce_movement_when_effects_overlap), Icons.Default.Layers, style.coordinateMotion) {
            value -> save { it.copy(coordinateMotion = value) }
        } },
        { SettingsSwitchItem(context.getString(R.string.match_motion_direction), context.getString(R.string.use_the_camera_direction_for_transitions), Icons.Default.Sync, style.flowTransitions) {
            value -> save { it.copy(flowTransitions = value) }
        } },
    ))
    SettingsGroup(title = context.getString(R.string.visible_area), items = listOf(
        { SettingsSliderItem(context.getString(R.string.lyrics_area_size), style.stageFillPercent, 65..100, 1, "%", Icons.Default.FormatSize,
            style.sizingMode == "focus") {
            value -> save { it.copy(stageFillPercent = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.horizontal_padding), style.stagePaddingDp, 8..40, 2, "dp", Icons.Default.FormatAlignLeft) {
            value -> save { it.copy(stagePaddingDp = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.translation_spacing), style.translationGapDp, 4..28, 2, "dp", Icons.Default.Translate) {
            value -> save { it.copy(translationGapDp = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.maximum_translation_lines), style.translationMaxLines, 1..4, 1, context.getString(R.string.lines_unit), Icons.Default.Translate) {
            value -> save { it.copy(translationMaxLines = value) }
        } },
    ))
    SettingsGroup(title = context.getString(R.string.text_entrance), items = listOf(
        { SettingsChoiceItem(context.getString(R.string.entrance_effect), style.entrance,
            listOf("scatter" to context.getString(R.string.multi_directional), "rise" to context.getString(R.string.rise), "zoom" to context.getString(R.string.zoom),
                "converge" to context.getString(R.string.converge), "disperse" to context.getString(R.string.disperse), "random" to context.getString(R.string.random)), Icons.Default.Layers) {
            value -> save { it.copy(entrance = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.entrance_duration), style.entranceMs, 200..1000, 50, "ms", Icons.Default.Speed) {
            value -> save { it.copy(entranceMs = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.entrance_lead_time), style.lookaheadMs, 0..250, 10, "ms", Icons.Default.Timeline) {
            value -> save { it.copy(lookaheadMs = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.entrance_travel), style.travelPercent, 0..200, 10, "%", Icons.Default.Layers) {
            value -> save { it.copy(travelPercent = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.active_word_scale), style.activeScalePercent, 100..180, 5, "%", Icons.Default.FormatSize) {
            value -> save { it.copy(activeScalePercent = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.text_rotation), style.rotationDegrees, 0..30, 1, "°", Icons.Default.Sync) {
            value -> save { it.copy(rotationDegrees = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.sustained_note_movement), style.sustainShakePercent, 0..200, 10, "%", Icons.Default.Sync) {
            value -> save { it.copy(sustainShakePercent = value) }
        } },
    ))
    SettingsGroup(title = context.getString(R.string.camera), items = listOf(
        { SettingsChoiceItem(context.getString(R.string.camera_motion), style.camera,
            listOf("off" to context.getString(R.string.fixed), "drift" to context.getString(R.string.drift), "follow" to context.getString(R.string.follow), "orbit" to context.getString(R.string.orbit)), Icons.Default.MotionPhotosOff) {
            value -> save { it.copy(camera = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.camera_movement_amount), style.cameraPercent, 0..200, 10, "%", Icons.Default.Speed,
            style.camera != "off") { value -> save { it.copy(cameraPercent = value) } } },
        { SettingsSwitchItem(context.getString(R.string.loop_zoom), null, Icons.Default.Sync, style.breathing) {
            value -> save { it.copy(breathing = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.follow_speed), style.cameraSpeedPercent, 50..180, 5, "%", Icons.Default.Speed,
            style.camera in listOf("follow", "orbit")) { value -> save { it.copy(cameraSpeedPercent = value) } } },
    ))
    SettingsGroup(title = context.getString(R.string.highlighting_and_transitions), items = listOf(
        { SettingsSliderItem(context.getString(R.string.whole_line_movement), style.rowMotionPercent, 0..100, 5, "%", Icons.Default.Layers) {
            value -> save { it.copy(rowMotionPercent = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.highlight_delay), style.textHoldMs, 0..800, 50, "ms", Icons.Default.Timeline) {
            value -> save { it.copy(textHoldMs = value) }
        } },
        { SettingsSliderItem(context.getString(R.string.sung_text_opacity), style.passedOpacityPercent, 0..100, 5, "%", Icons.Default.Contrast) {
            value -> save { it.copy(passedOpacityPercent = value) }
        } },
        { SettingsSwitchItem(context.getString(R.string.adaptive_duration), context.getString(R.string.shorten_the_entrance_on_fast_lines), Icons.Default.Speed, style.adaptiveTiming) {
            value -> save { it.copy(adaptiveTiming = value) }
        } },
        { SettingsChoiceItem(context.getString(R.string.line_transition), style.transition,
            listOf("fade" to context.getString(R.string.fade), "slide" to context.getString(R.string.slide), "zoom" to context.getString(R.string.zoom),
                "converge" to context.getString(R.string.converge), "disperse" to context.getString(R.string.disperse), "random" to context.getString(R.string.random)), Icons.Default.Layers) {
            value -> save { it.copy(transition = value) }
        } },
        { if (style.transition in listOf("slide", "random"))
            SettingsChoiceItem(context.getString(R.string.slide_direction), style.pushDirection,
                listOf("left" to context.getString(R.string.direction_left), "right" to context.getString(R.string.direction_right), "up" to context.getString(R.string.up), "down" to context.getString(R.string.down),
                    "horizontal" to context.getString(R.string.random_horizontal), "vertical" to context.getString(R.string.random_vertical), "random" to context.getString(R.string.random_direction)), Icons.Default.Layers) {
                value -> save { it.copy(pushDirection = value) }
            } },
    ))
}

@Composable
private fun PopSchemeSettings(context: Context, revision: Int, style: PopLyricsStyle, appearance: HomeLyricAppearance) {
    val saved = remember(revision) { PopLyricSchemes.saved(context) }
    val builtIns = remember(context) { PopLyricSchemes.builtIns(context) }
    val schemes = builtIns + saved
    val selectedScheme = (saved + builtIns).firstOrNull {
        it.motion.copy(preset = "custom") == style.copy(preset = "custom") && it.text == appearance }
    val selected = selectedScheme?.id ?: "custom"
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val savedRows: List<@Composable () -> Unit> = saved.map { scheme -> {
        SettingsItem(headlineContent = { Text(scheme.name) },
            modifier = Modifier.clickable { PopLyricSchemes.apply(context, scheme) },
            trailingContent = { TextButton(onClick = { PopLyricSchemes.delete(context, scheme.id) }) { Text(context.getString(R.string.delete)) } })
    } }
    SettingsGroup(title = context.getString(R.string.presets), items = listOf<@Composable () -> Unit>(
        { SettingsChoiceItem(context.getString(R.string.choose_a_preset), selected, listOf("custom" to context.getString(R.string.custom)) + schemes.map {
            it.id to it.name },
            Icons.Default.Style, showSummary = false, summary = selectedScheme?.description) {
            id -> schemes.firstOrNull { it.id == id }?.let { PopLyricSchemes.apply(context, it) } } },
        { SettingsNavigationItem(context.getString(R.string.restore_defaults), context.getString(R.string.reset_animation_and_text_settings), Icons.Default.Sync) {
            PopLyricSchemes.apply(context, PopLyricSchemes.recommended(context))
        } },
        { SettingsNavigationItem(context.getString(R.string.save_preset), icon = Icons.Default.Add) {
            name = ""; naming = true
        } },
    ) + savedRows)
    if (naming) {
        val normalized = name.trim()
        val replacing = saved.any { it.name == normalized }
        val full = !replacing && saved.size >= PopLyricSchemes.LIMIT
        AlertDialog(onDismissRequest = { naming = false }, title = { Text(if (full) context.getString(R.string.preset_limit_reached) else context.getString(R.string.save_preset)) },
            text = { Column {
                OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, singleLine = true,
                    label = { Text(context.getString(R.string.preset_name)) })
            } },
            confirmButton = { TextButton(enabled = normalized.isNotBlank() && !full, onClick = {
                if (PopLyricSchemes.save(context, normalized)) naming = false
            }) { Text(when { full -> context.getString(R.string.limit_reached); replacing -> context.getString(R.string.replace); else -> context.getString(R.string.save) }) } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(context.getString(R.string.cancel)) } })
    }
}
