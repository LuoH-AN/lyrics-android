package com.luoh.music.lrc.data.lyrics

import com.luoh.music.lrc.data.lyrics.DirectLyricsRepository.Result
import org.json.JSONArray
import org.json.JSONObject

internal class KuGouLyricsProvider(private val http: LyricsProviderHttpClient) {

    private data class KuGouRecording(val title: String, val artist: String, val durationMs: Long,
        val score: Int, val hash: String = "", val albumAudioId: String = "")

    fun query(track: String, artist: String, expectedDurationMs: Long,
        onCandidate: (Result) -> Unit = {}, excludedRecords: Set<String> = emptySet()): Result? {
        val headers = mapOf("Accept" to "application/json", "User-Agent" to USER_AGENT)
        val triedHashes = HashSet<String>()
        val triedDownloads = HashSet<String>()
        var attempts = 0
        fun matching(title: String, singer: String, duration: Long, hash: String = ""): KuGouRecording? {
            if (title.isBlank()) return null
            val versionPenalty = versionMatchPenalty(track, artist, title, singer, expectedDurationMs, duration) ?: return null
            if (expectedDurationMs > 0L && (duration <= 0L ||
                    kotlin.math.abs(expectedDurationMs - duration) > 8000L)) return null
            val score = platformMatchScore(track, artist, title, singer, expectedDurationMs, duration) + versionPenalty
            return KuGouRecording(title, singer, duration, score, hash).takeIf { score >= MIN_ACCEPTABLE_SCORE }
        }
        fun duration(raw: Long, reference: Long): Long {
            if (raw <= 0L || raw > 86_400_000L) return 0L
            // Some KuGou lyric records use seconds, others milliseconds. Choose
            // the unit closest to the already matched recording, never change LRC timestamps.
            return if (reference > 0L && raw <= 86_400L &&
                kotlin.math.abs(raw * 1000L - reference) < kotlin.math.abs(raw - reference)) raw * 1000L else raw
        }
        fun download(candidates: JSONArray, recording: KuGouRecording?, krc: Boolean = false): Result? {
            for (index in 0 until candidates.length()) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val candidate = candidates.optJSONObject(index) ?: continue
                val id = candidate.optString("download_id").ifBlank { candidate.optString("id") }
                val key = candidate.optString("accesskey")
                val downloadKey = "${if (krc) "krc" else "lrc"}:$id"
                if (id.isBlank() || key.isBlank() || id in excludedRecords || downloadKey in triedDownloads) continue
                val candidateDuration = duration(candidate.optLong("duration"), recording?.durationMs ?: expectedDurationMs)
                val resolvedDuration = candidateDuration.takeIf { it > 0L } ?: recording?.durationMs ?: 0L
                val title = candidate.optString("song")
                val singer = candidate.optString("singer")
                // Keyword records have returned song/singer in both orders. A
                // hash lookup may omit both; then use the validated song metadata.
                // KRC song names often omit (Live). Only a validated hash recording
                // may supply that missing suffix; conflicting tagged versions still fail.
                fun candidateMatch(candidateTitle: String, candidateArtist: String): KuGouRecording? {
                    matching(candidateTitle, candidateArtist, resolvedDuration)?.let { return it }
                    if (recording != null && versionTags(candidateTitle).isEmpty() &&
                        titleIdentityKey(candidateTitle) == titleIdentityKey(recording.title) &&
                        normalize(candidateArtist) == normalize(recording.artist)) return recording
                    return null
                }
                val named = listOfNotNull(candidateMatch(title, singer),
                    candidateMatch(singer, title)).maxByOrNull { it.score }
                if ((title.isNotBlank() || singer.isNotBlank()) && named == null) continue
                val matched = recording ?: named ?: continue
                if (candidateDuration > 0L && matched.durationMs > 0L &&
                    kotlin.math.abs(candidateDuration - matched.durationMs) > 8000L) continue
                if (expectedDurationMs > 0L && kotlin.math.abs(resolvedDuration - expectedDurationMs) > 8000L) continue
                if (krc && attempts >= MAX_LYRIC_CANDIDATES_PER_QUERY - 1) break
                if (attempts >= MAX_LYRIC_CANDIDATES_PER_QUERY) break
                attempts++
                triedDownloads.add(downloadKey)
                val root = try { JSONObject(http.getText((if (krc) "https://lyrics2.kugou.com/download" else "https://lyrics.kugou.com/download") +
                    "?fmt=${if (krc) "krc" else "lrc"}&charset=utf8&client=pc&ver=1&id=${encode(id)}&accesskey=${encode(key)}", headers))
                } catch (error: InterruptedException) { throw error }
                  catch (_: Exception) { continue }
                if (root.has("status") && root.optInt("status") != 200) continue
                val encoded = root.optString("content")
                if (encoded.isBlank() || encoded.equals("null", true)) continue
                var wordLyrics = ""
                val lyrics = try {
                    val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                    if (krc) {
                        val decoded = KugouKrcDecoder.decode(bytes)
                        wordLyrics = decoded.wordLyrics
                        decoded.lineLyrics
                    } else Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
                } catch (error: InterruptedException) { throw error }
                  catch (_: Exception) { continue }
                if (!Regex("(?m)^\\[\\d+:\\d+(?:\\.\\d+)?]").containsMatchIn(lyrics) ||
                    !isUsableLyrics(lyrics) || isTitleOnlyLyrics(lyrics, track)) continue
                return Result(lyrics = lyrics, wordLyrics = wordLyrics, durationMs = resolvedDuration, source = SOURCE,
                    recordId = id, title = matched.title, artist = matched.artist, score = matched.score).also(onCandidate)
            }
            return null
        }
        for (plan in searchPlans(track, artist)) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val root = JSONObject(http.getText("https://mobileservice.kugou.com/api/v3/search/song" +
                "?version=9108&plat=0&pagesize=8&showtype=0&keyword=${encode(plan.query)}", headers))
            val songs = root.optJSONObject("data")?.optJSONArray("info") ?: JSONArray()
            val recordings = (0 until songs.length()).mapNotNull { index ->
                val song = songs.optJSONObject(index) ?: return@mapNotNull null
                val seconds = song.optDouble("duration")
                val songDuration = if (seconds.isFinite() && seconds in 0.0..86_400.0) (seconds * 1000).toLong() else 0L
                matching(song.optString("songname"), song.optString("singername"), songDuration, song.optString("hash"))
                    ?.copy(albumAudioId = song.optString("album_audio_id"))
            }.sortedByDescending { it.score }.distinctBy { it.hash }.take(MAX_LYRIC_CANDIDATES_PER_QUERY)
            for (recording in recordings) {
                if (recording.hash.isBlank() || !triedHashes.add(recording.hash)) continue
                val rich = try { JSONObject(http.getText("https://krcs.kugou.com/search" +
                    "?ver=1&man=no&client=pc&lrctxt=1&hash=${encode(recording.hash)}" +
                    "&keyword=${encode("${recording.artist} - ${recording.title}")}&duration=${recording.durationMs}" +
                    "&album_audio_id=${encode(recording.albumAudioId)}", headers))
                } catch (error: InterruptedException) { throw error }
                  catch (_: Exception) { null }
                rich?.let { download(it.optJSONArray("candidates") ?: JSONArray(), recording, krc = true) }
                    ?.let { return it }
                if (attempts >= MAX_LYRIC_CANDIDATES_PER_QUERY) return null
                val found = JSONObject(http.getText("https://lyrics.kugou.com/search" +
                    "?ver=1&man=yes&client=pc&hash=${encode(recording.hash)}", headers))
                download(found.optJSONArray("candidates") ?: JSONArray(), recording)?.let { return it }
            }
            val query = if (plan.includesArtist) "${plan.track} - ${plan.artist}" else plan.track
            val rich = try { JSONObject(http.getText("https://krcs.kugou.com/search?ver=1&man=no&client=pc&lrctxt=1" +
                "&keyword=${encode(query)}" + if (expectedDurationMs > 0L) "&duration=$expectedDurationMs" else "", headers))
            } catch (error: InterruptedException) { throw error }
              catch (_: Exception) { null }
            rich?.let { download(it.optJSONArray("candidates") ?: JSONArray(), null, krc = true) }
                ?.let { return it }
            if (attempts >= MAX_LYRIC_CANDIDATES_PER_QUERY) return null
            val found = JSONObject(http.getText("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc" +
                "&keyword=${encode(query)}" + if (expectedDurationMs > 0L) "&duration=$expectedDurationMs" else "", headers))
            download(found.optJSONArray("candidates") ?: JSONArray(), null)?.let { return it }
            if (attempts >= MAX_LYRIC_CANDIDATES_PER_QUERY) return null
        }
        return null
    }

    companion object {
        const val SOURCE = "酷狗音乐"
    }
}
