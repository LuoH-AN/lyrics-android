package com.luoh.music.lrc

import kotlin.math.abs

/** Retain karaoke timing only for lines whose text and position were left unchanged. */
internal fun correctedWordLyrics(original: DirectLyricsRepository.Result?, lyrics: String): String {
    if (original == null || original.wordLyrics.isBlank()) return ""
    val edited = LyricParser.parse(CustomLyricsStore.parse(lyrics, "").lyrics)
    if (!edited.timed) return ""
    // Equal timestamps are ambiguous to the renderers' word attachment logic.
    val unchanged = edited.lines.groupBy { it.startMs }.values.filter { it.size == 1 }
        .map { it.single().let { line -> line.startMs to line.text } }.toSet()
    val source = LyricParser.parse(CustomLyricsStore.parse(original.lyrics, "").lyrics)
    // Parse the word track alone so previously saved partial timing isn't discarded by the coverage threshold.
    val words = LyricParser.parse("", wordLrc = original.wordLyrics)
    val retained = words.lines.mapNotNull { wordLine ->
        val line = source.lines.minByOrNull { abs(it.startMs - wordLine.startMs) } ?: return@mapNotNull null
        if (abs(line.startMs - wordLine.startMs) >= 1200L ||
            wordLine.text.trim() != line.text || (line.startMs to line.text) !in unchanged) null
        else wordLine.copy(startMs = line.startMs)
    }.distinctBy { it.startMs }
    return retained.joinToString("\n") { line ->
        val end = line.endMs ?: line.words.maxOf { it.startMs + it.durationMs }
        buildString {
            append('[').append(line.startMs).append(',').append((end - line.startMs).coerceAtLeast(0L)).append(']')
            line.words.forEach { word ->
                append('(').append(word.startMs).append(',').append(word.durationMs).append(')').append(word.text)
            }
        }
    }
}
