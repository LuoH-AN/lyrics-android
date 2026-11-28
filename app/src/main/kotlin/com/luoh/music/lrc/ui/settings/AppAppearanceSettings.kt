package com.luoh.music.lrc.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.R
import com.luoh.music.lrc.ui.agora.ColorSchemePreset
import com.luoh.music.lrc.ui.agora.SchemeStyle
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.agora.ThemeMode
import com.luoh.music.lrc.ui.icons.filled.ColorLens
import com.luoh.music.lrc.ui.icons.filled.Contrast
import com.luoh.music.lrc.ui.icons.filled.Palette
import com.luoh.music.lrc.ui.icons.filled.SettingsBrightness
import com.luoh.music.lrc.ui.icons.filled.Style
import com.luoh.music.lrc.ui.theme.AccentPrefs
import com.luoh.music.lrc.ui.theme.ThemePrefs

@Composable
internal fun AppAppearanceSettings(context: Context, prefs: SharedPreferences, revision: Int) {
    val appearance = remember(prefs, revision) { ThemePrefs.read(prefs) }
    val theme = when (appearance.mode) {
        ThemeMode.LIGHT -> ThemePrefs.LIGHT
        ThemeMode.DARK -> ThemePrefs.DARK
        ThemeMode.FOLLOW_DEVICE -> ThemePrefs.FOLLOW
    }
    val accent = remember(revision) { AccentPrefs.customColor(context)?.let(::settingsHexColor) }
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicActive = dynamicSupported && appearance.dynamicColor
    val presets = listOf(
        ColorSchemePreset.OCEAN to context.getString(R.string.ocean), ColorSchemePreset.MIDNIGHT to context.getString(R.string.midnight),
        ColorSchemePreset.NORDIC to context.getString(R.string.nordic), ColorSchemePreset.FOREST to context.getString(R.string.forest),
        ColorSchemePreset.SUNSET to context.getString(R.string.sunset), ColorSchemePreset.ROSE to context.getString(R.string.rose),
        ColorSchemePreset.LAVENDER to context.getString(R.string.lavender), ColorSchemePreset.SLATE to context.getString(R.string.slate),
    )
    val styles = listOf(
        SchemeStyle.NEUTRAL to context.getString(R.string.neutral), SchemeStyle.TONAL_SPOT to context.getString(R.string.soft),
        SchemeStyle.EXPRESSIVE to context.getString(R.string.expressive), SchemeStyle.VIBRANT to context.getString(R.string.vibrant),
        SchemeStyle.MONOCHROME to context.getString(R.string.monochrome),
    )
    val presetLabel = presets.first { it.first == appearance.preset }.second
    val primary = MaterialTheme.colorScheme.primary
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(title = context.getString(R.string.theme_and_colors), items = listOf(
        {
            SettingsChoiceItem(context.getString(R.string.theme), theme, listOf(ThemePrefs.FOLLOW to context.getString(R.string.follow_system), ThemePrefs.LIGHT to context.getString(R.string.light), ThemePrefs.DARK to context.getString(R.string.dark)), Icons.Default.SettingsBrightness) {
                prefs.edit().putString(ThemePrefs.KEY, it).apply()
            }
        },
        {
            SettingsSwitchItem("AMOLED", context.getString(R.string.amoled_description),
                Icons.Default.Contrast, appearance.amoled) {
                prefs.edit().putBoolean(ThemePrefs.KEY_AMOLED, it).apply()
            }
        },
        {
            SettingsSwitchItem(context.getString(R.string.dynamic_color), context.getString(R.string.dynamic_color_description),
                Icons.Default.ColorLens, appearance.dynamicColor, enabled = dynamicSupported) {
                prefs.edit().putBoolean(ThemePrefs.KEY_DYNAMIC_COLOR, it).apply()
            }
        },
        {
            SettingsChoiceItem(context.getString(R.string.color_scheme), if (accent != null) "custom" else appearance.preset.name,
                (if (accent != null) listOf("custom" to context.getString(R.string.custom)) else emptyList()) +
                    presets.map { it.first.name to it.second },
                Icons.Default.Palette, enabled = !dynamicActive) { value ->
                if (value != "custom") {
                    prefs.edit().putString(ThemePrefs.KEY_COLOR_SCHEME, value).remove(AccentPrefs.KEY).apply()
                    settingsOverlayAction(context, LyricsOverlayService.ACTION_REFRESH_ACCENT)
                }
            }
        },
        {
            SettingsChoiceItem(context.getString(R.string.scheme_style), appearance.style.name, styles.map { it.first.name to it.second },
                Icons.Default.Style, enabled = !dynamicActive) {
                prefs.edit().putString(ThemePrefs.KEY_SCHEME_STYLE, it).apply()
            }
        },
        {
            SettingsItem(
                headlineContent = { Text(context.getString(R.string.accent_color)) },
                supportingContent = { Text(if (dynamicActive) context.getString(R.string.follow_dynamic_color) else accent ?: presetLabel) },
                leadingContent = { SettingsLeadingIcon(Icons.Default.Palette) },
                trailingContent = { Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) },
                modifier = Modifier.alpha(if (dynamicActive) 0.38f else 1f)
                    .clickable(enabled = !dynamicActive) { showColorPicker = true },
            )
        },
    ))
    if (showColorPicker) SettingsColorDialog(
        title = context.getString(R.string.accent_color),
        initialColor = accent ?: settingsHexColor(primary.toArgb()),
        onDismiss = { showColorPicker = false },
        onReset = {
            AccentPrefs.clear(context)
            settingsOverlayAction(context, LyricsOverlayService.ACTION_REFRESH_ACCENT)
        },
        onApply = {
            AccentPrefs.set(context, it)
            settingsOverlayAction(context, LyricsOverlayService.ACTION_REFRESH_ACCENT)
        },
    )
}
