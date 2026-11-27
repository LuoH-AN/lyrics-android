package com.luoh.music.lrc.data.lyrics

import com.luoh.music.lrc.data.lyrics.DirectLyricsRepository.Result
import org.json.JSONArray
import org.json.JSONObject

internal class NetEaseLyricsProvider(private val http: LyricsProviderHttpClient) {
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
            "Referer" to "https://music.163.com/",
            "User-Agent" to USER_AGENT
        )
        planLoop@ for (plan in searchPlans(track, artist)) {
            val searchTrack = plan.track
            val searchUrl = "https://music.163.com/api/search/get/web" +
                "?type=1&limit=${searchResultLimit(searchTrack)}&s=${encode(plan.query)}"
            val root = JSONObject(http.getText(searchUrl, headers))
            val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: continue
            val rejectedSongIds = mutableSetOf<Long>()
            var best: Result? = null
            var firstSong: JSONObject? = null
            var extraTried = false
            while (rejectedSongIds.size < MAX_LYRIC_CANDIDATES_PER_QUERY) {
                val match = firstJsonMatch(
                    songs,
                    track,
                    searchTrack,
                    artist,
                    allowRankFallback = plan.includesArtist,
                    isRematch = excludedRecords != null,
                    expectedDurationMs = expectedDurationMs,
                    candidateDurationMs = { it.optLong("duration", it.optLong("dt", 0L)) },
                    isUsable = { item ->
                        val id = item.optLong("id", 0L)
                        id > 0L && id !in rejectedSongIds && id.toString() !in excludedRecords.orEmpty()
                    }
                ) { item ->
                    val artists = (item.optJSONArray("artists") ?: item.optJSONArray("ar")).joinNames("name")
                    Triple(item.optString("name"), artists, item)
                } ?: run { if (best != null) return best; null } ?: continue@planLoop
                val song = match.value
                if (firstSong != null) {
                    if (extraTried || !sameRecording(
                        firstSong!!.optString("name"), song.optString("name"),
                        firstSong!!.optJSONArray("artists").joinNames("name"), song.optJSONArray("artists").joinNames("name"),
                        firstSong!!.optLong("duration", 0L), song.optLong("duration", 0L)
                    )) return best
                    extraTried = true
                }
                val score = match.score
                val album = song.optJSONObject("album") ?: song.optJSONObject("al")
                var cover = album?.optString("picUrl").orEmpty()
                val songId = song.optLong("id", 0L)

                if (cover.isBlank()) {
                    val detailUrl = "https://music.163.com/api/song/detail/?id=$songId&ids=[$songId]"
                    val detail = runCatching { JSONObject(http.getText(detailUrl, headers)) }.getOrNull()
                    val detailSong = detail?.optJSONArray("songs")?.optJSONObject(0)
                    cover = (detailSong?.optJSONObject("album") ?: detailSong?.optJSONObject("al"))
                        ?.optString("picUrl").orEmpty()
                }
                if (!includeLyrics) return Result(cover = cover, source = SOURCE, score = score)

                val lyricUrl = "https://music.163.com/api/song/lyric?os=pc&id=$songId" +
                    "&lv=-1&kv=-1&tv=-1&yv=-1&rv=-1"
                val lyricRoot = JSONObject(http.getText(lyricUrl, headers))
                val lyrics = lyricRoot.optJSONObject("lrc")?.optString("lyric").orEmpty()
                if (!isUsableLyrics(lyrics)) {
                    rejectedSongIds += songId
                    continue
                }
                val result = Result(
                    lyrics = lyrics,
                    translatedLyrics = lyricRoot.optJSONObject("tlyric")?.optString("lyric").orEmpty(),
                    wordLyrics = lyricRoot.optJSONObject("yrc")?.optString("lyric")
                        .orEmpty()
                        .ifBlank { lyricRoot.optJSONObject("klyric")?.optString("lyric").orEmpty() },
                    durationMs = song.optLong("duration", song.optLong("dt", 0L)),
                    cover = cover,
                    source = SOURCE,
                    recordId = songId.toString(),
                    title = song.optString("name"),
                    artist = song.optJSONArray("artists").joinNames("name"),
                    score = score + 5
                )
                onCandidate(result)
                if (best == null || qualityRank(result) > qualityRank(best!!)) best = result
                if (firstSong != null || !needsEnrichment(result)) return best
                firstSong = song
                rejectedSongIds += songId
                continue
            }
            if (best != null) return best
        }
        return null
    }

    companion object {
        const val SOURCE = "网易云音乐"
    }
}
