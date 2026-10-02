package com.luoh.music.lrc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * 用户手动指定的 LRC 歌词。按「歌名 + 歌手」匹配，歌手留空则匹配所有同名歌曲。
 * 命中后主页与悬浮窗都直接使用，不再联网搜索；删除后恢复自动匹配。
 */
object CustomLyricsStore {
    const val SOURCE = "自定义歌词"
    const val MAX_LENGTH = 200_000
    private const val PREFS = "custom_lyrics_v1"
    private const val ENTRY_PREFIX = "entry:"
    private val BOM = 0xFEFF.toChar().toString()

    private val TIME_TAG = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val OFFSET_TAG = Regex("^\\[offset:\\s*([+-]?\\d+)\\s*]$", RegexOption.IGNORE_CASE)
    private val META_TAG = Regex("^\\[([a-zA-Z]+):(.*)]$")
    private val WORD_TAG = Regex("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>")

    data class Entry(
        val id: String,
        val title: String,
        val artist: String,
        val lyrics: String,
        val updatedAt: Long
    ) {
        /** 内容变了 recordId 就变，悬浮窗据此判断需不需要重新渲染。 */
        val recordId: String get() = "custom:$id:$updatedAt"
    }

    /** 同一时间戳下的第二行视为译文；没有时间轴的行会被忽略。 */
    data class Parsed(
        val lyrics: String,
        val translatedLyrics: String,
        val lineCount: Int,
        val translationCount: Int,
        val ignoredCount: Int
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun idOf(title: String, artist: String): String = MessageDigest.getInstance("SHA-256")
        .digest("${normalize(title)}\u0000${normalize(artist)}".toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString("") { "%02x".format(it) }

    fun all(context: Context): List<Entry> = prefs(context).all
        .filterKeys { it.startsWith(ENTRY_PREFIX) }
        .values
        .mapNotNull { (it as? String)?.let(::decode) }
        .sortedByDescending { it.updatedAt }

    fun get(context: Context, id: String): Entry? =
        prefs(context).getString(ENTRY_PREFIX + id, null)?.let(::decode)

    fun find(context: Context, title: String, artist: String): Entry? {
        if (normalize(title).isEmpty()) return null
        get(context, idOf(title, artist))?.let { return it }
        return if (normalize(artist).isNotEmpty()) get(context, idOf(title, "")) else null
    }

    /** 改了歌名或歌手时 id 会变，需要把旧条目一并删掉。 */
    fun save(context: Context, title: String, artist: String, lyrics: String, replacingId: String? = null): Entry {
        val entry = Entry(idOf(title, artist), title.trim(), artist.trim(), lyrics.take(MAX_LENGTH), System.currentTimeMillis())
        val editor = prefs(context).edit()
        if (replacingId != null && replacingId != entry.id) editor.remove(ENTRY_PREFIX + replacingId)
        editor.putString(ENTRY_PREFIX + entry.id, encode(entry)).apply()
        return entry
    }

    fun delete(context: Context, id: String) {
        prefs(context).edit().remove(ENTRY_PREFIX + id).apply()
    }

    fun parse(raw: String): Parsed {
        var offsetMs = 0L
        var ignored = 0
        // 按时间戳收集文本，保留出现顺序：第一条是原文，第二条是译文
        val texts = LinkedHashMap<Long, MutableList<String>>()
        raw.removePrefix(BOM).replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            OFFSET_TAG.matchEntire(line)?.let { match ->
                offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }
            // 只认行首连续的时间标签，例如 [00:12.30][01:02.00]副歌
            val stamps = mutableListOf<Long>()
            var cursor = 0
            while (true) {
                val match = TIME_TAG.find(line, cursor)?.takeIf { it.range.first == cursor } ?: break
                stamps += toMillis(match)
                cursor = match.range.last + 1
            }
            if (stamps.isEmpty()) {
                if (!META_TAG.matches(line)) ignored++
                return@forEach
            }
            val text = line.substring(cursor).replace(WORD_TAG, "").trim()
            stamps.forEach { texts.getOrPut(it) { mutableListOf() }.add(text) }
        }

        val main = StringBuilder()
        val translated = StringBuilder()
        var lineCount = 0
        var translationCount = 0
        for ((stamp, values) in texts.entries.sortedBy { it.key }) {
            // [offset:+500] 表示歌词整体提前 0.5 秒
            val time = formatTime((stamp - offsetMs).coerceAtLeast(0L))
            val nonBlank = values.filter { it.isNotBlank() }
            val original = nonBlank.firstOrNull().orEmpty()
            // 空文本的时间戳原样保留，主页用它判断一句在哪结束、间奏从哪开始
            main.append(time).append(original).append('\n')
            if (original.isNotEmpty()) lineCount++
            val translation = nonBlank.drop(1).joinToString(" / ")
            if (translation.isNotEmpty()) {
                translated.append(time).append(translation).append('\n')
                translationCount++
            }
        }
        return Parsed(main.toString().trimEnd(), translated.toString().trimEnd(), lineCount, translationCount, ignored)
    }

    /** 读取 LRC 里的 [ti:] / [ar:] 标签，导入文件时用来补全歌名和歌手。 */
    fun metadata(raw: String): Map<String, String> = raw.lineSequence()
        .mapNotNull { META_TAG.matchEntire(it.trim()) }
        .associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2].trim() }

    /** 与 DirectLyricsRepository.Result.toJson() 同形，悬浮窗可以直接当作歌词候选使用。 */
    fun toResultJson(entry: Entry, durationMs: Long): JSONObject {
        val parsed = parse(entry.lyrics)
        fun candidate() = JSONObject()
            .put("lyrics", parsed.lyrics)
            .put("translatedLyrics", parsed.translatedLyrics)
            .put("wordLyrics", "")
            .put("duration", durationMs)
            .put("cover", "")
            .put("source", SOURCE)
            .put("recordId", entry.recordId)
            .put("title", entry.title)
            .put("artist", entry.artist)
            .put("matchScore", 100)
            .put("custom", true)
        return candidate().put("alternatives", JSONArray().put(candidate()))
    }

    private fun toMillis(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
        val millis = if (fraction.isEmpty()) 0L else fraction.padEnd(3, '0').take(3).toLong()
        return minutes * 60_000L + seconds * 1_000L + millis
    }

    private fun formatTime(milliseconds: Long): String = "[%02d:%02d.%03d]".format(
        Locale.US, milliseconds / 60_000L, (milliseconds / 1_000L) % 60L, milliseconds % 1_000L
    )

    private fun encode(entry: Entry): String = JSONObject()
        .put("id", entry.id)
        .put("title", entry.title)
        .put("artist", entry.artist)
        .put("lyrics", entry.lyrics)
        .put("updatedAt", entry.updatedAt)
        .toString()

    private fun decode(raw: String): Entry? = runCatching {
        val json = JSONObject(raw)
        Entry(
            json.getString("id"),
            json.optString("title"),
            json.optString("artist"),
            json.optString("lyrics"),
            json.optLong("updatedAt")
        )
    }.getOrNull()?.takeIf { it.lyrics.isNotBlank() }
}
