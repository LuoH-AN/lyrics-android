package com.luoh.music.lrc

import java.util.Locale

/**
 * 把当前歌词导出为 .lrc 文件内容：原文按源文本原样保留（含 offset/staff 标签与多时间戳行），
 * 译文以相同时间戳紧跟原句之后——这是播放器通用的双语 LRC 约定。
 */
object LyricExporter {
    private val BOM = 0xFEFF.toChar().toString()
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val forbidden = Regex("[\\\\/:*?\"<>|\\r\\n\\t]")

    fun fileName(title: String, artist: String): String {
        val base = listOf(artist.trim(), title.trim()).filter { it.isNotEmpty() }.joinToString(" - ")
        val cleaned = base.replace(forbidden, " ").replace(Regex("\\s+"), " ")
            .trim().take(80).trimEnd(' ', '.')
        return (if (cleaned.isEmpty()) "歌词" else cleaned) + ".lrc"
    }

    fun buildLrc(title: String, artist: String, lyrics: String, translated: String): String {
        val body = if (stamp.containsMatchIn(lyrics)) mergeTimed(lyrics, translated)
        else interleavePlain(lyrics, translated)
        // 源文件已带的 [ti:]/[ar:] 不重复写，MediaSession 的信息只补缺
        val header = buildString {
            if (title.trim().isNotEmpty() && !body.hasTag("ti")) append("[ti:").append(title.trim()).append("]\n")
            if (artist.trim().isNotEmpty() && !body.hasTag("ar")) append("[ar:").append(artist.trim()).append("]\n")
        }
        return header + body.trimEnd()
    }

    private fun String.hasTag(tag: String): Boolean =
        lineSequence().any { it.startsWith("[$tag:", ignoreCase = true) }

    /** 带时间轴的歌词：原行原样输出，命中同一句（10ms 归桶，与 LyricParser 一致）的译文补在下一行。 */
    private fun mergeTimed(lyrics: String, translated: String): String {
        val translations = LinkedHashMap<Long, String>()
        translated.removePrefix(BOM).lineSequence().forEach { row ->
            val times = stamp.findAll(row).toList()
            if (times.isEmpty()) return@forEach
            val text = row.replace(stamp, "").trim()
            if (text.isEmpty()) return@forEach
            for (time in times) {
                val bucket = (millis(time) + 5L) / 10L
                translations[bucket] = listOfNotNull(translations[bucket], text).joinToString(" / ")
            }
        }
        if (translations.isEmpty()) return lyrics.removePrefix(BOM).trimEnd()
        val out = StringBuilder()
        lyrics.removePrefix(BOM).lineSequence().forEach { row ->
            out.append(row.trimEnd()).append('\n')
            if (row.replace(stamp, "").trim().isEmpty()) return@forEach
            val time = stamp.find(row)?.let(::millis) ?: return@forEach
            val text = translations[(time + 5L) / 10L] ?: return@forEach
            out.append(formatTime(time)).append(text).append('\n')
        }
        return out.toString()
    }

    /** 无时间轴的纯文本：按行号把译文穿插在原文后面。 */
    private fun interleavePlain(lyrics: String, translated: String): String {
        val originals = plainLines(lyrics)
        val translations = plainLines(translated)
        if (translations.isEmpty()) return originals.joinToString("\n")
        return originals.mapIndexed { index, text ->
            translations.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { "$text\n$it" } ?: text
        }.joinToString("\n")
    }

    private fun plainLines(raw: String): List<String> = raw.removePrefix(BOM).lineSequence()
        .map(String::trim).filter(String::isNotEmpty).toList()

    private fun millis(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
        val millis = if (fraction.isEmpty()) 0L else fraction.padEnd(3, '0').take(3).toLong()
        return minutes * 60_000L + seconds * 1_000L + millis
    }

    private fun formatTime(milliseconds: Long): String = "[%02d:%02d.%03d]".format(
        Locale.US, milliseconds / 60_000L, (milliseconds / 1_000L) % 60L, milliseconds % 1_000L
    )
}
