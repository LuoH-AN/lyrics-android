package com.luoh.music.lrc.data.lyrics

import android.icu.text.Transliterator
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

internal const val MIN_ACCEPTABLE_SCORE = 50
internal const val EXACT_MATCH_SCORE = 95

// Providers run concurrently; ICU transliterators must not be shared across threads.
private val toSimplified = ThreadLocal.withInitial { Transliterator.getInstance("Hant-Hans") }
private val toTraditional = ThreadLocal.withInitial { Transliterator.getInstance("Hans-Hant") }

private fun simplifiedChinese(value: String): String = toSimplified.get()!!.transliterate(value)
private fun traditionalChinese(value: String): String = toTraditional.get()!!.transliterate(value)

private val latinTransliterator = ThreadLocal.withInitial {
    Transliterator.getInstance("Any-Latin; NFD; [:Nonspacing Mark:] Remove; NFC")
}

internal data class JsonMatch(val value: JSONObject, val score: Int)
internal data class SearchPlan(val track: String, val artist: String, val query: String, val includesArtist: Boolean)

internal fun firstJsonMatch(
    array: JSONArray,
    track: String,
    searchTrack: String,
    artist: String,
    allowRankFallback: Boolean = true,
    isRematch: Boolean = false,
    expectedDurationMs: Long = 0L,
    candidateDurationMs: (JSONObject) -> Long = { 0L },
    isUsable: (JSONObject) -> Boolean = { true },
    fields: (JSONObject) -> Triple<String, String, JSONObject>
): JsonMatch? {
    var best: JSONObject? = null
    var bestScore = Int.MIN_VALUE
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        if (!isUsable(item)) continue
        val (title, singer, value) = fields(item)
        val versionPenalty = versionMatchPenalty(track, artist, title, singer,
            expectedDurationMs, candidateDurationMs(item)) ?: continue
        val originalScore = platformMatchScore(
            track, artist, title, singer, expectedDurationMs, candidateDurationMs(item)
        )
        val variantScore = if (normalize(searchTrack) != normalize(track)) {
            platformMatchScore(
                searchTrack, artist, title, singer, expectedDurationMs, candidateDurationMs(item)
            )
        } else {
            originalScore
        }
        val rankScore = if (allowRankFallback) rankedSearchFallbackScore(
            searchTrack,
            artist,
            title,
            singer,
            expectedDurationMs,
            candidateDurationMs(item),
            index,
            isRematch
        ) else 0
        val score = maxOf(
            originalScore,
            variantScore,
            rankScore
        ) + versionPenalty
        if (score >= MIN_ACCEPTABLE_SCORE && score > bestScore) {
            best = value
            bestScore = score
        }
    }
    return best?.let { JsonMatch(it, bestScore) }
}

private fun searchTrackVariants(track: String): List<String> {
    val stripped = track
        .replace(Regex("\\s*[（(][^）)]*[）)]\\s*"), " ")
        .replace(Regex("\\s*[【\\[].*?[】\\]]\\s*"), " ")
        .trim()
    return listOf(track.trim(), stripped)
        .filter { it.isNotBlank() }
        .distinct()
}

internal fun searchPlans(track: String, artist: String): List<SearchPlan> {
    val variants = searchTrackVariants(track).flatMap { title ->
        listOf(
            title to artist.trim(),
            simplifiedChinese(title) to simplifiedChinese(artist).trim(),
            traditionalChinese(title) to traditionalChinese(artist).trim()
        )
    }.distinct()
    return buildList {
        variants.forEach { (title, singer) ->
            add(SearchPlan(title, singer, "$title $singer".trim(), includesArtist = singer.isNotBlank()))
        }
        if (artist.isNotBlank()) {
            variants.forEach { (title, _) ->
                add(SearchPlan(title, "", title, includesArtist = false))
            }
        }
        // Do not normalize here: simplified and traditional queries must both survive.
    }.distinctBy { it.query }
}

internal fun coreTitle(value: String): String = value
    .replace(Regex("\\s*[（(][^）)]*[）)]\\s*"), " ")
    .replace(Regex("\\s*[【\\[].*?[】\\]]\\s*"), " ")
    .replace(Regex("\\s*(?:-|/)?\\s*(?:feat\\.?|ft\\.?|with)\\s+.+$", RegexOption.IGNORE_CASE), " ")
    .trim()

internal fun textSimilarity(first: String, second: String): Double {
    val left = normalize(first)
    val right = normalize(second)
    if (left.isBlank() || right.isBlank()) return 0.0
    if (left == right) return 1.0
    if (left in right || right in left) {
        return minOf(left.length, right.length).toDouble() / maxOf(left.length, right.length)
    }
    return latinSimilarity(first, second).coerceAtLeast(
        1.0 - editDistance(left, right).toDouble() / maxOf(left.length, right.length).toDouble()
    )
}

internal fun versionTags(value: String): Set<String> {
    val normalized = simplifiedChinese(Normalizer.normalize(value, Normalizer.Form.NFKC)).lowercase(Locale.ROOT)
    return buildSet {
        if (Regex("\\blive\\b|现场|ライヴ|ライブ").containsMatchIn(normalized)) add("live")
        if (Regex("instrumental|伴奏|纯音[乐楽]|off\\s*vocal").containsMatchIn(normalized)) add("instrumental")
        if (Regex("remix|リミックス").containsMatchIn(normalized)) add("remix")
        if (Regex("acoustic|アコースティック").containsMatchIn(normalized)) add("acoustic")
        if (Regex("karaoke|カラオケ").containsMatchIn(normalized)) add("karaoke")
    }
}

/** Missing Live metadata is common. Treat it as a preference when the recording agrees. */
internal fun versionMatchPenalty(
    track: String, artist: String, candidateTrack: String, candidateArtist: String,
    expectedDurationMs: Long, candidateDurationMs: Long
): Int? {
    val requested = versionTags(track)
    val found = versionTags(candidateTrack)
    if (requested == found) return 0
    if (requested.isNotEmpty() || found != setOf("live")) return null
    val title = titleIdentityKey(track)
    if (title.isBlank() || title != titleIdentityKey(candidateTrack)) return null
    val singer = normalize(artist)
    val singers = candidateArtist.split(Regex("[/、,&;＆，；]"))
    if (singer.isNotBlank() && singers.none { normalize(it) == singer }) return null
    if (expectedDurationMs > 0L) {
        if (candidateDurationMs <= 0L ||
            kotlin.math.abs(expectedDurationMs - candidateDurationMs) > 8_000L) return null
    } else if (singer.isBlank()) return null
    // A small preference for matching labels must not outweigh an exact artist match.
    return -2
}

internal fun searchResultLimit(track: String): Int {
    val searchableLength = normalize(track).length
    return if (searchableLength <= 3) 30 else 12
}

/**
 * Provider rank is useful evidence only when duration is very close. It is
 * deliberately combined with either the queried title or the artist, so a
 * random first result can never pass on rank alone.
 */
private fun rankedSearchFallbackScore(
    searchTrack: String,
    artist: String,
    candidateTrack: String,
    candidateArtist: String,
    expectedDurationMs: Long,
    candidateDurationMs: Long,
    resultIndex: Int,
    isRematch: Boolean
): Int {
    if (expectedDurationMs <= 0L || candidateDurationMs <= 0L) return 0
    val durationDelta = kotlin.math.abs(expectedDurationMs - candidateDurationMs)
    if (durationDelta > 8_000L) return 0

    val wantedTitle = titleIdentityKey(searchTrack)
    val foundTitle = titleIdentityKey(candidateTrack)
    val titleExact = wantedTitle.isNotBlank() && wantedTitle == foundTitle
    val wantedArtist = normalize(artist)
    val foundArtist = normalize(candidateArtist)
    val artistStrong = wantedArtist.isNotBlank() && foundArtist.isNotBlank() &&
        (wantedArtist == foundArtist || wantedArtist in foundArtist || foundArtist in wantedArtist ||
            latinSimilarity(artist, candidateArtist) >= 0.58)
    val durationBonus = durationScore(expectedDurationMs, candidateDurationMs)

    // Handles provider-localized stage names and symbolic titles such as
    // "MIREI" -> "當山みれい" and "^^", without accepting arbitrary hits.
    if (titleExact && resultIndex <= 8 &&
        (artistStrong || !(containsCjk(artist) && containsCjk(candidateArtist)))) {
        return 82 + durationBonus - resultIndex.coerceAtMost(8)
    }
    // Handles localized titles such as "Till I Know What Love Is" ->
    // "愛を知るまでは" when the artist, duration and top search rank agree.
    if (artistStrong && resultIndex <= 2 &&
        !(containsCjk(searchTrack) && containsCjk(candidateTrack)) &&
        !isRematch) {
        return 84 + durationBonus - resultIndex
    }
    return 0
}

internal fun matchScore(track: String, artist: String, candidateTrack: String, candidateArtist: String): Int {
    val wantedTrack = normalize(track)
    val foundTrack = normalize(candidateTrack)
    val wantedArtist = normalize(artist)
    val foundArtist = normalize(candidateArtist)
    if (wantedTrack.isBlank() || foundTrack.isBlank()) return 0
    if (
        wantedArtist.isNotBlank() &&
        foundArtist.isNotBlank() &&
        wantedArtist !in foundArtist &&
        foundArtist !in wantedArtist
    ) return 0

    val titleScore = when {
        wantedTrack == foundTrack -> 80
        wantedTrack.length >= 4 && (wantedTrack in foundTrack || foundTrack in wantedTrack) -> 62
        commonPrefixRatio(wantedTrack, foundTrack) >= 0.72 -> 50
        else -> 0
    }
    val artistScore = when {
        wantedArtist.isBlank() -> 12
        wantedArtist == foundArtist -> 20
        wantedArtist.length >= 2 && (wantedArtist in foundArtist || foundArtist in wantedArtist) -> 17
        else -> 0
    }
    return titleScore + artistScore
}

internal fun platformMatchScore(
    track: String,
    artist: String,
    candidateTrack: String,
    candidateArtist: String,
    expectedDurationMs: Long = 0L,
    candidateDurationMs: Long = 0L
): Int {
    val strict = matchScore(track, artist, candidateTrack, candidateArtist)
    val durationBonus = durationScore(expectedDurationMs, candidateDurationMs)
    if (strict >= MIN_ACCEPTABLE_SCORE) return strict + durationBonus

    val wantedTitle = titleIdentityKey(track)
    val foundTitle = titleIdentityKey(candidateTrack)
    val titleExact = wantedTitle.isNotBlank() && wantedTitle == foundTitle
    val titleLatinSimilarity = latinSimilarity(track, candidateTrack)
    val artistLatinSimilarity = latinSimilarity(artist, candidateArtist)
    val durationReliable = expectedDurationMs > 0L && candidateDurationMs > 0L &&
        kotlin.math.abs(expectedDurationMs - candidateDurationMs) <= 12_000L

    if (!durationReliable) return strict
    if (titleExact && artistLatinSimilarity >= 0.58) return 88 + durationBonus
    if (titleLatinSimilarity >= 0.56 &&
        (normalize(artist) == normalize(candidateArtist) || artistLatinSimilarity >= 0.58)
    ) return 84 + durationBonus
    return strict
}

private fun containsCjk(value: String): Boolean =
    Regex("[\\u3040-\\u30FF\\u3400-\\u9FFF\\uAC00-\\uD7AF]").containsMatchIn(value)

private fun durationScore(expectedMs: Long, candidateMs: Long): Int {
    if (expectedMs <= 0L || candidateMs <= 0L) return 0
    return when (kotlin.math.abs(expectedMs - candidateMs)) {
        in 0L..3_000L -> 12
        in 3_001L..8_000L -> 8
        in 8_001L..15_000L -> 4
        in 15_001L..35_000L -> -5
        else -> -25
    }
}

private fun latinSimilarity(first: String, second: String): Double {
    val left = normalize(latinTransliterator.get()!!.transliterate(first))
    val right = normalize(latinTransliterator.get()!!.transliterate(second))
    if (left.isBlank() || right.isBlank()) return 0.0
    if (left == right) return 1.0
    val distance = editDistance(left, right)
    return 1.0 - distance.toDouble() / maxOf(left.length, right.length).toDouble()
}

private fun editDistance(first: String, second: String): Int {
    var previous = IntArray(second.length + 1) { it }
    for (i in first.indices) {
        val current = IntArray(second.length + 1)
        current[0] = i + 1
        for (j in second.indices) {
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + if (first[i] == second[j]) 0 else 1
            )
        }
        previous = current
    }
    return previous[second.length]
}

internal fun normalize(value: String): String = simplifiedChinese(Normalizer.normalize(value, Normalizer.Form.NFKC))
    .lowercase(Locale.ROOT)
    .replace(Regex("[（(\\[].*?(live|现场|ライヴ|ライブ|remaster|版|伴奏|纯音乐|翻唱).*?[）)\\]]", RegexOption.IGNORE_CASE), "")
    .replace(Regex("[^\\p{L}\\p{N}]"), "")

internal fun titleIdentityKey(value: String): String {
    val alphanumeric = normalize(value)
    if (alphanumeric.isNotBlank()) return alphanumeric
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), "")
}

private fun commonPrefixRatio(first: String, second: String): Double {
    val limit = minOf(first.length, second.length)
    var same = 0
    while (same < limit && first[same] == second[same]) same++
    return same.toDouble() / maxOf(first.length, second.length).toDouble()
}

internal fun sameRecording(title: String, otherTitle: String, artist: String, otherArtist: String,
                          duration: Long, otherDuration: Long): Boolean =
    titleIdentityKey(title) == titleIdentityKey(otherTitle) &&
        normalize(artist).isNotBlank() && normalize(artist) == normalize(otherArtist) &&
        versionTags(title) == versionTags(otherTitle) && duration > 0 && otherDuration > 0 &&
        kotlin.math.abs(duration - otherDuration) <= 3000L
