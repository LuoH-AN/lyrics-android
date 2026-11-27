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
    val context = LocalContext.current.applicationContext
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
    CollapsingSettingsLazyScaffold(title = "同步记忆", onBack = onBack) {
        item(key = "summary") {
            SettingsGroup("同步调整", items = listOf({
                Column {
                    SettingsItem(headlineContent = { Text(if (!loaded) "正在读取…" else if (entries.isEmpty()) "暂无同步记忆" else "已保存 ${entries.size} 条同步记忆") })
                    if (entries.isNotEmpty()) LibraryAction("全部重置") {
                        confirmation = LibraryConfirmation("重置全部同步调整？", "所有歌曲和歌词源都会恢复原始同步时间，此操作无法撤销。", "确认重置") {
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
                SettingsItem(headlineContent = { Text(entry.title.ifBlank { "未知歌曲" }) },
                    supportingContent = { Text(listOf(entry.artist, "${entry.source.ifBlank { "歌词源" }} · ${LyricSyncText.format(entry.offsetMs)}").filter { it.isNotBlank() }.joinToString("\n")) },
                    trailingContent = { TextButton(onClick = {
                        confirmation = LibraryConfirmation("删除同步记忆？", "《${entry.title}》在 ${entry.source} 的歌词将恢复原始同步时间。", "删除记忆") {
                            scope.launch {
                                withContext(Dispatchers.IO) { memory.delete(entry.key) }
                                if (LyricsOverlayService.isRunning) context.startService(Intent(context, LyricsOverlayService::class.java)
                                    .setAction(LyricsOverlayService.ACTION_DELETE_LYRIC_OFFSET_MEMORY).putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MEMORY_KEY, entry.key))
                            }
                        }
                    }) { Text("删除") } })
            }
        }
    }
    LibraryConfirmationDialog(confirmation) { confirmation = null }
}
