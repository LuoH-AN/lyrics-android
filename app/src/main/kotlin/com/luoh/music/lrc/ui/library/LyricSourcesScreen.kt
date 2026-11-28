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
import com.luoh.music.lrc.R
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

private fun sourceVersions(context: Context, entry: JSONObject): List<ComposeSourceVersion> {
    val history = entry.optJSONArray("history") ?: JSONArray().put(entry.optJSONObject("candidate"))
    return (0 until history.length()).mapNotNull { index ->
        val candidate = history.optJSONObject(index) ?: return@mapNotNull null
        val seconds = candidate.optLong("duration") / 1000
        val current = !entry.optBoolean("needsReview") && candidate.optString("recordId") == entry.optJSONObject("candidate")?.optString("recordId")
        ComposeSourceVersion("${candidate.optString("recordId")}:$index",
            candidate.optString("title").ifBlank { entry.optString("title") },
            candidate.optString("artist").ifBlank { entry.optString("artist") } + " · " + context.lyricSourceLabel(candidate.optString("source")),
            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} · " + (if (candidate.optString("wordLyrics").isNotBlank()) context.getString(R.string.word_timed_lyrics) else context.getString(R.string.line_timed_lyrics)) +
                (if (candidate.optString("translatedLyrics").isNotBlank()) context.getString(R.string.includes_translation_suffix) else ""),
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
    val context = LocalContext.current
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
    var message by remember(route.id) { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    val entry = snapshot.selected
    LaunchedEffect(revision, route.id, query, active) {
        if (!active) return@LaunchedEffect
        if (query.isNotBlank() && selectedKey == null) delay(120)
        val values = withContext(Dispatchers.IO) {
            val values = memory.entries()
            val selected = values.find { it.optString("key") == selectedKey }
            val rows = values.asReversed().map {
                ComposeSourceRow(it.optString("key"), it.optString("title").ifBlank { context.getString(R.string.unknown_song) }, it.optString("artist"), it.optString("source"))
            }.filter { (it.title + " " + it.artist).contains(query.trim(), true) }
            SourcePageSnapshot(values, selected, selected?.let { sourceVersions(context, it) }.orEmpty(), rows,
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
            catch (error: Exception) { message = context.getString(R.string.source_selection_save_failed) }
            finally { busy = false }
        }
    }
    fun searchNext(item: JSONObject) {
        if (busy) return
        if (item.optString("title").isBlank()) { message = context.getString(R.string.old_match_missing_song_details); return }
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
                message = if (stored) context.getString(R.string.new_versions_found) else context.getString(R.string.no_better_match)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = context.getString(R.string.lyrics_source_request_failed) }
            finally { busy = false }
        }
    }
    CollapsingSettingsLazyScaffold(title = if (selectedKey == null) context.getString(R.string.lyrics_sources) else context.getString(R.string.versions_and_previews), onBack = onBack) {
        if (message.isNotBlank()) item(key = "message") { LibraryStatus(message) }
        if (selectedKey == null) {
            item(key = "search") { LibraryField(context.getString(R.string.search_by_song_or_artist), query, { query = it }) }
            if (filtered.isEmpty()) item(key = "empty") { LibraryStatus(if (!loaded) context.getString(R.string.loading) else if (entries.isEmpty()) context.getString(R.string.no_lyrics_match_history) else context.getString(R.string.no_matching_songs)) }
            itemsIndexed(filtered, key = { _, item -> item.key }, contentType = { _, _ -> "source" }) { index, item ->
                LibraryLazyGroupItem(index, filtered.size) {
                    SettingsItem(modifier = Modifier.clickable { onNavigate(ComposeRoute("sources", id = item.key)) },
                        headlineContent = { Text(item.title.ifBlank { context.getString(R.string.unknown_song) }) },
                        supportingContent = { Text(item.artist + "\n" + context.lyricSourceLabel(item.source)) },
                        trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("sources", id = item.key)) }) { Text(context.getString(R.string.view_versions)) } })
                }
            }
            item(key = "cache") {
                SettingsGroup(context.getString(R.string.match_history_and_cache), items = listOf({
                    Column {
                        SettingsItem(headlineContent = { Text(context.getString(R.string.saved_source_count, entries.size)) })
                        LibraryAction(context.getString(R.string.clear_history_and_cache), !busy) {
                            val count = snapshot.songCount
                            confirmation = LibraryConfirmation(context.getString(R.string.clear_match_history_and_cache), context.getString(R.string.clear_match_history_message, count), context.getString(R.string.confirm_clearing)) {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        memory.clear()
                                        refreshOverlayMatches(context)
                                        SharedLyricsCache(context).clear()
                                    }
                                    message = context.getString(R.string.history_and_cache_cleared)
                                }
                            }
                        }
                    }
                }))
            }
        } else if (entry == null) item(key = "missing") { LibraryStatus(if (loaded) context.getString(R.string.song_source_deleted) else context.getString(R.string.loading)) }
        else {
            item(key = "song") {
                SettingsGroup(context.getString(R.string.song), items = listOf({
                    Column {
                        SettingsItem(headlineContent = { Text(entry.optString("title")) }, supportingContent = { Text(entry.optString("artist")) })
                        LibraryAction(if (busy) context.getString(R.string.please_wait) else context.getString(R.string.find_other_versions), !busy) { searchNext(entry) }
                        if (entry.optJSONObject("original") != null) LibraryAction(context.getString(R.string.restore_original_version), !busy) {
                            val original = entry.optJSONObject("original") ?: return@LibraryAction
                            update(entry.optString("key")) { it.put("candidate", original).put("needsReview", false) }
                        }
                    }
                }))
            }
            items(versions, key = { it.id }, contentType = { "version" }) { version ->
                SettingsGroup(if (version.current) context.getString(R.string.selected_version) else context.getString(R.string.other_versions), items = listOf({
                    Column {
                        SettingsItem(headlineContent = { Text(version.title) }, supportingContent = { Text(version.artist + "\n" + version.summary) })
                        Text(version.preview, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        LibraryAction(if (version.current) context.getString(R.string.in_use) else context.getString(R.string.use_this_version), !version.current && !busy) {
                            update(entry.optString("key")) {
                                it.remove("auto"); it.put("candidate", version.candidate).put("needsReview", false).put("at", System.currentTimeMillis())
                            }
                        }
                    }
                }))
            }
            item(key = "delete") {
                SettingsGroup(context.getString(R.string.saved_source), items = listOf({
                    LibraryAction(context.getString(R.string.delete_saved_source), !busy) {
                        confirmation = LibraryConfirmation(context.getString(R.string.delete_saved_source_title), context.getString(R.string.delete_saved_source_message, entry.optString("title"), context.lyricSourceLabel(entry.optString("source"))), context.getString(R.string.delete_saved_entry)) {
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
