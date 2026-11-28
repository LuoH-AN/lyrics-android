package com.luoh.music.lrc.ui.home

import androidx.annotation.StringRes
import com.luoh.music.lrc.R
import com.luoh.music.lrc.data.translation.SupplementOutcome
import com.luoh.music.lrc.data.translation.TranslationText
import com.luoh.music.lrc.lyrics.LyricDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Keeps optional translation separate from lyric timing and rejects obsolete song/config results. */
class HomeTranslationController(
    private val scope: CoroutineScope,
    private val translate: suspend (List<MissingLine>, onTranslationRequired: () -> Unit) -> Result,
    private val onDocument: (LyricDocument) -> Unit,
    private val onStatus: (Status?) -> Unit
) {
    data class Status(@StringRes val message: Int, val detail: String? = null)
    data class MissingLine(val id: Int, val text: String)
    data class Result(val translations: Map<Int, String> = emptyMap(), val outcome: SupplementOutcome = SupplementOutcome.COMPLETE)
    private data class Input(val key: String, val document: LyricDocument, val display: String, val mode: String, val config: String)
    private var input: Input? = null
    private var job: Job? = null
    private var generation = 0

    fun update(key: String, document: LyricDocument, display: String, mode: String, config: String = "") {
        val next = Input(key, document, display, mode, config)
        if (next == input) return
        cancel()
        input = next
        if (document.lines.isEmpty() || display == "original") {
            onStatus(null)
            return
        }
        val missing = document.lines.mapIndexedNotNull { id, line ->
            if (line.translation.isBlank() && TranslationText.hasForeignLetters(line.text)) MissingLine(id, line.text) else null
        }
        if (missing.isEmpty()) {
            onStatus(null)
            return
        }
        if (mode != "offline" && mode != "api") {
            // Bilingual is a display preference, not a request to enable machine translation.
            onStatus(null)
            return
        }
        val token = generation
        onStatus(null)
        job = scope.launch {
            try {
                val result = translate(missing) {
                    // Only missing foreign lines need progress, even when most of the song is Chinese.
                    if (token == generation) {
                        onStatus(Status(if (mode == "offline") R.string.translating_lyrics_offline else R.string.translating_lyrics_api))
                    }
                }
                if (token != generation) return@launch
                if (result.outcome != SupplementOutcome.COMPLETE) {
                    onStatus(null)
                    return@launch
                }
                val results = result.translations
                val lines = document.lines.mapIndexed { id, line ->
                    if (line.translation.isNotBlank()) line
                    else results[id]?.takeIf { it.isNotBlank() }?.let { line.copy(translation = it) } ?: line
                }
                if (lines != document.lines) onDocument(document.copy(lines = lines))
                onStatus(if (lines == document.lines) Status(R.string.translation_no_result) else null)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (token == generation) onStatus(Status(R.string.translation_unfinished, error.message.orEmpty().take(120)))
            }
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        input = null
    }
}
