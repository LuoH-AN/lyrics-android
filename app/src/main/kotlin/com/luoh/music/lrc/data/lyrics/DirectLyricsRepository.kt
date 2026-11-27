package com.luoh.music.lrc.data.lyrics

import android.os.SystemClock
import android.util.Log
import com.luoh.music.lrc.diagnostics.UsageAnalytics
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Resolves lyrics directly from public music services. No request is routed through
 * a Lobsta/Tcrrry-owned server. MediaSession artwork remains the preferred cover;
 * QQ Music and NetEase artwork are only used when the player did not publish one.
 */
class DirectLyricsRepository(fetchBytes: ((String, Map<String, String>) -> ByteArray)? = null) {
    data class Result(
        val lyrics: String = "",
        val translatedLyrics: String = "",
        val wordLyrics: String = "",
        val durationMs: Long = 0L,
        val cover: String = "",
        val source: String = "",
        val recordId: String = "",
        val title: String = "",
        val artist: String = "",
        val score: Int = 0,
        val alternatives: List<Result> = emptyList()
    ) {
        private fun candidateJson(): JSONObject = JSONObject()
            .put("lyrics", lyrics)
            .put("translatedLyrics", translatedLyrics)
            .put("wordLyrics", wordLyrics)
            .put("duration", durationMs)
            .put("cover", cover)
            .put("source", source)
            .put("recordId", recordId)
            .put("title", title)
            .put("artist", artist)
            .put("matchScore", score)

        /** The matching engine needs the primary candidate in its list; persistent caches do not. */
        fun toJson(includePrimary: Boolean = true): JSONObject = candidateJson().put(
            "alternatives",
            JSONArray().apply {
                if (includePrimary) put(candidateJson())
                alternatives.forEach { put(it.candidateJson()) }
            }
        )
    }

    private val http = LyricsProviderHttpClient(fetchBytes)
    private val lrcLib = LrcLibProvider(http)
    private val kuGou = KuGouLyricsProvider(http)
    private val qqMusic = QqMusicLyricsProvider(http)
    private val netEase = NetEaseLyricsProvider(http)
    private val localizedIdentity = LocalizedSongIdentity(http)

    fun rematch(
        source: String,
        track: String,
        artist: String,
        album: String,
        durationMs: Long,
        excluded: Set<String>
    ): Result? {
        val excludedRecords = excluded.toSet()
        fun query(title: String, singer: String): Result? = when (source) {
            QqMusicLyricsProvider.SOURCE -> qqMusic.query(title, singer, true, durationMs, excludedRecords = excludedRecords)
            NetEaseLyricsProvider.SOURCE -> netEase.query(title, singer, true, durationMs, excludedRecords = excludedRecords)
            LrcLibProvider.SOURCE -> lrcLib.query(title, singer, durationMs, album, excludedRecords)
            KuGouLyricsProvider.SOURCE -> kuGou.query(title, singer, durationMs, excludedRecords = excludedRecords)
            else -> null
        }

        val direct = query(track, artist)
        if (direct != null && direct.score >= MIN_ACCEPTABLE_SCORE) return direct
        val identity = localizedIdentity.resolve(track, artist, album, durationMs) ?: return null
        return query(identity.track, identity.artist)?.takeIf { it.score >= MIN_ACCEPTABLE_SCORE }
    }

    private val executor = Executors.newFixedThreadPool(6) { runnable ->
        Thread(runnable, "direct-lyrics").apply { isDaemon = true }
    }

    fun resolveLyrics(
        track: String,
        artist: String,
        album: String = "",
        expectedDurationMs: Long = 0L,
        onPartial: (Result) -> Unit = {}
    ): Result {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val result = resolveLyricsFromProviders(track, artist, album, expectedDurationMs, onPartial)
            UsageAnalytics.lyricsLookup(result.source,
                if (isUsableLyrics(result.lyrics)) UsageAnalytics.Result.SUCCESS else UsageAnalytics.Result.NOT_FOUND,
                SystemClock.elapsedRealtime() - startedAt)
            result
        } catch (error: Exception) {
            UsageAnalytics.lyricsLookup("",
                if (error is InterruptedException || Thread.currentThread().isInterrupted)
                    UsageAnalytics.Result.CANCELLED else UsageAnalytics.Result.ERROR,
                SystemClock.elapsedRealtime() - startedAt, UsageAnalytics.failure(error))
            throw error
        }
    }

    private fun resolveLyricsFromProviders(
        track: String,
        artist: String,
        album: String,
        expectedDurationMs: Long,
        onPartial: (Result) -> Unit
    ): Result {
        val direct = resolveFromProviders(track, artist, expectedDurationMs, album = album)
        val current = (listOf(direct) + direct.alternatives).filter { isUsableLyrics(it.lyrics) }
        // A usable lyric is not proof that all providers found the correct identity.
        val incomplete = current.size < LYRIC_SOURCES.size || current.any(::needsEnrichment)
        if (current.isNotEmpty() && !incomplete) return direct
        if (current.isNotEmpty()) onPartial(direct)

        val identity = runCatching {
            localizedIdentity.resolve(track, artist, album, expectedDurationMs)
        }.onFailure { error ->
            Log.w(
                LOG_TAG,
                "Lyrics identity bridge failed for ${track.take(48)}: " +
                    "${error.javaClass.simpleName}: ${error.message.orEmpty().take(80)}"
            )
        }.getOrNull() ?: return direct
        if (normalize(identity.track) == normalize(track) &&
            normalize(identity.artist) == normalize(artist)
        ) return direct

        Log.i(
            LOG_TAG,
            "Lyrics identity bridge ${track.take(40)} / ${artist.take(32)} -> " +
                "${identity.track.take(40)} / ${identity.artist.take(32)}"
        )
        val retrySources = LYRIC_SOURCES.filter { source ->
            current.none { it.source == source } || current.any { it.source == source && needsEnrichment(it) }
        }.toSet()
        val retried = resolveFromProviders(identity.track, identity.artist, expectedDurationMs, retrySources, album)
        val combined = (current + retried + retried.alternatives)
            .filter { isUsableLyrics(it.lyrics) }
            .sortedByDescending(::qualityRank).distinctBy { it.source }
        val best = combined.firstOrNull() ?: return direct
        return best.copy(alternatives = combined.drop(1).map { it.copy(alternatives = emptyList()) })
    }

    private fun resolveFromProviders(track: String, artist: String, expectedDurationMs: Long,
                                     sources: Set<String> = LYRIC_SOURCES, album: String = ""): Result {
        val available = ConcurrentHashMap<String, Result>()
        val remember: (Result) -> Unit = { result ->
            available.compute(result.source) { _, previous ->
                if (previous == null || qualityRank(result) > qualityRank(previous)) result else previous
            }
        }
        val completion = ExecutorCompletionService<Result?>(executor)
        val futures = listOfNotNull(
            runCatching { completion.submit(Callable {
                if (LrcLibProvider.SOURCE in sources) querySource("LRCLIB", track) { lrcLib.query(track, artist, expectedDurationMs, album) } else null
            }) }.getOrNull(),
            runCatching { completion.submit(Callable {
                if (QqMusicLyricsProvider.SOURCE in sources) querySource("QQ", track) {
                    qqMusic.query(track, artist, includeLyrics = true, expectedDurationMs, remember)
                } else null
            }) }.getOrNull(),
            runCatching { completion.submit(Callable {
                if (NetEaseLyricsProvider.SOURCE in sources) querySource("NetEase", track) {
                    netEase.query(track, artist, includeLyrics = true, expectedDurationMs, remember)
                } else null
            }) }.getOrNull(),
            runCatching { completion.submit(Callable {
                if (KuGouLyricsProvider.SOURCE in sources) querySource("KuGou", track) {
                    kuGou.query(track, artist, expectedDurationMs, remember)
                } else null
            }) }.getOrNull()
        )
        if (futures.isEmpty()) return Result()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LYRICS_DEADLINE_MS)
        val candidates = mutableListOf<Result>()
        var completed = 0
        var firstCandidateAt = 0L

        try {
            while (completed < futures.size) {
                val candidateDeadline = if (firstCandidateAt == 0L) {
                    deadline
                } else {
                    minOf(deadline, firstCandidateAt + TimeUnit.MILLISECONDS.toNanos(SOURCE_GRACE_MS))
                }
                val remaining = candidateDeadline - System.nanoTime()
                if (remaining <= 0L) break
                val future = completion.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                completed++
                val candidate = runCatching { future.get() }.getOrNull()
                    ?.takeIf { isUsableLyrics(it.lyrics) && it.score >= MIN_ACCEPTABLE_SCORE }
                if (candidate != null) {
                    candidates += candidate
                    if (firstCandidateAt == 0L) firstCandidateAt = System.nanoTime()
                }
            }
        } finally {
            futures.forEach { it.cancel(true) }
        }
        val ranked = (candidates + available.values.toList())
            .sortedByDescending(::qualityRank)
            .distinctBy { it.source }
            .distinctBy { "${it.source}\u0000${it.lyrics}" }
            .sortedByDescending(::qualityRank)
        val primary = ranked.firstOrNull() ?: return Result()
        return primary.copy(alternatives = ranked.drop(1))
    }

    fun resolveCover(track: String, artist: String): String {
        val completion = ExecutorCompletionService<Result?>(executor)
        val futures = listOfNotNull(
            runCatching {
                completion.submit(Callable { qqMusic.query(track, artist, includeLyrics = false) })
            }.getOrNull(),
            runCatching {
                completion.submit(Callable { netEase.query(track, artist, includeLyrics = false) })
            }.getOrNull()
        )
        if (futures.isEmpty()) return ""
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(COVER_DEADLINE_MS)
        var best: Result? = null

        try {
            repeat(futures.size) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) return@repeat
                val future = completion.poll(remaining, TimeUnit.NANOSECONDS) ?: return@repeat
                val candidate = runCatching { future.get() }.getOrNull()
                    ?.takeIf { it.cover.isNotBlank() && it.score >= MIN_ACCEPTABLE_SCORE }
                if (candidate != null && (best == null || candidate.score > best!!.score)) {
                    best = candidate
                }
                if (candidate != null && candidate.score >= EXACT_MATCH_SCORE) {
                    return candidate.cover
                }
            }
        } finally {
            futures.forEach { it.cancel(true) }
        }
        return best?.cover.orEmpty()
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun querySource(source: String, track: String, block: () -> Result?): Result? {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val result = block()
            UsageAnalytics.lyricsResult(source,
                if (result != null && isUsableLyrics(result.lyrics) && result.score >= MIN_ACCEPTABLE_SCORE)
                    UsageAnalytics.Result.SUCCESS else UsageAnalytics.Result.NOT_FOUND,
                SystemClock.elapsedRealtime() - startedAt)
            Log.d(
                LOG_TAG,
                "Lyrics provider=$source track=${track.take(48)} " +
                    "candidate=${result != null} score=${result?.score ?: 0}"
            )
            result
        } catch (error: Throwable) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            UsageAnalytics.lyricsResult(source,
                if (Thread.currentThread().isInterrupted) UsageAnalytics.Result.CANCELLED else UsageAnalytics.Result.ERROR,
                SystemClock.elapsedRealtime() - startedAt, UsageAnalytics.failure(error))
            Log.w(
                LOG_TAG,
                "Lyrics provider=$source track=${track.take(48)} failed: " +
                    "${error.javaClass.simpleName}: ${error.message.orEmpty().take(80)}"
            )
            null
        }
    }

    private companion object {
        val LYRIC_SOURCES = setOf(
            NetEaseLyricsProvider.SOURCE, QqMusicLyricsProvider.SOURCE, LrcLibProvider.SOURCE, KuGouLyricsProvider.SOURCE
        )
        const val LOG_TAG = "DesktopLyrics"
        const val LYRICS_DEADLINE_MS = 10_000L
        const val SOURCE_GRACE_MS = 9_000L
        const val COVER_DEADLINE_MS = 6_000L
    }
}
