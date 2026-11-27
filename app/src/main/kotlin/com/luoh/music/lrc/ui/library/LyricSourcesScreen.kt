package com.luoh.music.lrc.ui.library

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.data.lyrics.DirectLyricsRepository
import com.luoh.music.lrc.data.lyrics.LyricMatchMemory
import com.luoh.music.lrc.data.lyrics.SharedLyricsCache
import com.luoh.music.lrc.ui.agora.CollapsingSettingsLazyScaffold
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.navigation.ComposeRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun refreshOverlayMatches(context: Context) {
    if (LyricsOverlayService.isRunning) {
        context.startService(Intent(context, LyricsOverlayService::class.java)
            .setAction(LyricsOverlayService.ACTION_REFRESH_MATCHES))
    }
}

private data class ComposeSourceVersion(val id: String, val title: String, val artist: String, val summary: String, val preview: String, val candidate: JSONObject, val current: Boolean)

private fun sourceVersions(entry: JSONObject): List<ComposeSourceVersion> {
    val history = entry.optJSONArray("history") ?: JSONArray().put(entry.optJSONObject("candidate"))
    return (0 until history.length()).mapNotNull { index ->
        val candidate = history.optJSONObject(index) ?: return@mapNotNull null
        val seconds = candidate.optLong("duration") / 1000
        val current = !entry.optBoolean("needsReview") && candidate.optString("recordId") == entry.optJSONObject("candidate")?.optString("recordId")
        ComposeSourceVersion("${candidate.optString("recordId")}:$index",
            candidate.optString("title").ifBlank { entry.optString("title") },
            candidate.optString("artist").ifBlank { entry.optString("artist") } + " · " + candidate.optString("source"),
            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} · " + (if (candidate.optString("wordLyrics").isNotBlank()) "含逐字数据" else "普通歌词") +
                (if (candidate.optString("translatedLyrics").isNotBlank()) " · 含译文" else ""),
            candidate.optString("lyrics").lineSequence().map { it.replace(Regex("^(\\[[^]]+])+"), "").trim() }
                .filter { it.isNotBlank() }.take(16).joinToString("\n"), candidate, current)
    }
}

@Immutable
private data class ComposeSourceRow(val key: String, val title: String, val artist: String, val source: String)
private data class SourcePageSnapshot(
    val entries: List<JSONObject> = emptyList(),
    val selected: JSONObject? = null,
    val versions: List<ComposeSourceVersion> = emptyList(),
    val rows: List<ComposeSourceRow> = emptyList(),
    val songCount: Int = 0,
)

@Composable
internal fun SourcesComposeScreen(route: ComposeRoute, onNavigate: (ComposeRoute) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE) }
    val memory = remember(prefs) { LyricMatchMemory(prefs) }
    val revision = preferenceRevision(prefs, setOf(LyricMatchMemory.PREF_KEY))
    val scope = rememberLibraryScope()
    val active = LocalSettingsScrollRetentionActive.current
    val selectedKey = route.id.takeIf { it.isNotBlank() }
    var query by rememberSaveable(route.id) { mutableStateOf(route.category) }
    var snapshot by remember { mutableStateOf(SourcePageSnapshot()) }
    val entries = snapshot.entries
    val filtered = snapshot.rows
    val versions = snapshot.versions
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable(route.id) { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    val entry = snapshot.selected
    LaunchedEffect(revision, route.id, query, active) {
        if (!active) return@LaunchedEffect
        if (query.isNotBlank() && selectedKey == null) delay(120)
        val values = withContext(Dispatchers.IO) {
            val values = memory.entries()
            val selected = values.find { it.optString("key") == selectedKey }
            val rows = values.asReversed().map {
                ComposeSourceRow(it.optString("key"), it.optString("title").ifBlank { "未知歌曲" }, it.optString("artist"), it.optString("source"))
            }.filter { (it.title + " " + it.artist).contains(query.trim(), true) }
            SourcePageSnapshot(values, selected, selected?.let(::sourceVersions).orEmpty(), rows,
                values.map { it.optString("title") + "\u0000" + it.optString("artist") }.distinct().size)
        }
        snapshot = values; loaded = true
    }
    fun update(key: String, change: (JSONObject) -> Unit) {
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) {
                if (memory.update(key, change)) refreshOverlayMatches(context)
            } } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = "无法保存来源选择，请重试" }
            finally { busy = false }
        }
    }
    fun searchNext(item: JSONObject) {
        if (busy) return
        if (item.optString("title").isBlank()) { message = "旧记录缺少歌曲信息，请播放歌曲并获取歌词后重试。"; return }
        busy = true; message = ""
        val key = item.optString("key")
        val history = item.optJSONArray("history") ?: JSONArray().put(item.optJSONObject("candidate"))
        val excluded = (0 until history.length()).mapNotNull { history.optJSONObject(it)?.optString("recordId") }.filter { it.isNotBlank() }.toSet()
        scope.launch {
            try {
                val candidate = withContext(Dispatchers.IO) {
                    val repository = DirectLyricsRepository()
                    try { repository.rematch(item.optString("source"), item.optString("title"), item.optString("artist"),
                        item.optString("album"), item.optLong("duration"), excluded)?.toJson() }
                    finally { repository.close() }
                }
                val stored = withContext(Dispatchers.IO) {
                    if (candidate == null) false else memory.update(key) { latest ->
                        candidate.remove("alternatives")
                        val recent = latest.optJSONArray("history") ?: history
                        if ((0 until recent.length()).none {
                            recent.optJSONObject(it)?.optString("recordId") == candidate.optString("recordId")
                        }) recent.put(candidate)
                        latest.put("history", recent).put("at", System.currentTimeMillis())
                    }
                }
                message = if (stored) "已找到候选版本，预览后可选用；当前歌词未更改。" else "暂无更合适的匹配结果，已保留当前版本。"
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = "歌词源请求失败，请稍后重试。" }
            finally { busy = false }
        }
    }
    CollapsingSettingsLazyScaffold(title = if (selectedKey == null) "歌词源管理" else "版本与预览", onBack = onBack) {
        if (message.isNotBlank()) item(key = "message") { LibraryStatus(message) }
        if (selectedKey == null) {
            item(key = "search") { LibraryField("搜索歌名或歌手", query, { query = it }) }
            if (filtered.isEmpty()) item(key = "empty") { LibraryStatus(if (!loaded) "正在读取…" else if (entries.isEmpty()) "暂无歌词匹配记录" else "未找到符合条件的歌曲") }
            itemsIndexed(filtered, key = { _, item -> item.key }, contentType = { _, _ -> "source" }) { index, item ->
                LibraryLazyGroupItem(index, filtered.size) {
                    SettingsItem(modifier = Modifier.clickable { onNavigate(ComposeRoute("sources", id = item.key)) },
                        headlineContent = { Text(item.title.ifBlank { "未知歌曲" }) },
                        supportingContent = { Text(item.artist + "\n" + item.source) },
                        trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("sources", id = item.key)) }) { Text("查看版本") } })
                }
            }
            item(key = "cache") {
                SettingsGroup("匹配记录与缓存", items = listOf({
                    Column {
                        SettingsItem(headlineContent = { Text("已记录 ${entries.size} 个来源") })
                        LibraryAction("清除记录与缓存", !busy) {
                            val count = snapshot.songCount
                            confirmation = LibraryConfirmation("清除匹配记录与缓存？", "清除 $count 首歌曲的匹配记录、手动选择与历史版本，以及缓存歌词，此操作无法撤销。\n\n保留自定义歌词、翻译语言包与同步设置。", "确认清除") {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        memory.clear()
                                        refreshOverlayMatches(context)
                                        SharedLyricsCache(context).clear()
                                    }
                                    message = "记录与缓存已清除，下次搜索将自动匹配"
                                }
                            }
                        }
                    }
                }))
            }
        } else if (entry == null) item(key = "missing") { LibraryStatus(if (loaded) "该歌曲的来源记忆已删除" else "正在读取…") }
        else {
            item(key = "song") {
                SettingsGroup("歌曲", items = listOf({
                    Column {
                        SettingsItem(headlineContent = { Text(entry.optString("title")) }, supportingContent = { Text(entry.optString("artist")) })
                        LibraryAction(if (busy) "正在处理…" else "搜索候选版本", !busy) { searchNext(entry) }
                        if (entry.optJSONObject("original") != null) LibraryAction("恢复初始版本", !busy) {
                            val original = entry.optJSONObject("original") ?: return@LibraryAction
                            update(entry.optString("key")) { it.put("candidate", original).put("needsReview", false) }
                        }
                    }
                }))
            }
            items(versions, key = { it.id }, contentType = { "version" }) { version ->
                SettingsGroup(if (version.current) "当前选择" else "候选版本", items = listOf({
                    Column {
                        SettingsItem(headlineContent = { Text(version.title) }, supportingContent = { Text(version.artist + "\n" + version.summary) })
                        Text(version.preview, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        LibraryAction(if (version.current) "使用中" else "选用此版本", !version.current && !busy) {
                            update(entry.optString("key")) {
                                it.remove("auto"); it.put("candidate", version.candidate).put("needsReview", false).put("at", System.currentTimeMillis())
                            }
                        }
                    }
                }))
            }
            item(key = "delete") {
                SettingsGroup("来源记忆", items = listOf({
                    LibraryAction("删除来源记忆", !busy) {
                        confirmation = LibraryConfirmation("删除来源记忆？", "将删除《${entry.optString("title")}》在 ${entry.optString("source")} 的选择及历史版本，下次搜索恢复自动匹配。", "删除记忆") {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    memory.delete(entry.optString("key"))
                                    refreshOverlayMatches(context)
                                }
                                onBack()
                            }
                        }
                    }
                }))
            }
        }
    }
    LibraryConfirmationDialog(confirmation) { confirmation = null }
}
