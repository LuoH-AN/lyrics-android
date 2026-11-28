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
import com.luoh.music.lrc.R
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
import java.text.DateFormat
import java.util.Date

@Immutable
private data class ComposeCustomEntry(val id: String, val title: String, val artist: String, val summary: String)

@Composable
internal fun CachedLibraryComposeScreen(onNavigate: (ComposeRoute) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
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
    CollapsingSettingsLazyScaffold(title = context.getString(R.string.edit_cached_lyrics), onBack = onBack) {
        item(key = "intro") { LibraryStatus(context.getString(R.string.cached_lyrics_intro)) }
        item(key = "search") { LibraryField(context.getString(R.string.search_by_song_or_artist), query, { query = it }) }
        if (filtered.isEmpty()) item(key = "empty") {
            LibraryStatus(when { !loaded -> context.getString(R.string.loading_cache); entries.isEmpty() -> context.getString(R.string.no_cached_lyrics_play_a_song); else -> context.getString(R.string.no_matching_cached_lyrics) })
        }
        itemsIndexed(filtered, key = { _, entry -> "${entry.title}\u0000${entry.artist}" }, contentType = { _, _ -> "cached-lyric" }) { index, entry ->
            LibraryLazyGroupItem(index, filtered.size) {
                SettingsItem(
                    modifier = Modifier.clickable { onNavigate(ComposeRoute("cached-editor", title = entry.title, artist = entry.artist)) },
                    headlineContent = { Text(entry.title) },
                    supportingContent = { Text(listOf(entry.artist, entry.result.source).filter { it.isNotBlank() }.joinToString(" · ")) },
                    trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("cached-editor", title = entry.title, artist = entry.artist)) }) { Text(context.getString(R.string.edit_lyrics)) } }
                )
            }
        }
    }
}

@Composable
internal fun CustomLibraryComposeScreen(onNavigate: (ComposeRoute) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
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
            val timeFormat = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT, context.resources.configuration.locales[0],
            )
            CustomLyricsStore.all(context).map { entry ->
                val parsed = CustomLyricsStore.parse(entry)
                ComposeCustomEntry(entry.id, entry.title, entry.artist, context.getString(R.string.custom_lyrics_summary, parsed.lineCount, if (parsed.translationCount > 0) context.getString(R.string.includes_translation_suffix) else "", timeFormat.format(Date(entry.updatedAt))))
            } to NowPlaying.current(context)
        }
        entries = values.first; playing = values.second; loaded = true
    }
    CollapsingSettingsLazyScaffold(title = context.getString(R.string.custom_lyrics), onBack = onBack) {
        playing?.let { track -> item(key = "playing") {
            SettingsGroup(context.getString(R.string.now_playing), items = listOf({
                SettingsItem(headlineContent = { Text(track.title.ifBlank { context.getString(R.string.unknown_song) }) }, supportingContent = { Text(track.artist) },
                    trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("editor", title = track.title, artist = track.artist)) }) { Text(context.getString(R.string.add_or_edit)) } })
            }))
        } }
        item(key = "new") {
            SettingsGroup(context.getString(R.string.lyrics_library), items = listOf({
                SettingsItem(headlineContent = { Text(context.getString(R.string.new_custom_lyrics)) }, supportingContent = { Text(context.getString(R.string.custom_lyrics_saved_count, entries.size)) },
                    trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("editor")) }) { Text(context.getString(R.string.create_new)) } })
            }))
        }
        item(key = "cached") {
            SettingsGroup(context.getString(R.string.cached_lyrics), items = listOf({
                SettingsItem(headlineContent = { Text(context.getString(R.string.edit_downloaded_lyrics)) }, supportingContent = { Text(context.getString(R.string.edit_cached_lyrics_description)) },
                    trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("cached")) }) { Text(context.getString(R.string.view_cache)) } })
            }))
        }
        if (entries.isEmpty()) item(key = "empty") { LibraryStatus(if (loaded) context.getString(R.string.no_custom_lyrics_yet) else context.getString(R.string.loading)) }
        itemsIndexed(entries, key = { _, item -> item.id }, contentType = { _, _ -> "custom-lyric" }) { index, item ->
            val entry = item
            LibraryLazyGroupItem(index, entries.size) {
                Column {
                    SettingsItem(modifier = Modifier.clickable { onNavigate(ComposeRoute("editor", id = entry.id)) },
                        headlineContent = { Text(entry.title.ifBlank { context.getString(R.string.untitled) }) },
                        supportingContent = { Text(entry.artist.ifBlank { context.getString(R.string.all_artists) } + "\n" + item.summary) })
                    Row {
                        LibraryAction(context.getString(R.string.edit)) { onNavigate(ComposeRoute("editor", id = entry.id)) }
                        LibraryAction(context.getString(R.string.delete)) {
                            confirmation = LibraryConfirmation(context.getString(R.string.delete_custom_lyrics), context.getString(R.string.delete_custom_lyrics_message, entry.title.ifBlank { context.getString(R.string.untitled) }, if (entry.artist.isNotBlank()) " · ${entry.artist}" else ""), context.getString(R.string.confirm_deletion)) {
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
    val context = LocalContext.current
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
        else confirmation = LibraryConfirmation(context.getString(R.string.discard_unsaved_changes), context.getString(R.string.unsaved_edits_and_imports_will_be), context.getString(R.string.discard_changes), onBack)
    }
    fun importFile(uri: Uri) {
        if (saving || !loaded) return
        importJob?.cancel(); importing = true
        val before = snapshot()
        importJob = scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readCustomLyricsFile(context, uri) }
                if (snapshot() != before || saving) return@launch
                if (text.isBlank()) { message = context.getString(R.string.lrc_read_failed); return@launch }
                val imported = withContext(Dispatchers.IO) { CustomLyricsStore.parseImport(text) }
                if (snapshot() != before || saving) return@launch
                if (imported.wordLyrics.isNotBlank()) {
                    structured = true
                    lyrics = TextFieldValue(imported.parsed.lyrics)
                    translated = TextFieldValue(imported.parsed.translatedLyrics)
                    importedWordLyrics = imported.wordLyrics
                    importedDurationMs = imported.durationMs
                    message = context.getString(R.string.timed_lyrics_imported, imported.parsed.lineCount, imported.wordLineCount)
                } else {
                    importedWordLyrics = ""
                    importedDurationMs = 0L
                    val separated = if (structured) imported.parsed else null
                    lyrics = TextFieldValue(if (separated != null && separated.lineCount > 0) separated.lyrics else text.trim())
                    if (structured) translated = TextFieldValue(separated?.translatedLyrics.orEmpty())
                    val raw = lyrics.text; val translation = translated.text; val separate = structured
                    val parsed = withContext(Dispatchers.IO) { if (separate) CustomLyricsStore.parse(raw, translation) else CustomLyricsStore.parse(raw) }
                    message = if (parsed.lineCount > 0) context.getString(R.string.lyrics_imported, parsed.lineCount) else context.getString(R.string.no_timed_lyric_lines_found)
                }
                fillTags(text)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = context.getString(R.string.lrc_read_failed_size) }
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
        if (name.isBlank()) { message = context.getString(R.string.enter_a_song_title); return }
        if (raw.length + translation.length > CustomLyricsStore.MAX_LENGTH) { message = context.getString(R.string.lyrics_too_long_to_save); return }
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
                if (parsed.lineCount == 0) { message = if (separate) context.getString(R.string.enter_lyrics_before_saving) else context.getString(R.string.timed_lyrics_format_example); return@launch }
                savedSnapshot = snapshot()
                LyricsOverlayService.instance?.reloadCustomLyrics(); onBack()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = context.getString(R.string.save_failed) }
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
                    if (route.kind == "cached-editor") message = context.getString(R.string.opened_existing_custom_lyrics)
                } else if (cached != null) {
                    if (cached.lyrics.length + cached.translatedLyrics.length <= CustomLyricsStore.MAX_LENGTH) {
                        lyrics = TextFieldValue(cached.lyrics); translated = TextFieldValue(cached.translatedLyrics)
                    } else message = context.getString(R.string.cached_lyrics_too_long)
                } else if (route.kind == "cached-editor") message = context.getString(R.string.cache_cleared_find_lyrics_again)
                savedSnapshot = snapshot(); loaded = true
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { message = context.getString(R.string.saved_lyrics_load_failed) }
    }
    LaunchedEffect(lyrics.text, translated.text, structured, importedWordLyrics, active) {
        if (!active) return@LaunchedEffect
        val raw = lyrics.text
        val translation = if (structured) translated.text else ""; val separate = structured
        val length = raw.length + translation.length
        status = when { raw.isBlank() -> if (separate) context.getString(R.string.edit_lyrics_then_save) else context.getString(R.string.paste_type_or_import_lrc_lyrics); length > CustomLyricsStore.MAX_LENGTH -> context.getString(R.string.lyrics_character_limit, CustomLyricsStore.MAX_LENGTH / 1000); else -> "" }
        if (raw.isBlank() || length > CustomLyricsStore.MAX_LENGTH) return@LaunchedEffect
        delay(120)
        val parsed = withContext(Dispatchers.IO) { if (separate) CustomLyricsStore.parse(raw, translation) else CustomLyricsStore.parse(raw) }
        status = if (parsed.lineCount == 0) { if (separate) context.getString(R.string.no_lyrics_found_in_the_text) else context.getString(R.string.no_timed_lyric_lines_found) } else buildString {
            append(context.getString(R.string.lyrics_lines_found, parsed.lineCount))
            if (parsed.translationCount > 0) append(context.getString(R.string.translated_lines_count, parsed.translationCount))
            if (parsed.ignoredCount > 0) append(context.getString(R.string.untimed_lines_skipped, parsed.ignoredCount))
            if (importedWordLyrics.isNotBlank()) append(context.getString(R.string.word_timing_loaded))
            if (!parsed.timed) append(context.getString(R.string.plain_text_lyrics_suffix))
        }
    }
    BackHandler(enabled = active) { leave() }
    CollapsingSettingsScaffold(title = if (editingId != null) context.getString(R.string.edit_custom_lyrics) else if (route.kind == "cached-editor") context.getString(R.string.edit_cached_version) else context.getString(R.string.add_custom_lyrics), onBack = ::leave,
        actions = { TextButton(enabled = loaded && !saving && (!structured || originalLoaded), onClick = ::save) { Text(if (saving) context.getString(R.string.saving) else context.getString(R.string.save)) } }) {
        if (structured) LibraryStatus(context.getString(R.string.edit_cached_lyrics_message))
        SettingsGroup(context.getString(R.string.song_details), items = listOf({
            Column {
                LibraryField(context.getString(R.string.song_title_required), title, { title = it }, loaded && !saving)
                LibraryField(context.getString(R.string.artist_optional_match_any), artist, { artist = it }, loaded && !saving)
            }
        }))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = loaded && !saving && !importing, onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text(if (importing) context.getString(R.string.importing) else context.getString(R.string.import_lrc_yrc)) }
            TextButton(enabled = loaded && !saving, onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (text.isBlank()) message = context.getString(R.string.no_text_on_the_clipboard)
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
            }) { Text(if (structured && editingTranslation) context.getString(R.string.paste_translation) else context.getString(R.string.paste_lyrics)) }
        }
        OutlinedTextField(value = lyrics, onValueChange = { lyrics = it }, enabled = loaded && !saving,
            label = { Text(if (structured) context.getString(R.string.lyrics_lrc_or_plain_text) else context.getString(R.string.timed_lrc_lyrics)) }, minLines = 10, maxLines = 16,
            placeholder = { Text(if (structured) context.getString(R.string.lrc_example) else context.getString(R.string.bilingual_lrc_example)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (it.isFocused) editingTranslation = false })
        if (structured) OutlinedTextField(value = translated, onValueChange = { translated = it }, enabled = loaded && !saving,
            label = { Text(context.getString(R.string.translation_optional)) }, minLines = 4, maxLines = 10,
            supportingText = { Text(context.getString(R.string.translation_timestamp_hint)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) editingTranslation = true })
        LibraryStatus(if (!loaded) context.getString(R.string.loading_lyrics) else status)
        LibraryStatus(message)
        if (editingId != null) LibraryAction(context.getString(R.string.delete_custom_lyrics_action), loaded && !saving) {
            val id = editingId ?: return@LibraryAction
            confirmation = LibraryConfirmation(context.getString(R.string.delete_custom_lyrics), context.getString(R.string.resume_song_auto_match, title), context.getString(R.string.confirm_deletion)) {
                scope.launch {
                    try { withContext(Dispatchers.IO) { CustomLyricsStore.delete(context, id) }; LyricsOverlayService.instance?.reloadCustomLyrics(); onBack() }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { message = context.getString(R.string.delete_failed) }
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
