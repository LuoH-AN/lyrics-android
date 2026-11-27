package com.luoh.music.lrc.data.lyrics

import com.luoh.music.lrc.data.lyrics.DirectLyricsRepository.Result
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal class LrcLibProvider(private val http: LyricsProviderHttpClient) {
    private val artistAliasCache = ConcurrentHashMap<String, Set<String>>()

    private fun providerArtistAliases(artist: String): Set<String> {
        val key = normalize(artist)
        artistAliasCache[key]?.let { return it }
        val aliases = mutableSetOf(key)
        return runCatching {
            val root = JSONObject(http.getText(
                "https://music.163.com/api/search/get/web?type=100&limit=3&s=${encode(artist)}",
                mapOf("Referer" to "https://music.163.com/")
            ))
            val artists = root.optJSONObject("result")?.optJSONArray("artists") ?: return@runCatching aliases
            for (index in 0 until artists.length()) {
                val entry = artists.optJSONObject(index) ?: continue
                // Search ranking alone must never create a new artist identity.
                if (normalize(entry.optString("name")) != key) continue
                val names = entry.optJSONArray("alias") ?: entry.optJSONArray("alia") ?: continue
                for (i in 0 until names.length()) normalize(names.optString(i)).takeIf { it.isNotBlank() }?.let(aliases::add)
            }
            if (artistAliasCache.size >= 128) artistAliasCache.keys.firstOrNull()?.let(artistAliasCache::remove)
            artistAliasCache[key] = aliases.toSet()
            aliases.toSet()
        }.getOrDefault(aliases)
    }

    fun query(track: String, artist: String, expectedDurationMs: Long, album: String = "", excludedRecords: Set<String> = emptySet()): Result? {
        val headers = mapOf("Accept" to "application/json",
            "User-Agent" to "DesktopLyrics/1.0 (https://github.com/LuoH-AN/lyrics-android)")
        var best: Result? = null
        val requests = buildList {
            for ((index, plan) in searchPlans(track, artist).withIndex()) {
                val url = "https://lrclib.net/api/search?track_name=${encode(plan.track)}" +
                    if (artist.isNotBlank() && !plan.includesArtist) "" else "&artist_name=${encode(plan.artist)}"
                if (index == 0 && album.isNotBlank()) add(plan to "$url&album_name=${encode(album)}")
                add(plan to url)
            }
            if (track.isNotBlank()) {
                if (artist.isNotBlank()) add(SearchPlan(track, artist, "$track $artist", true) to
                    "https://lrclib.net/api/search?q=${encode("$artist $track")}")
                add(SearchPlan(track, "", track, false) to "https://lrclib.net/api/search?q=${encode(track)}")
            }
        }.distinctBy { it.second }
        for ((plan, requestUrl) in requests) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val broad = artist.isNotBlank() && !plan.includesArtist
            val list = JSONArray(http.getText(requestUrl, headers))
            val aliases = if (broad && list.length() > 0) providerArtistAliases(artist) else setOf(normalize(artist))
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                if (item.optLong("id").toString() in excludedRecords) continue
                val syncedLyrics = item.optString("syncedLyrics")
                val plainLyrics = item.optString("plainLyrics")
                val lyrics = if (isUsableLyrics(syncedLyrics)) syncedLyrics else plainLyrics
                if (!isUsableLyrics(lyrics)) continue
                val synced = isUsableLyrics(syncedLyrics)
                val seconds = item.optDouble("duration", 0.0)
                if (!seconds.isFinite() || seconds !in 0.0..86_400.0) continue
                val durationMs = (seconds * 1000.0).toLong()
                if (expectedDurationMs > 0L && durationMs > 0L &&
                    kotlin.math.abs(expectedDurationMs - durationMs) > 8000L) continue
                val candidateTrack = item.optString("trackName")
                val candidateArtist = item.optString("artistName")
                val aliasMatch = normalize(candidateArtist) in aliases
                val versionPenalty = versionMatchPenalty(track, artist, candidateTrack,
                    if (aliasMatch) artist else candidateArtist, expectedDurationMs, durationMs) ?: continue
                if (broad && (!aliasMatch || titleIdentityKey(track) != titleIdentityKey(candidateTrack) ||
                        expectedDurationMs <= 0 || kotlin.math.abs(expectedDurationMs - durationMs) > 8000L)) continue
                val score = maxOf(
                    platformMatchScore(track, artist, candidateTrack, if (aliasMatch) artist else candidateArtist,
                        expectedDurationMs, durationMs),
                    platformMatchScore(plan.track, artist, candidateTrack, if (aliasMatch) artist else candidateArtist,
                        expectedDurationMs, durationMs)
                ) + versionPenalty + if (synced) 5 else 0
                val result = Result(
                    lyrics = lyrics,
                    durationMs = durationMs,
                    source = SOURCE,
                    recordId = item.optLong("id").toString(),
                    title = candidateTrack,
                    artist = candidateArtist,
                    score = score
                )
                if (best == null || result.score > best.score) best = result
            }
            if (best != null && best.score >= MIN_ACCEPTABLE_SCORE) return best
        }
        return best
    }

    companion object {
        const val SOURCE = "LRCLIB"
    }
}
