package com.luoh.music.lrc.ui.library

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.R
import com.luoh.music.lrc.data.lyrics.LyricOffsetMemory
import com.luoh.music.lrc.overlay.LyricSyncText
import com.luoh.music.lrc.ui.agora.CollapsingSettingsLazyScaffold
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun OffsetMemoriesComposeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE) }
    val memory = remember(prefs) { LyricOffsetMemory(prefs) }
    val revision = preferenceRevision(prefs, setOf(LyricOffsetMemory.INDEX_KEY))
    val scope = rememberLibraryScope()
    val active = LocalSettingsScrollRetentionActive.current
    var entries by remember { mutableStateOf(emptyList<LyricOffsetMemory.Entry>()) }
    var loaded by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    LaunchedEffect(revision, active) {
        if (!active) return@LaunchedEffect
        entries = withContext(Dispatchers.IO) { memory.entries() }
        loaded = true
    }
    CollapsingSettingsLazyScaffold(title = context.getString(R.string.saved_sync_offsets), onBack = onBack) {
        item(key = "summary") {
            SettingsGroup(context.getString(R.string.sync_adjustments), items = listOf({
                Column {
                    SettingsItem(headlineContent = { Text(if (!loaded) context.getString(R.string.loading) else if (entries.isEmpty()) context.getString(R.string.no_saved_sync_offsets) else context.getString(R.string.saved_sync_offset_count, entries.size)) })
                    if (entries.isNotEmpty()) LibraryAction(context.getString(R.string.reset_all)) {
                        confirmation = LibraryConfirmation(context.getString(R.string.reset_all_sync_adjustments), context.getString(R.string.reset_all_sync_message), context.getString(R.string.confirm_reset)) {
                            scope.launch {
                                withContext(Dispatchers.IO) { memory.clear() }
                                if (LyricsOverlayService.isRunning) context.startService(Intent(context, LyricsOverlayService::class.java)
                                    .setAction(LyricsOverlayService.ACTION_CLEAR_LYRIC_OFFSET_MEMORIES))
                            }
                        }
                    }
                }
            }))
        }
        itemsIndexed(entries, key = { _, entry -> entry.key }, contentType = { _, _ -> "offset" }) { index, entry ->
            LibraryLazyGroupItem(index, entries.size) {
                SettingsItem(headlineContent = { Text(entry.title.ifBlank { context.getString(R.string.unknown_song) }) },
                    supportingContent = { Text(listOf(entry.artist, "${context.lyricSourceLabel(entry.source)} · ${LyricSyncText.format(context, entry.offsetMs)}").filter { it.isNotBlank() }.joinToString("\n")) },
                    trailingContent = { TextButton(onClick = {
                        confirmation = LibraryConfirmation(context.getString(R.string.delete_saved_sync_offset), context.getString(R.string.delete_sync_offset_message, entry.title, context.lyricSourceLabel(entry.source)), context.getString(R.string.delete_saved_entry)) {
                            scope.launch {
                                withContext(Dispatchers.IO) { memory.delete(entry.key) }
                                if (LyricsOverlayService.isRunning) context.startService(Intent(context, LyricsOverlayService::class.java)
                                    .setAction(LyricsOverlayService.ACTION_DELETE_LYRIC_OFFSET_MEMORY).putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MEMORY_KEY, entry.key))
                            }
                        }
                    }) { Text(context.getString(R.string.delete)) } })
            }
        }
    }
    LibraryConfirmationDialog(confirmation) { confirmation = null }
}
