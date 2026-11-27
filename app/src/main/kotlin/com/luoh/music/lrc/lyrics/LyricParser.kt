package com.luoh.music.lrc.lyrics

import kotlin.math.abs

/** Normalizes line LRC and the canonical timestamp-before-text word format returned by the repository. */
object LyricParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private val metadata = Regex("^\\[[a-zA-Z]+:.*]$")
    private val credit = Regex("^(作词|作曲|编曲|制作人|混音|母带|录音|和声|监制|出品|发行|词|曲)\\s*[：:]", RegexOption.IGNORE_CASE)
    private val wordLine = Regex("^\\[(\\d+),(\\d+)](.*)$")
    private val wordStamp = Regex("\\((\\d+),(\\d+)(?:,\\d+)?\\)")
    private val whitespace = Regex("\\s+")

    fun parse(lrc: String, translated: String = "", wordLrc: String = "", allowPartialWords: Boolean = false): LyricDocument {
        val lines = parseLines(lrc).toMutableList()
        val words = parseWords(wordLrc)
        if (lines.isEmpty() && words.isNotEmpty() && lrc.isBlank()) lines.addAll(words)
        if (lines.isEmpty()) {
            if (stamp.containsMatchIn(lrc)) return LyricDocument(emptyList(), true)
            val plain = plainLines(lrc)
            val translations = plainLines(translated)
            return LyricDocument(plain.mapIndexed { index, text ->
                LyricLine(0L, text, translations.getOrNull(index).orEmpty())
            }, false)
        }
        val translations = parseLines(translated).associate { bucket(it.startMs) to it.text }
        for (index in lines.indices) {
            lines[index] = lines[index].copy(translation = translations[bucket(lines[index].startMs)].orEmpty())
        }
        val attached = mutableSetOf<Int>()
        val identities = lines.map { it.text.replace(whitespace, "") }
        var windowStart = 0
        // Search only nearby timestamps, then require matching text. Each occurrence
        // owns its timing even when a chorus repeats or a neighbouring row is closer.
        for (word in words) {
            while (windowStart < lines.size && lines[windowStart].startMs <= word.startMs - 1200L) {
                windowStart++
            }
            val identity = word.text.replace(whitespace, "")
            var index = -1
            var nearestDistance = 1200L
            var candidate = windowStart
            while (candidate < lines.size && lines[candidate].startMs < word.startMs + 1200L) {
                val line = lines[candidate]
                val matches = if (allowPartialWords) {
                    // Edited lyrics retain timing only for exact, unchanged rows.
                    line.startMs == word.startMs && line.text == word.text.trim()
                } else identities[candidate] == identity
                val distance = abs(line.startMs - word.startMs)
                if (candidate !in attached && matches && distance < nearestDistance) {
                    index = candidate
                    nearestDistance = distance
                }
                candidate++
            }
            if (index >= 0) {
                lines[index] = lines[index].copy(words = word.words, endMs = word.endMs)
                attached += index
            }
        }
        // Missing timing elsewhere must not erase accurate timing on these rows.
        return LyricDocument(lines, true)
    }

    private fun bucket(timeMs: Long) = (timeMs + 5L) / 10L

    private fun plainLines(raw: String): List<String> = raw.removePrefix("﻿").lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !metadata.matches(it) && !credit.containsMatchIn(it) }
        .toList()

    private fun parseLines(raw: String): List<LyricLine> {
        val offsetMs = offset.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val stamped = mutableListOf<Pair<Long, String>>()
        raw.removePrefix("﻿").lineSequence().forEach { row ->
            val times = stamp.findAll(row).toList()
            if (times.isEmpty()) return@forEach
            val text = row.replace(stamp, "").trim()
            for (time in times) {
                val fraction = time.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                val milliseconds = time.groupValues[1].toLong() * 60_000L +
                    time.groupValues[2].toLong() * 1000L + fraction
                stamped += (milliseconds - offsetMs).coerceAtLeast(0L) to text
            }
        }
        val lines = mutableListOf<LyricLine>()
        for ((time, text) in stamped.sortedBy { it.first }) {
            if (text.isEmpty()) {
                val previous = lines.lastOrNull()
                if (previous != null && previous.endMs == null) lines[lines.lastIndex] = previous.copy(endMs = time)
            } else if (!credit.containsMatchIn(text)) {
                lines += LyricLine(time, text)
            }
        }
        return lines
    }

    private fun parseWords(raw: String): List<LyricLine> = raw.lineSequence().mapNotNull { row ->
        val match = wordLine.matchEntire(row.trim()) ?: return@mapNotNull null
        val start = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val duration = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
        val body = match.groupValues[3]
        val markers = wordStamp.findAll(body).toList()
        val words = markers.mapIndexedNotNull { index, marker ->
            val textEnd = markers.getOrNull(index + 1)?.range?.first ?: body.length
            val text = body.substring(marker.range.last + 1, textEnd)
            if (text.isEmpty()) null else LyricWord(
                marker.groupValues[1].toLongOrNull() ?: start,
                marker.groupValues[2].toLongOrNull() ?: 0L,
                text
            )
        }
        if (words.isEmpty()) null else LyricLine(start, words.joinToString("") { it.text }, endMs = start + duration, words = words)
    }.sortedBy { it.startMs }.toList()
}
