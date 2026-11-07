package com.luoh.music.lrc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Keeps optional translation separate from lyric timing and rejects obsolete song/config results. */
class HomeTranslationController(
    private val scope: CoroutineScope,
    private val translate: suspend (List<MissingLine>, onTranslationRequired: () -> Unit) -> Result,
    private val onDocument: (LyricDocument) -> Unit,
    private val onStatus: (String) -> Unit
) {
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
            onStatus("")
            return
        }
        val missing = document.lines.mapIndexedNotNull { id, line ->
            if (line.translation.isBlank() && TranslationText.hasForeignLetters(line.text)) MissingLine(id, line.text) else null
        }
        if (missing.isEmpty()) {
            onStatus("")
            return
        }
        if (mode != "offline" && mode != "api") {
            // Bilingual is a display preference, not a request to enable machine translation.
            onStatus("")
            return
        }
        val token = generation
        onStatus("")
        job = scope.launch {
            try {
                val result = translate(missing) {
                    // Only missing foreign lines need progress, even when most of the song is Chinese.
                    if (token == generation) onStatus(if (mode == "offline") "正在补充译文 · 离线翻译" else "正在补充译文 · 自定义 API")
                }
                if (token != generation) return@launch
                if (result.outcome != SupplementOutcome.COMPLETE) {
                    onStatus("")
                    return@launch
                }
                val results = result.translations
                val lines = document.lines.mapIndexed { id, line ->
                    if (line.translation.isNotBlank()) line
                    else results[id]?.takeIf { it.isNotBlank() }?.let { line.copy(translation = it) } ?: line
                }
                if (lines != document.lines) onDocument(document.copy(lines = lines))
                onStatus(if (lines == document.lines) "未获取到补充译文，请检查翻译配置" else "")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (token == generation) onStatus("补充翻译未完成：${error.message.orEmpty().take(120).ifBlank { "请检查网络或翻译配置" }}")
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
