package com.luoh.music.lrc.data.lyrics

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal class LocalizedSongIdentity(private val http: LyricsProviderHttpClient) {
    data class Identity(val track: String, val artist: String, val album: String)

    private val identityCache = ConcurrentHashMap<String, Identity>()

    /**
     * The iTunes catalogue is used only as an identity bridge after all lyric
     * providers fail. It is especially useful when a player exposes localized
     * English or Chinese metadata while lyric providers index Japanese names.
     * No preview audio or artwork is downloaded.
     */
    fun resolve(
        track: String,
        artist: String,
        album: String,
        expectedDurationMs: Long
    ): Identity? {
        if (expectedDurationMs <= 0L) return null
        val cacheKey = listOf(track, artist, album, (expectedDurationMs / 2_000L).toString())
            .joinToString("\u0000") { normalize(it) }
        identityCache[cacheKey]?.let { return it }

        val query = "$track $artist".trim()
        val url = "https://itunes.apple.com/search?term=${encode(query)}" +
            "&media=music&entity=song&limit=20&country=jp&lang=ja_jp"
        val root = JSONObject(
            http.getText(
                url,
                mapOf("Accept" to "application/json", "User-Agent" to USER_AGENT)
            )
        )
        val results = root.optJSONArray("results") ?: return null
        val ranked = buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                if (item.optString("kind") != "song") continue
                val candidateTrack = item.optString("trackName")
                val candidateArtist = item.optString("artistName")
                val candidateAlbum = item.optString("collectionName")
                val candidateDuration = item.optLong("trackTimeMillis", 0L)
                val durationDelta = kotlin.math.abs(expectedDurationMs - candidateDuration)
                if (candidateTrack.isBlank() || candidateArtist.isBlank() || durationDelta > 15_000L) continue

                val inputVersions = versionTags(track)
                val candidateVersions = versionTags(candidateTrack)
                val versionPenalty = if (candidateVersions.any { it !in inputVersions }) {
                    versionMatchPenalty(track, artist, candidateTrack, candidateArtist,
                        expectedDurationMs, candidateDuration) ?: continue
                } else 0

                val durationPoints = when (durationDelta) {
                    in 0L..3_000L -> 40
                    in 3_001L..8_000L -> 32
                    else -> 20
                }
                val titlePoints = (textSimilarity(coreTitle(track), coreTitle(candidateTrack)) * 35).toInt()
                val artistPoints = (textSimilarity(artist, candidateArtist) * 20).toInt()
                val albumPoints = (textSimilarity(album, candidateAlbum) * 20).toInt()
                val rankPoints = (10 - index).coerceAtLeast(0)
                add(
                    Pair(
                        Identity(candidateTrack, candidateArtist, candidateAlbum),
                        durationPoints + titlePoints + artistPoints + albumPoints + rankPoints + versionPenalty
                    )
                )
            }
        }.groupBy { "${normalize(it.first.track)}\u0000${normalize(it.first.artist)}" }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.second } }
            .sortedByDescending { it.second }

        val best = ranked.firstOrNull() ?: return null
        val secondScore = ranked.getOrNull(1)?.second ?: Int.MIN_VALUE
        if (best.second < IDENTITY_MIN_SCORE) return null
        if (secondScore != Int.MIN_VALUE && best.second < IDENTITY_STRONG_SCORE &&
            best.second - secondScore < IDENTITY_MIN_MARGIN
        ) return null
        if (identityCache.size >= 128) identityCache.keys.firstOrNull()?.let(identityCache::remove)
        identityCache[cacheKey] = best.first
        return best.first
    }

    private companion object {
        const val IDENTITY_MIN_SCORE = 50
        const val IDENTITY_STRONG_SCORE = 75
        const val IDENTITY_MIN_MARGIN = 5
    }
}
