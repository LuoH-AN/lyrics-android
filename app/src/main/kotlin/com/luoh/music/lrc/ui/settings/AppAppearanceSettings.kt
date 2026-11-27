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
        ColorSchemePreset.OCEAN to "海洋", ColorSchemePreset.MIDNIGHT to "午夜",
        ColorSchemePreset.NORDIC to "北欧", ColorSchemePreset.FOREST to "森林",
        ColorSchemePreset.SUNSET to "日落", ColorSchemePreset.ROSE to "玫瑰",
        ColorSchemePreset.LAVENDER to "薰衣草", ColorSchemePreset.SLATE to "岩灰",
    )
    val styles = listOf(
        SchemeStyle.NEUTRAL to "中性", SchemeStyle.TONAL_SPOT to "柔和",
        SchemeStyle.EXPRESSIVE to "表现力", SchemeStyle.VIBRANT to "鲜艳",
        SchemeStyle.MONOCHROME to "黑白灰",
    )
    val presetLabel = presets.first { it.first == appearance.preset }.second
    val primary = MaterialTheme.colorScheme.primary
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(title = "主题与颜色", items = listOf(
        {
            SettingsChoiceItem("主题", theme, listOf(ThemePrefs.FOLLOW to "跟随系统", ThemePrefs.LIGHT to "浅色", ThemePrefs.DARK to "深色"), Icons.Default.SettingsBrightness) {
                prefs.edit().putString(ThemePrefs.KEY, it).apply()
            }
        },
        {
            SettingsSwitchItem("AMOLED", "浅色使用纯白背景，深色使用纯黑背景",
                Icons.Default.Contrast, appearance.amoled) {
                prefs.edit().putBoolean(ThemePrefs.KEY_AMOLED, it).apply()
            }
        },
        {
            SettingsSwitchItem("动态颜色", "使用系统壁纸配色（Android 12 及以上）",
                Icons.Default.ColorLens, appearance.dynamicColor, enabled = dynamicSupported) {
                prefs.edit().putBoolean(ThemePrefs.KEY_DYNAMIC_COLOR, it).apply()
            }
        },
        {
            SettingsChoiceItem("配色方案", if (accent != null) "custom" else appearance.preset.name,
                (if (accent != null) listOf("custom" to "自定义") else emptyList()) +
                    presets.map { it.first.name to it.second },
                Icons.Default.Palette, enabled = !dynamicActive) { value ->
                if (value != "custom") {
                    prefs.edit().putString(ThemePrefs.KEY_COLOR_SCHEME, value).remove(AccentPrefs.KEY).apply()
                    settingsOverlayAction(context, LyricsOverlayService.ACTION_REFRESH_ACCENT)
                }
            }
        },
        {
            SettingsChoiceItem("方案风格", appearance.style.name, styles.map { it.first.name to it.second },
                Icons.Default.Style, enabled = !dynamicActive) {
                prefs.edit().putString(ThemePrefs.KEY_SCHEME_STYLE, it).apply()
            }
        },
        {
            SettingsItem(
                headlineContent = { Text("主调颜色") },
                supportingContent = { Text(if (dynamicActive) "跟随动态颜色" else accent ?: presetLabel) },
                leadingContent = { SettingsLeadingIcon(Icons.Default.Palette) },
                trailingContent = { Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) },
                modifier = Modifier.alpha(if (dynamicActive) 0.38f else 1f)
                    .clickable(enabled = !dynamicActive) { showColorPicker = true },
            )
        },
    ))
    if (showColorPicker) SettingsColorDialog(
        title = "主调颜色",
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
