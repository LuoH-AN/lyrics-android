package com.luoh.music.lrc.data.lyrics

import com.luoh.music.lrc.data.lyrics.DirectLyricsRepository.Result

private val PLACEHOLDER_LYRICS = listOf(
    Regex("^(?:此|该)?歌曲(?:为)?(?:一首)?(?:没有填词的|无歌词的)?纯音乐请您?欣赏$"),
    Regex("^纯音乐请您?欣赏$"),
    Regex("^(?:暂无|暂未匹配到|没有|无)歌词$"),
    Regex("^歌词暂无$")
)

private fun coverage(original: String, extra: String): Int {
    if (!isUsableLyrics(extra)) return 0
    fun timedLines(text: String): Int = text.lineSequence().count {
        Regex("^\\[(?:\\d+:\\d+|\\d+,\\d+)").containsMatchIn(it.trim())
    }
    val base = timedLines(original).coerceAtLeast(1)
    return (timedLines(extra) * 100 / base).coerceIn(0, 100)
}

internal fun needsEnrichment(result: Result): Boolean = result.score >= EXACT_MATCH_SCORE &&
    (coverage(result.lyrics, result.wordLyrics) < 80 ||
        (Regex("[a-zA-Z\\u3040-\\u30ff\\uac00-\\ud7af]").containsMatchIn(result.lyrics) &&
            coverage(result.lyrics, result.translatedLyrics) < 80))

internal fun qualityRank(result: Result): Int {
    val confidenceBand = if (result.score >= EXACT_MATCH_SCORE) 2 else 1
    return confidenceBand * 100_000 +
    coverage(result.lyrics, result.translatedLyrics) * 200 +
    coverage(result.lyrics, result.wordLyrics) * 100 +
    result.score * 100 +
    lyricBodyScore(result.lyrics) +
    when (result.source) {
        "网易云音乐" -> 4
        "QQ音乐" -> 2
        else -> 0
    }
}

internal fun isTitleOnlyLyrics(value: String, track: String): Boolean {
    val lines = value.lineSequence().map { it.replace(Regex("^(\\[[^]]+])+"), "").trim() }
        .filter { it.isNotBlank() }.toList()
    if (lines.size != 1) return false
    val title = lines[0].split(Regex("\\s+[-–—]\\s+"), limit = 2)[0]
    return titleIdentityKey(title) == titleIdentityKey(track)
}

internal fun isUsableLyrics(value: String): Boolean {
    val normalized = value.trim()
    if (normalized.isEmpty() || normalized.equals("null", true) ||
        normalized.equals("undefined", true)
    ) return false

    val meaningful = normalized.lineSequence()
        .map { it.replace(Regex("^(\\[[^]]+])+"), "").trim() }
        .filter { it.isNotBlank() }
        .filterNot { Regex("^(?:作词|作詞|作曲|编曲|編曲|词|詞|曲|制作人|製作人|混音|母带|录音|演唱|歌手|composer|lyricist|arranger|producer)\\s*[:：]", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        .toList()
    if (meaningful.isEmpty()) return false
    val body = meaningful.asSequence()
        .joinToString("")
        .replace(Regex("[\\s,，。.!！?？、]"), "")
    if (body.length <= 48 && PLACEHOLDER_LYRICS.any { it.matches(body) }) return false
    return true
}

private fun lyricBodyScore(lyrics: String): Int {
    val credit = Regex("^(作词|作詞|作曲|编曲|編曲|词|詞|曲|composer|lyricist|arranger)\\s*[:：]", RegexOption.IGNORE_CASE)
    val count = lyrics.lineSequence().count { raw ->
        val text = raw.replace(Regex("^(\\[[^]]+])+"), "").trim()
        text.isNotBlank() && !credit.containsMatchIn(text) &&
            !text.contains("纯音乐，请欣赏")
    }
    return count.coerceAtMost(40) * 3
}
