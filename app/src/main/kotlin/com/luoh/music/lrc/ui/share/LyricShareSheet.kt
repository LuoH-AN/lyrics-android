package com.luoh.music.lrc.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.R
import com.luoh.music.lrc.ui.agora.SmoothBottomSheet
import com.luoh.music.lrc.ui.agora.SmoothBottomSheetValue
import com.luoh.music.lrc.ui.agora.rememberSmoothBottomSheetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LyricShareSheet(draft: LyricShareDraft, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberSmoothBottomSheetState()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = draft.initialIndex.coerceIn(draft.lines.indices))
    val previewScroll = rememberScrollState()
    var selected by remember(draft) { mutableStateOf(setOf(draft.initialIndex.coerceIn(draft.lines.indices))) }
    var translated by remember(draft) { mutableStateOf(draft.lines.any { it.translation.isNotBlank() }) }
    var preview by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<Bitmap?>(null) }
    var message by remember { mutableStateOf("") }
    val lines = remember(selected, draft) { LyricShareCard.selectedLines(draft, selected) }
    val fits = remember(lines, translated) { LyricShareCard.fits(lines, translated) }
    val working = busy || pendingSave != null

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val savedBitmap = pendingSave
        pendingSave = null
        if (uri != null && savedBitmap != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { LyricShareCard.save(context, savedBitmap, uri) }
                    message = context.getString(R.string.image_saved)
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { message = context.getString(R.string.image_save_location_failed) }
                finally { busy = false }
            }
        }
    }

    LaunchedEffect(draft) { sheet.value = SmoothBottomSheetValue.Expanded }
    LaunchedEffect(preview, lines, translated) {
        bitmap = null
        if (!preview || !fits) return@LaunchedEffect
        try {
            bitmap = withContext(Dispatchers.Default) { LyricShareCard.render(context, draft, lines, translated) }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { message = context.getString(R.string.lyric_card_create_failed) }
    }

    fun copyExcerpt() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.lyric_excerpt), LyricShareCard.excerpt(lines, translated)))
        message = context.getString(R.string.selected_lyrics_copied)
    }

    SmoothBottomSheet(state = sheet, onDismissRequest = onDismiss, onBackRequest = { working },
        contentAtTop = { if (preview) !previewScroll.canScrollBackward else !list.canScrollBackward },
        header = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (preview) context.getString(R.string.share_card) else context.getString(R.string.lyric_excerpt), style = MaterialTheme.typography.titleLarge)
                    Text(draft.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(enabled = !working, onClick = { sheet.requestDismiss() }) { Text(context.getString(R.string.close)) }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (preview) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !working, onClick = { preview = false; message = "" }) { Text(context.getString(R.string.select_lines_again)) }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val rendered = bitmap
                    if (rendered != null) Column(Modifier.fillMaxSize().verticalScroll(previewScroll)) {
                        Image(rendered.asImageBitmap(), context.getString(R.string.lyric_card_preview),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).testTag("lyric_share_preview"))
                    } else if (fits) CircularProgressIndicator()
                }
            } else {
                Text(context.getString(R.string.lyric_share_selection_count, selected.size, LyricShareCard.MAX_LINES),
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("lyric_share_lines"),
                    contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(draft.lines) { index, line ->
                        val checked = index in selected
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).toggleable(
                            value = checked, role = Role.Checkbox, enabled = !working,
                            onValueChange = { value ->
                                if (value && selected.size >= LyricShareCard.MAX_LINES) message = context.getString(R.string.select_up_to_6_lines)
                                else { selected = if (value) selected + index else selected - index; message = "" }
                            }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(line.text, style = MaterialTheme.typography.bodyLarge)
                                if (translated && line.translation.isNotBlank()) Text(line.translation,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (draft.lines.any { it.translation.isNotBlank() }) ShareOption(context.getString(R.string.include_translation), translated, !working) { translated = it }
            val status = if (!fits) {
                if (selected.isEmpty()) context.getString(R.string.select_at_least_one_line) else context.getString(R.string.lyric_share_selection_too_long)
            } else message
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled = fits && !working, onClick = ::copyExcerpt, modifier = Modifier.weight(1f)) { Text(context.getString(R.string.copy_lyrics)) }
                if (!preview) Button(enabled = fits && !working, onClick = { message = ""; preview = true },
                    modifier = Modifier.weight(1f)) { Text(context.getString(R.string.preview_card)) }
                else Button(enabled = bitmap != null && !working, onClick = {
                    val image = bitmap ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            val intent = withContext(Dispatchers.IO) { LyricShareCard.shareIntent(context, image) }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_lyric_card)))
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { message = context.getString(R.string.image_share_failed) }
                        finally { busy = false }
                    }
                }, modifier = Modifier.weight(1f)) { Text(context.getString(R.string.share_image)) }
            }
            if (preview) OutlinedButton(enabled = bitmap != null && !working, onClick = {
                pendingSave = bitmap
                val filename = draft.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(70).ifBlank { context.getString(R.string.lyric_card) }
                saveLauncher.launch("$filename.png")
            }, modifier = Modifier.fillMaxWidth()) { Text(if (working) context.getString(R.string.working) else context.getString(R.string.save_image)) }
        }
    }
}

@Composable
private fun ShareOption(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
