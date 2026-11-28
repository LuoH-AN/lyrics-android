package com.luoh.music.lrc.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.R
import com.luoh.music.lrc.data.lyrics.SongSearchCorrections
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.agora.SmoothBottomSheet
import com.luoh.music.lrc.ui.agora.rememberSmoothBottomSheetState
import com.luoh.music.lrc.ui.icons.filled.Download
import com.luoh.music.lrc.ui.icons.filled.Layers
import com.luoh.music.lrc.ui.icons.filled.LibraryMusic
import com.luoh.music.lrc.ui.icons.filled.Lyrics
import com.luoh.music.lrc.ui.icons.filled.MusicNote
import com.luoh.music.lrc.ui.icons.filled.Translate

@Composable
internal fun HomeMoreSheet(state: HomePresentationState, onEnterImmersive: () -> Unit, onCorrectSong: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheet = rememberSmoothBottomSheetState()
    val scroll = rememberLazyListState()
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun select(action: () -> Unit) {
        if (sheet.dismissing || pendingAction != null) return
        pendingAction = action
        sheet.requestDismiss()
    }
    SmoothBottomSheet(
        state = sheet,
        onDismissRequest = {
            val action = pendingAction
            pendingAction = null
            onDismiss()
            action?.invoke()
        },
        contentAtTop = { !scroll.canScrollBackward },
        header = {
            Text(context.getString(R.string.more), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(state.snapshot.track.ifBlank { context.getString(R.string.lyrics_and_display_settings) }, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 16.dp))
        },
    ) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { HomeSheetOption(context.getString(R.string.immersive_mode), Icons.Default.Lyrics) { select(onEnterImmersive) } }
            if (state.document.lines.isNotEmpty()) item {
                HomeSheetOption(context.getString(R.string.select_and_share_lyrics), Icons.Default.Share) { select { state.beginSharing() } }
            }
            if (state.document.lines.isNotEmpty() && !state.custom) item {
                HomeSheetOption(context.getString(R.string.find_lyrics_again), Icons.Default.Refresh) { select { state.actions?.retryLyrics() } }
            }
            item { HomeSheetOption(when {
                state.custom -> context.getString(R.string.edit_custom_lyrics)
                state.document.lines.isNotEmpty() -> context.getString(R.string.edit_current_lyrics)
                else -> context.getString(R.string.add_custom_lyrics)
            }, Icons.Default.Edit) { select { state.actions?.editCustomLyrics() } } }
            if (state.snapshot.track.isNotBlank()) item {
                HomeSheetOption(context.getString(R.string.correct_song_title), Icons.Default.MusicNote) { select(onCorrectSong) }
            }
            item { HomeSheetOption(context.getString(R.string.manage_custom_lyrics), Icons.Default.LibraryMusic) { select { state.actions?.manageCustomLyrics() } } }
            item { HomeSheetOption(context.getString(R.string.lyrics_sources), Icons.Default.Layers) { select { state.actions?.manageLyricSources() } } }
            item { HomeSheetOption(context.getString(R.string.supplemental_translation), Icons.Default.Translate) { select { state.onTranslationSettings?.invoke() } } }
            item { HomeSheetOption(context.getString(R.string.export_lrc_lyrics), Icons.Default.Download) { select { state.actions?.exportLrc() } } }
            item { HomeSheetOption(context.getString(R.string.settings), Icons.Default.Settings) { select { state.actions?.openSettings() } } }
        }
    }
}

@Composable
internal fun SongCorrectionDialog(track: String, artist: String, onDismiss: () -> Unit,
    onSave: (String?, String) -> Unit) {
    val context = LocalContext.current
    val initial = remember(track, artist) { SongSearchCorrections.resolve(context, track, artist) }
    var title by rememberSaveable(track, artist) { mutableStateOf(initial.title.take(SongSearchCorrections.MAX_LENGTH)) }
    var searchArtist by rememberSaveable(track, artist) { mutableStateOf(initial.artist.take(SongSearchCorrections.MAX_LENGTH)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.correct_song_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(SongSearchCorrections.MAX_LENGTH) },
                    label = { Text(context.getString(R.string.song_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = searchArtist, onValueChange = { searchArtist = it.take(SongSearchCorrections.MAX_LENGTH) },
                    label = { Text(context.getString(R.string.artist)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title.trim(), searchArtist.trim()) }, enabled = title.isNotBlank()) { Text(context.getString(R.string.save)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(null, artist) }) { Text(context.getString(R.string.restore)) }
                TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun HomeSheetOption(title: String, icon: ImageVector, onClick: () -> Unit) {
    SettingsItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
    )
}
