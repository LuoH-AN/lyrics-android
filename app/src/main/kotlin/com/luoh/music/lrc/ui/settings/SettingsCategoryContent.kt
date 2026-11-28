package com.luoh.music.lrc.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.R
import com.luoh.music.lrc.ui.agora.CollapsingSettingsScaffold
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.home.HomeLyricsPrefs
import com.luoh.music.lrc.ui.home.HomeMotionPrefs
import com.luoh.music.lrc.ui.home.pop.PopLyricPreview
import com.luoh.music.lrc.ui.home.pop.PopLyricsPrefs
import com.luoh.music.lrc.ui.navigation.ComposeRoute
import com.luoh.music.lrc.ui.theme.ThemePrefs

@Composable
internal fun SettingsCategoryContent(
    category: String,
    onNavigate: (ComposeRoute) -> Unit,
    onBack: () -> Unit,
) {
    val normalizedCategory = category.uppercase(java.util.Locale.ROOT)
    val context = LocalContext.current
    val overlayPrefs = remember(context) {
        context.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val appPrefs = remember(context) {
        context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE)
    }
    val alignmentPrefs = remember(context) {
        context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
    }
    val motionPrefs = remember(context) {
        context.getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE)
    }
    val revision = settingsPreferenceRevision(overlayPrefs, appPrefs, alignmentPrefs, motionPrefs)
    var previewPinned by rememberSaveable(category) { mutableStateOf(false) }
    val canPreview = normalizedCategory == "HOME" && HomeLyricsPrefs.displayMode(context) == HomeLyricsPrefs.POP
    // Reading this state keeps business values current when the service or another page changes them.
    val title = remember(normalizedCategory) {
        when (normalizedCategory) {
            "ACCESS" -> context.getString(R.string.permissions_and_controls)
            "OVERLAY" -> context.getString(R.string.lyrics_overlay_appearance)
            "SYNC" -> context.getString(R.string.lyrics_sync)
            "LYRICS" -> context.getString(R.string.lyrics_and_translation)
            "APPEARANCE" -> context.getString(R.string.app_appearance)
            "HOME" -> context.getString(R.string.home_screen_lyrics)
            "MOTION" -> context.getString(R.string.scrolling_animation)
            "ABOUT" -> context.getString(R.string.about)
            else -> context.getString(R.string.settings)
        }
    }
    CollapsingSettingsScaffold(title = title, onBack = onBack, floatingActionButton = {
        if (canPreview && previewPinned) Surface(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp, shadowElevation = 4.dp) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(context.getString(R.string.preview), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { previewPinned = false }) { Text(context.getString(R.string.collapse)) }
                }
                PopLyricPreview(PopLyricsPrefs.style(context), HomeLyricsPrefs.appearance(context), compact = true)
            }
        }
    }) {
        when (normalizedCategory) {
            "ACCESS" -> DesktopAccessSettings(context)
            "OVERLAY" -> DesktopAppearanceSettings(context, overlayPrefs, revision)
            "SYNC" -> LyricsSyncSettings(context, overlayPrefs, revision, onNavigate)
            "LYRICS" -> LyricsContentSettings(context, overlayPrefs, revision, onNavigate)
            "APPEARANCE" -> AppAppearanceSettings(context, appPrefs, revision)
            "HOME" -> HomeAlignmentSettings(context, revision, previewPinned) { previewPinned = true }
            "MOTION" -> HomeMotionSettings(context, revision)
            "ABOUT" -> AboutSettings(context, onNavigate)
        }
        if (canPreview && previewPinned) Spacer(Modifier.height(260.dp))
    }
}

@Composable
internal fun settingsPreferenceRevision(vararg prefs: SharedPreferences): Int {
    var revision by remember { mutableIntStateOf(0) }
    val active = LocalSettingsScrollRetentionActive.current
    DisposableEffect(prefs.toList(), active) {
        if (!active) return@DisposableEffect onDispose { }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.forEach { it.registerOnSharedPreferenceChangeListener(listener) }
        revision++
        onDispose { prefs.forEach { it.unregisterOnSharedPreferenceChangeListener(listener) } }
    }
    return revision
}
