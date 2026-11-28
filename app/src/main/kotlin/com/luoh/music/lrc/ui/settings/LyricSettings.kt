package com.luoh.music.lrc.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.R
import com.luoh.music.lrc.data.lyrics.LyricOffsetMemory
import com.luoh.music.lrc.overlay.LyricSyncText
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.icons.filled.Link
import com.luoh.music.lrc.ui.icons.filled.Lyrics
import com.luoh.music.lrc.ui.icons.filled.Sync
import com.luoh.music.lrc.ui.icons.filled.Timeline
import com.luoh.music.lrc.ui.icons.filled.Translate
import com.luoh.music.lrc.ui.navigation.ComposeRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun LyricsSyncSettings(context: Context, prefs: SharedPreferences, revision: Int, onNavigate: (ComposeRoute) -> Unit) {
    val memory = remember(prefs) { LyricOffsetMemory(prefs) }
    val activeSong = remember(revision) { memory.activeSong() }
    val songKey = activeSong?.key
    val available = activeSong != null
    var offset by remember(songKey) { mutableIntStateOf(activeSong?.let(memory::read) ?: 0) }
    var pendingWrites by remember(songKey) { mutableIntStateOf(0) }
    val screenActive = LocalSettingsScrollRetentionActive.current
    LaunchedEffect(screenActive) { if (!screenActive) pendingWrites = 0 }
    LaunchedEffect(revision, songKey) {
        if (pendingWrites == 0) offset = activeSong?.let(memory::read) ?: 0
    }
    val scope = rememberSettingsBusinessScope()
    val offsetWrites = remember { Mutex() }
    fun saveOffset(value: Int) {
        val song = activeSong ?: return
        val normalized = value.coerceIn(LyricOffsetMemory.MIN_MS, LyricOffsetMemory.MAX_MS)
        offset = normalized
        pendingWrites++
        memory.writeImmediateOffset(song, normalized)
        scope.launch {
            try {
                offsetWrites.withLock {
                    // Superseded values need no extra JSON/index work or service refresh.
                    if (normalized == offset) {
                        withContext(Dispatchers.IO) {
                            memory.save(song, normalized)
                        }
                        settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_LYRIC_OFFSET) {
                            putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MS, normalized)
                            putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MEMORY_KEY, song.key)
                        }
                    }
                }
            } finally {
                pendingWrites = (pendingWrites - 1).coerceAtLeast(0)
            }
        }
    }
    SettingsGroup(title = context.getString(R.string.current_lyrics), items = listOf({
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            SettingsItem(
                headlineContent = { Text(LyricSyncText.format(context, offset)) },
                supportingContent = { Text(if (available) context.getString(R.string.sync_step_description) else context.getString(R.string.sync_timing_unavailable)) },
                leadingContent = { SettingsLeadingIcon(Icons.Default.Sync) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { saveOffset(offset - 100) }, enabled = available && offset > LyricOffsetMemory.MIN_MS,
                    modifier = Modifier.weight(1f)) { Text(context.getString(R.string.later)) }
                TextButton(onClick = { saveOffset(0) }, enabled = available && offset != 0,
                    modifier = Modifier.weight(1f)) { Text(context.getString(R.string.reset)) }
                TextButton(onClick = { saveOffset(offset + 100) }, enabled = available && offset < LyricOffsetMemory.MAX_MS,
                    modifier = Modifier.weight(1f)) { Text(context.getString(R.string.earlier)) }
            }
        }
    }))
    SettingsGroup(title = context.getString(R.string.saved_sync_offsets), items = listOf({
        SettingsNavigationItem(context.getString(R.string.manage_saved_sync_offsets), context.getString(R.string.manage_timing_adjustments_for_individual_songs), Icons.Default.Timeline) { onNavigate(ComposeRoute("offsets")) }
    }))
}

@Composable
internal fun LyricsContentSettings(context: Context, prefs: SharedPreferences, revision: Int, onNavigate: (ComposeRoute) -> Unit) {
    val translation = remember(revision) {
        prefs.getString(LyricsOverlayService.PREF_TRANSLATION_MODE, LyricsOverlayService.TRANSLATION_BILINGUAL).orEmpty()
    }
    SettingsGroup(title = context.getString(R.string.lyrics_display), items = listOf({
        SettingsChoiceItem(context.getString(R.string.translation_display), translation, listOf(
            LyricsOverlayService.TRANSLATION_ORIGINAL to context.getString(R.string.original),
            LyricsOverlayService.TRANSLATION_BILINGUAL to context.getString(R.string.original_and_translation),
            LyricsOverlayService.TRANSLATION_TRANSLATED to context.getString(R.string.translation),
        ), Icons.Default.Translate) { value ->
            prefs.edit().putString(LyricsOverlayService.PREF_TRANSLATION_MODE, value).apply()
            settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_TRANSLATION_MODE) { putExtra(LyricsOverlayService.EXTRA_TRANSLATION_MODE, value) }
        }
    }))
    SettingsGroup(title = context.getString(R.string.manage_lyrics), items = listOf(
        { SettingsNavigationItem(context.getString(R.string.supplemental_translation), context.getString(R.string.translation_services_and_offline_language_packs), Icons.Default.Translate) { onNavigate(ComposeRoute("translation")) } },
        { SettingsNavigationItem(context.getString(R.string.custom_lyrics), context.getString(R.string.import_and_edit_local_lyrics), Icons.Default.Lyrics) { onNavigate(ComposeRoute("custom")) } },
        { SettingsNavigationItem(context.getString(R.string.edit_cached_lyrics), context.getString(R.string.correct_cached_lyrics_and_translations), Icons.Default.Edit) { onNavigate(ComposeRoute("cached")) } },
        { SettingsNavigationItem(context.getString(R.string.lyrics_sources), context.getString(R.string.match_history_and_lyrics_versions), Icons.Default.Link) { onNavigate(ComposeRoute("sources")) } },
    ))
}
