package com.luoh.music.lrc.ui.library

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.data.lyrics.CustomLyricsStore
import com.luoh.music.lrc.data.lyrics.DirectLyricsRepository
import com.luoh.music.lrc.data.lyrics.SharedLyricsCache
import com.luoh.music.lrc.data.lyrics.correctedWordLyrics
import com.luoh.music.lrc.playback.NowPlaying
import com.luoh.music.lrc.ui.agora.CollapsingSettingsLazyScaffold
import com.luoh.music.lrc.ui.agora.CollapsingSettingsScaffold
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.navigation.ComposeRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
private data class ComposeCustomEntry(val id: String, val title: String, val artist: String, val summary: String)

@Composable
internal fun CachedLibraryComposeScreen(onNavigate: (ComposeRoute) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences(SharedLyricsCache.PREFS, Context.MODE_PRIVATE) }
    val revision = preferenceRevision(prefs)
    val active = LocalSettingsScrollRetentionActive.current
    var entries by remember { mutableStateOf(emptyList<SharedLyricsCache.Entry>()) }
    var loaded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(revision, active) {
        if (!active) return@LaunchedEffect
        entries = withContext(Dispatchers.IO) { SharedLyricsCache(context).all() }
        loaded = true
    }
    val filtered = remember(entries, query) {
        entries.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }
    }
    CollapsingSettingsLazyScaffold(title = "缓存歌词勘误", onBack = onBack) {
        item(key = "intro") { LibraryStatus("修改缓存歌词并保存为自定义版本后，主页与桌面歌词将优先使用。删除自定义版本可恢复自动匹配。") }
        item(key = "search") { LibraryField("搜索歌名或歌手", query, { query = it }) }
        if (filtered.isEmpty()) item(key = "empty") {
            LibraryStatus(when { !loaded -> "正在读取缓存…"; entries.isEmpty() -> "暂无缓存歌词，请播放歌曲并获取歌词"; else -> "未找到符合条件的缓存歌词" })
        }
        itemsIndexed(filtered, key = { _, entry -> "${entry.title}\u0000${entry.artist}" }, contentType = { _, _ -> "cached-lyric" }) { index, entry ->
            LibraryLazyGroupItem(index, filtered.size) {
                SettingsItem(
                    modifier = Modifier.clickable { onNavigate(ComposeRoute("cached-editor", title = entry.title, artist = entry.artist)) },
                    headlineContent = { Text(entry.title) },
                    supportingContent = { Text(listOf(entry.artist, entry.result.source).filter { it.isNotBlank() }.joinToString(" · ")) },
                    trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("cached-editor", title = entry.title, artist = entry.artist)) }) { Text("编辑歌词") } }
                )
            }
        }
    }
}

@Composable
internal fun CustomLibraryComposeScreen(onNavigate: (ComposeRoute) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("custom_lyrics_v1", Context.MODE_PRIVATE) }
    val revision = preferenceRevision(prefs)
    val scope = rememberLibraryScope()
    val active = LocalSettingsScrollRetentionActive.current
    var entries by remember { mutableStateOf(emptyList<ComposeCustomEntry>()) }
    var playing by remember { mutableStateOf<NowPlaying.Track?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    LaunchedEffect(revision, active) {
        if (!active) return@LaunchedEffect
        val values = withContext(Dispatchers.IO) {
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            CustomLyricsStore.all(context).map { entry ->
                val parsed = CustomLyricsStore.parse(entry)
                ComposeCustomEntry(entry.id, entry.title, entry.artist, "${parsed.lineCount} 行歌词${if (parsed.translationCount > 0) " · 含译文" else ""} · ${timeFormat.format(Date(entry.updatedAt))}")
            } to NowPlaying.current(context)
        }
        entries = values.first; playing = values.second; loaded = true
    }
    CollapsingSettingsLazyScaffold(title = "自定义歌词", onBack = onBack) {
        playing?.let { track -> item(key = "playing") {
            SettingsGroup("正在播放", items = listOf({
                SettingsItem(headlineContent = { Text(track.title.ifBlank { "未知歌曲" }) }, supportingContent = { Text(track.artist) },
                    trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("editor", title = track.title, artist = track.artist)) }) { Text("添加或编辑") } })
            }))
        } }
        item(key = "new") {
            SettingsGroup("歌词库", items = listOf({
                SettingsItem(headlineContent = { Text("新建自定义歌词") }, supportingContent = { Text("填写歌曲信息或导入 LRC，已保存 ${entries.size} 份") },
                    trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("editor")) }) { Text("新建") } })
            }))
        }
        item(key = "cached") {
            SettingsGroup("缓存歌词", items = listOf({
                SettingsItem(headlineContent = { Text("修改已下载的歌词") }, supportingContent = { Text("编辑缓存正文与译文，保存为自定义版本") },
                    trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("cached")) }) { Text("查看缓存") } })
            }))
        }
        if (entries.isEmpty()) item(key = "empty") { LibraryStatus(if (loaded) "暂无自定义歌词" else "正在读取…") }
        itemsIndexed(entries, key = { _, item -> item.id }, contentType = { _, _ -> "custom-lyric" }) { index, item ->
            val entry = item
            LibraryLazyGroupItem(index, entries.size) {
                Column {
                    SettingsItem(modifier = Modifier.clickable { onNavigate(ComposeRoute("editor", id = entry.id)) },
                        headlineContent = { Text(entry.title.ifBlank { "未命名" }) },
                        supportingContent = { Text(entry.artist.ifBlank { "所有歌手" } + "\n" + item.summary) })
                    Row {
                        LibraryAction("编辑") { onNavigate(ComposeRoute("editor", id = entry.id)) }
                        LibraryAction("删除") {
                            confirmation = LibraryConfirmation("删除自定义歌词？", "${entry.title.ifBlank { "未命名" }}${if (entry.artist.isNotBlank()) " · ${entry.artist}" else ""}\n删除后该歌曲将恢复自动匹配。", "确认删除") {
                                scope.launch {
                                    withContext(Dispatchers.IO) { CustomLyricsStore.delete(context, entry.id) }
                                    LyricsOverlayService.instance?.reloadCustomLyrics()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LibraryConfirmationDialog(confirmation) { confirmation = null }
}

/** Saved-state fields hold drafts, while parsing/import/network work stays in cancellable IO jobs. */
@Composable
internal fun CustomEditorComposeScreen(route: ComposeRoute, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberLibraryScope()
    val active = LocalSettingsScrollRetentionActive.current
    var editingId by rememberSaveable(route) { mutableStateOf(route.id.takeIf { route.kind == "editor" && it.isNotBlank() }) }
    var title by rememberSaveable(route) { mutableStateOf(route.title) }
    var artist by rememberSaveable(route) { mutableStateOf(route.artist) }
    var lyrics by rememberSaveable(route, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var translated by rememberSaveable(route, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var structured by rememberSaveable(route) { mutableStateOf(route.kind == "cached-editor") }
    var savedSnapshot by rememberSaveable(route) { mutableStateOf("") }
    var loaded by rememberSaveable(route) { mutableStateOf(false) }
    var original by remember(route) { mutableStateOf<DirectLyricsRepository.Result?>(null) }
    var originalLoaded by remember(route) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var editingTranslation by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var importedWordLyrics by rememberSaveable(route) { mutableStateOf("") }
    var importedDurationMs by rememberSaveable(route) { mutableStateOf(0L) }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    // A digest avoids duplicating up to 200K characters in Android's saved-state transaction.
    fun snapshot() = MessageDigest.getInstance("SHA-256")
        .digest(listOf(title, artist, lyrics.text, if (structured) translated.text else "", importedWordLyrics)
            .joinToString("\u0000").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun fillTags(raw: String) {
        val tags = CustomLyricsStore.metadata(raw)
        if (title.isBlank()) title = tags["ti"].orEmpty()
        if (artist.isBlank()) artist = tags["ar"].orEmpty()
    }
    fun leave() {
        if (saving) return
        if (!loaded || snapshot() == savedSnapshot) onBack()
        else confirmation = LibraryConfirmation("放弃未保存的修改？", "未保存的输入与导入内容将丢失。", "放弃修改", onBack)
    }
    fun importFile(uri: Uri) {
        if (saving || !loaded) return
        importJob?.cancel(); importing = true
        val before = snapshot()
        importJob = scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readCustomLyricsFile(context, uri) }
                if (snapshot() != before || saving) return@launch
                if (text.isBlank()) { message = "文件读取失败，请使用文本格式的 LRC 文件"; return@launch }
                val imported = withContext(Dispatchers.IO) { CustomLyricsStore.parseImport(text) }
                if (snapshot() != before || saving) return@launch
                if (imported.wordLyrics.isNotBlank()) {
                    structured = true
                    lyrics = TextFieldValue(imported.parsed.lyrics)
                    translated = TextFieldValue(imported.parsed.translatedLyrics)
                    importedWordLyrics = imported.wordLyrics
                    importedDurationMs = imported.durationMs
                    message = "已导入 ${imported.parsed.lineCount} 行歌词 · ${imported.wordLineCount} 行逐字时间轴"
                } else {
                    importedWordLyrics = ""
                    importedDurationMs = 0L
                    val separated = if (structured) imported.parsed else null
                    lyrics = TextFieldValue(if (separated != null && separated.lineCount > 0) separated.lyrics else text.trim())
                    if (structured) translated = TextFieldValue(separated?.translatedLyrics.orEmpty())
                    val raw = lyrics.text; val translation = translated.text; val separate = structured
                    val parsed = withContext(Dispatchers.IO) { if (separate) CustomLyricsStore.parse(raw, translation) else CustomLyricsStore.parse(raw) }
                    message = if (parsed.lineCount > 0) "已导入 ${parsed.lineCount} 行歌词" else "未识别到带时间轴的歌词行"
                }
                fillTags(text)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = "文件读取失败，请使用不超过 1 MB 的文本格式 LRC 文件" }
            finally { importing = false }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importFile(uri) }
    fun save() {
        if (saving || !loaded || (structured && !originalLoaded)) return
        val name = title.trim(); val performer = artist.trim(); val raw = lyrics.text.trim(); val oldId = editingId
        val translation = if (structured) translated.text.trim() else ""
        val separate = structured; val source = original
        val importedWords = importedWordLyrics
        val importedDuration = importedDurationMs
        if (name.isBlank()) { message = "请填写歌名"; return }
        if (raw.length + translation.length > CustomLyricsStore.MAX_LENGTH) { message = "歌词超出长度限制，请删减后保存"; return }
        saving = true; importJob?.cancel()
        scope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val timingSource = if (importedWords.isNotBlank()) DirectLyricsRepository.Result(
                        lyrics = CustomLyricsStore.parseImport(importedWords).parsed.lyrics,
                        wordLyrics = importedWords,
                        durationMs = importedDuration
                    ) else source
                    val parsed = if (separate) CustomLyricsStore.parse(raw, translation) else CustomLyricsStore.parse(raw)
                    if (parsed.lineCount > 0) CustomLyricsStore.save(context, name, performer, raw, oldId,
                        structured = separate, translatedLyrics = translation,
                        wordLyrics = if (separate) correctedWordLyrics(timingSource, raw) else "",
                        durationMs = timingSource?.durationMs ?: 0L)
                    parsed
                }
                if (parsed.lineCount == 0) { message = if (separate) "未识别到歌词正文，请输入后保存" else "未识别到带时间轴的歌词行，格式示例：[01:23.45]歌词"; return@launch }
                savedSnapshot = snapshot()
                LyricsOverlayService.instance?.reloadCustomLyrics(); onBack()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = "保存失败，请重试" }
            finally { saving = false }
        }
    }
    LaunchedEffect(route, active) {
        if (!active) return@LaunchedEffect
        originalLoaded = false
        try {
            val (existing, cached) = withContext(Dispatchers.IO) {
                val custom = route.id.takeIf { route.kind == "editor" && it.isNotBlank() }?.let { CustomLyricsStore.get(context, it) }
                    ?: CustomLyricsStore.find(context, route.title, route.artist)
                custom to (if (custom == null && route.kind == "cached-editor") SharedLyricsCache(context).readForEditing(route.title, route.artist) else null)
            }
            original = if (existing?.structured == true) DirectLyricsRepository.Result(
                lyrics = existing.lyrics, translatedLyrics = existing.translatedLyrics,
                wordLyrics = existing.wordLyrics, durationMs = existing.durationMs
            ) else cached
            originalLoaded = true
            if (!loaded) {
                if (existing != null) {
                    editingId = existing.id; title = existing.title; artist = existing.artist
                    lyrics = TextFieldValue(existing.lyrics); translated = TextFieldValue(existing.translatedLyrics); structured = existing.structured
                    if (route.kind == "cached-editor") message = "该歌曲已有自定义歌词，已打开现有版本"
                } else if (cached != null) {
                    if (cached.lyrics.length + cached.translatedLyrics.length <= CustomLyricsStore.MAX_LENGTH) {
                        lyrics = TextFieldValue(cached.lyrics); translated = TextFieldValue(cached.translatedLyrics)
                    } else message = "缓存歌词超出编辑限制，请删减后粘贴或导入"
                } else if (route.kind == "cached-editor") message = "缓存已清除，请重新获取歌词后编辑"
                savedSnapshot = snapshot(); loaded = true
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { message = "无法读取已保存的歌词，请重试" }
    }
    LaunchedEffect(lyrics.text, translated.text, structured, importedWordLyrics, active) {
        if (!active) return@LaunchedEffect
        val raw = lyrics.text
        val translation = if (structured) translated.text else ""; val separate = structured
        val length = raw.length + translation.length
        status = when { raw.isBlank() -> if (separate) "修改歌词正文或译文后保存" else "粘贴、输入或导入 LRC 歌词"; length > CustomLyricsStore.MAX_LENGTH -> "歌词超出限制，请删减至 ${CustomLyricsStore.MAX_LENGTH / 1000}K 字符以内"; else -> "" }
        if (raw.isBlank() || length > CustomLyricsStore.MAX_LENGTH) return@LaunchedEffect
        delay(120)
        val parsed = withContext(Dispatchers.IO) { if (separate) CustomLyricsStore.parse(raw, translation) else CustomLyricsStore.parse(raw) }
        status = if (parsed.lineCount == 0) { if (separate) "未识别到歌词正文" else "未识别到带时间轴的歌词行" } else buildString {
            append("已识别 ${parsed.lineCount} 行歌词")
            if (parsed.translationCount > 0) append(" · ${parsed.translationCount} 行译文")
            if (parsed.ignoredCount > 0) append(" · 已忽略 ${parsed.ignoredCount} 行无时间轴内容")
            if (importedWordLyrics.isNotBlank()) append(" · 已载入逐字时间轴")
            if (!parsed.timed) append(" · 纯文本歌词")
        }
    }
    BackHandler(enabled = active) { leave() }
    CollapsingSettingsScaffold(title = if (editingId != null) "编辑自定义歌词" else if (route.kind == "cached-editor") "修改缓存歌词" else "添加自定义歌词", onBack = ::leave,
        actions = { TextButton(enabled = loaded && !saving && (!structured || originalLoaded), onClick = ::save) { Text(if (saving) "保存中…" else "保存") } }) {
        if (structured) LibraryStatus("保存后优先使用自定义版本，删除可恢复自动匹配。修改正文或时间戳的行按整句显示，其余行尽量保留逐字高亮。")
        SettingsGroup("歌曲信息", items = listOf({
            Column {
                LibraryField("歌名（必填）", title, { title = it }, loaded && !saving)
                LibraryField("歌手（留空匹配所有同名歌曲）", artist, { artist = it }, loaded && !saving)
            }
        }))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = loaded && !saving && !importing, onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text(if (importing) "正在导入…" else "导入 .lrc / .yrc") }
            TextButton(enabled = loaded && !saving, onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (text.isBlank()) message = "剪贴板暂无文本"
                else {
                    if (structured && editingTranslation) {
                        val start = translated.selection.min.coerceIn(0, translated.text.length); val end = translated.selection.max.coerceIn(0, translated.text.length)
                        translated = TextFieldValue(translated.text.replaceRange(start, end, text), TextRange(start + text.length))
                    } else if (focused) {
                        val start = lyrics.selection.min.coerceIn(0, lyrics.text.length); val end = lyrics.selection.max.coerceIn(0, lyrics.text.length)
                        lyrics = TextFieldValue(lyrics.text.replaceRange(start, end, text), TextRange(start + text.length))
                    } else lyrics = TextFieldValue(text.trim())
                    fillTags(text)
                }
            }) { Text(if (structured && editingTranslation) "粘贴译文" else "粘贴歌词") }
        }
        OutlinedTextField(value = lyrics, onValueChange = { lyrics = it }, enabled = loaded && !saving,
            label = { Text(if (structured) "歌词正文（LRC 或纯文本）" else "LRC 时间轴歌词") }, minLines = 10, maxLines = 16,
            placeholder = { Text(if (structured) "[00:12.30]第一句歌词\n[00:16.85]第二句歌词" else "[00:12.30]第一句歌词\n[00:16.85]第二句歌词\n[00:16.85]同一时间戳的第二行译文") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (it.isFocused) editingTranslation = false })
        if (structured) OutlinedTextField(value = translated, onValueChange = { translated = it }, enabled = loaded && !saving,
            label = { Text("译文（可选）") }, minLines = 4, maxLines = 10,
            supportingText = { Text("译文时间戳应与原文一致，纯文本译文按行对应。") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) editingTranslation = true })
        LibraryStatus(if (!loaded) "正在读取歌词…" else status)
        LibraryStatus(message)
        if (editingId != null) LibraryAction("删除自定义歌词", loaded && !saving) {
            val id = editingId ?: return@LibraryAction
            confirmation = LibraryConfirmation("删除自定义歌词？", "《$title》会恢复自动匹配歌词。", "确认删除") {
                scope.launch {
                    try { withContext(Dispatchers.IO) { CustomLyricsStore.delete(context, id) }; LyricsOverlayService.instance?.reloadCustomLyrics(); onBack() }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { message = "删除失败，请重试" }
                }
            }
        }
    }
    LibraryConfirmationDialog(confirmation) { confirmation = null }
}

/** Bounded read plus the same UTF-8/BOM/UTF-16/GB18030 fallback as the previous LRC editor. */
private fun readCustomLyricsFile(context: Context, uri: Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer); if (read < 0) break
            output.write(buffer, 0, read); require(output.size() <= 1_000_000) { "File too large" }
        }
        output.toByteArray()
    } ?: return ""
    val text = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> runCatching { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { String(bytes, Charset.forName("GB18030")) }
    }
    return text.removePrefix(0xFEFF.toChar().toString())
}
