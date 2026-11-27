package com.luoh.music.lrc.data.lyrics

import android.util.Log
import com.luoh.music.lrc.data.lyrics.DirectLyricsRepository.Result
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.Locale

internal class QqMusicLyricsProvider(private val http: LyricsProviderHttpClient) {
    private data class QqRichLyrics(
        val lineLyrics: String,
        val translatedLyrics: String,
        val wordLyrics: String
    )

    fun query(
        track: String,
        artist: String,
        includeLyrics: Boolean,
        expectedDurationMs: Long = 0L,
        onCandidate: (Result) -> Unit = {},
        excludedRecords: Set<String>? = null
    ): Result? {
        val headers = mapOf(
            "Accept" to "application/json",
            "Referer" to "https://y.qq.com/",
            "User-Agent" to USER_AGENT
        )
        planLoop@ for (plan in searchPlans(track, artist)) {
            val searchTrack = plan.track
            val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/search_for_qq_cp" +
                "?format=json&p=1&n=${searchResultLimit(searchTrack)}&w=${encode(plan.query)}"
            val root = JSONObject(http.getText(searchUrl, headers))
            val songs = root.optJSONObject("data")
                ?.optJSONObject("song")
                ?.optJSONArray("list") ?: continue
            val rejectedSongMids = mutableSetOf<String>()
            var best: Result? = null
            var firstSong: JSONObject? = null
            var extraTried = false
            while (rejectedSongMids.size < MAX_LYRIC_CANDIDATES_PER_QUERY) {
                val match = firstJsonMatch(
                    songs,
                    track,
                    searchTrack,
                    artist,
                    allowRankFallback = plan.includesArtist,
                    isRematch = excludedRecords != null,
                    expectedDurationMs = expectedDurationMs,
                    candidateDurationMs = { it.optLong("interval", 0L) * 1000L },
                    isUsable = { item ->
                        val mid = item.optString("songmid")
                        mid !in rejectedSongMids && mid !in excludedRecords.orEmpty() &&
                            (!includeLyrics || (mid.isNotBlank() && mid != "0"))
                    }
                ) { item ->
                    val singers = item.optJSONArray("singer").joinNames("name")
                    Triple(item.optString("songname").ifBlank { item.optString("songorig") }, singers, item)
                } ?: run { if (best != null) return best; null } ?: continue@planLoop
                val song = match.value
                if (firstSong != null) {
                    if (extraTried || !sameRecording(
                        firstSong!!.optString("songname"), song.optString("songname"),
                        firstSong!!.optJSONArray("singer").joinNames("name"), song.optJSONArray("singer").joinNames("name"),
                        firstSong!!.optLong("interval", 0L) * 1000L, song.optLong("interval", 0L) * 1000L
                    )) return best
                    extraTried = true
                }
                val score = match.score
                val albumMid = song.optString("albummid")
                val albumId = song.optLong("albumid", 0L)
                val cover = when {
                    albumMid.isNotBlank() && !albumMid.all(Char::isDigit) ->
                        "https://y.gtimg.cn/music/photo_new/T002R800x800M000$albumMid.jpg"
                    albumId > 0L ->
                        "https://y.gtimg.cn/music/photo/album_500/${albumId % 100}/500_albumpic_${albumId}_0.jpg"
                    else -> ""
                }
                if (!includeLyrics) return Result(cover = cover, source = SOURCE, score = score)

                val songMid = song.optString("songmid")
                val richLyrics = song.optLong("songid", 0L).takeIf { it > 0L }?.let { songId ->
                    runCatching { queryQqRichLyrics(songId, headers, track) }
                        .onFailure { error ->
                            Log.w(
                                "DesktopLyrics",
                                "QQ QRC failed for $songId: ${error.javaClass.simpleName}: " +
                                    error.message.orEmpty().take(80)
                            )
                        }
                        .getOrNull()
                }
                if (richLyrics != null && isUsableLyrics(richLyrics.lineLyrics)) {
                    val result = Result(
                        lyrics = richLyrics.lineLyrics,
                        translatedLyrics = richLyrics.translatedLyrics,
                        wordLyrics = richLyrics.wordLyrics,
                        durationMs = song.optLong("interval", 0L) * 1000L,
                        cover = cover,
                        source = SOURCE,
                        recordId = songMid,
                        title = song.optString("songname"),
                        artist = song.optJSONArray("singer").joinNames("name"),
                        score = score + 5
                    )
                    onCandidate(result)
                    if (best == null || qualityRank(result) > qualityRank(best!!)) best = result
                    if (firstSong != null || !needsEnrichment(result)) return best
                    firstSong = song
                    rejectedSongMids += songMid
                    continue
                }

                val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
                    "?songmid=${encode(songMid)}&format=json&nobase64=1&trans=1"
                val lyricRoot = parseJsonFlexible(http.getBytes(lyricUrl, headers))
                val lyrics = lyricRoot?.optString("lyric")?.let(::unescapeHtml).orEmpty()
                if (!isUsableLyrics(lyrics) || isTitleOnlyLyrics(lyrics, track)) {
                    rejectedSongMids += songMid
                    continue
                }
                val result = Result(
                    lyrics = lyrics,
                    translatedLyrics = unescapeHtml(lyricRoot?.optString("trans").orEmpty()),
                    durationMs = song.optLong("interval", 0L) * 1000L,
                    cover = cover,
                    source = SOURCE,
                    recordId = songMid,
                    title = song.optString("songname"),
                    artist = song.optJSONArray("singer").joinNames("name"),
                    score = score + 5
                )
                onCandidate(result)
                if (best == null || qualityRank(result) > qualityRank(best!!)) best = result
                if (firstSong != null || !needsEnrichment(result)) return best
                firstSong = song
                rejectedSongMids += songMid
                continue
            }
            if (best != null) return best
        }
        return null
    }

    private fun queryQqRichLyrics(songId: Long, headers: Map<String, String>, track: String): QqRichLyrics? {
        val response = http.postFormText(
            "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg",
            headers + ("Referer" to "https://c.y.qq.com/"),
            mapOf(
                "version" to "15",
                "miniversion" to "82",
                "lrctype" to "4",
                "musicid" to songId.toString()
            )
        )
        if (!Regex("<result>\\s*0\\s*</result>").containsMatchIn(response)) return null

        val encryptedOriginal = extractQqTrack(response, "content")
        if (encryptedOriginal.isBlank()) return null
        val originalPayload = decodeQqTrack(encryptedOriginal)
        val original = extractQqLyricContent(originalPayload)
        val lineLyrics = qrcToLineLrc(original)
        if (!isUsableLyrics(lineLyrics) || isTitleOnlyLyrics(lineLyrics, track)) return null

        val translatedPayload = extractQqTrack(response, "contentts")
            .takeIf { it.isNotBlank() }
            ?.let(::decodeQqTrack)
            ?.let(::extractQqLyricContent)
            .orEmpty()
        val translated = cleanQqTranslation(qrcToLineLrc(translatedPayload))
        val hasWordTiming = Regex("^\\[\\d+,\\d+]", RegexOption.MULTILINE).containsMatchIn(original) &&
            Regex("\\(\\d+,\\d+(?:,\\d+)?\\)").containsMatchIn(original)

        return QqRichLyrics(
            lineLyrics = lineLyrics,
            translatedLyrics = translated,
            wordLyrics = if (hasWordTiming) qrcToCanonicalWordLyrics(original) else ""
        )
    }

    private fun extractQqTrack(response: String, tag: String): String {
        val safeTag = Regex.escape(tag)
        return Regex(
            "<$safeTag(?:\\s[^>]*)?><!\\[CDATA\\[(.*?)]]></$safeTag>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(response)?.groupValues?.getOrNull(1).orEmpty().trim()
    }

    private fun decodeQqTrack(payload: String): String {
        val normalized = payload.trim()
        return if (normalized.length >= 16 && normalized.length % 2 == 0 &&
            normalized.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        ) {
            QqQrcDecoder.decode(normalized)
        } else {
            normalized
        }
    }

    private fun extractQqLyricContent(payload: String): String {
        val content = Regex(
            "LyricContent=\\\"(.*?)\\\"",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(payload)?.groupValues?.getOrNull(1) ?: payload
        return unescapeHtml(content).replace("&#10;", "\n").replace("&#13;", "")
    }

    // QQ places each timestamp after its text; the renderer expects it before.
    private fun qrcToCanonicalWordLyrics(payload: String): String {
        val linePattern = Regex("^\\[(\\d+),(\\d+)\\](.*)$")
        val markerPattern = Regex("\\((\\d+),(\\d+)(?:,\\d+)?\\)")
        return payload.replace("\r", "").lineSequence().mapNotNull { raw ->
            val line = linePattern.matchEntire(raw.trim()) ?: return@mapNotNull null
            val body = line.groupValues[3]
            val markers = markerPattern.findAll(body).toList()
            if (markers.isEmpty()) return@mapNotNull null
            var cursor = 0
            val converted = buildString {
                for (marker in markers) {
                    val text = body.substring(cursor, marker.range.first)
                    append("(${marker.groupValues[1]},${marker.groupValues[2]})")
                    append(text)
                    cursor = marker.range.last + 1
                }
                append(body.substring(cursor))
            }
            "[${line.groupValues[1]},${line.groupValues[2]}]$converted"
        }.joinToString("\n")
    }

    private fun qrcToLineLrc(payload: String): String {
        if (payload.isBlank()) return ""
        val qrcLine = Regex("^\\[(\\d+),(\\d+)](.*)$")
        val wordMarker = Regex("\\(\\d+,\\d+(?:,\\d+)?\\)")
        return payload.replace("\r", "").lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            val match = qrcLine.matchEntire(line)
            if (match == null) {
                line.takeIf { Regex("^\\[\\d{1,3}:\\d{2}(?:[.:]\\d+)?]").containsMatchIn(it) }
            } else {
                val start = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val text = match.groupValues[3].replace(wordMarker, "").trim()
                if (text.isBlank()) null else "${formatLrcTime(start)}$text"
            }
        }.joinToString("\n")
    }

    private fun formatLrcTime(milliseconds: Long): String {
        val safe = milliseconds.coerceAtLeast(0L)
        val minutes = safe / 60_000L
        val seconds = (safe / 1_000L) % 60L
        val millis = safe % 1_000L
        return "[%02d:%02d.%03d]".format(Locale.US, minutes, seconds, millis)
    }

    private fun cleanQqTranslation(lyrics: String): String = lyrics.lineSequence()
        .filterNot { line ->
            val text = line.replace(Regex("^(\\[[^]]+])+"), "").trim()
            text == "//" || text.contains("QQ音乐享有本翻译作品的著作权") ||
                text.contains("TME享有本翻译作品的著作权")
        }
        .joinToString("\n")

    private fun parseJsonFlexible(bytes: ByteArray): JSONObject? {
        return runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrElse {
            runCatching { JSONObject(bytes.toString(Charset.forName("GBK"))) }.getOrNull()
        }
    }

    private fun unescapeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    companion object {
        const val SOURCE = "QQ音乐"
    }
}
