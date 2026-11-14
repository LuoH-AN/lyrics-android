package com.luoh.music.lrc

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class DirectLyricsProvidersTest {
    private val lyrics = "[00:01.25]Hello world\n[00:03.50]Stay with me"
    private fun song(hash: String, title: String = "Song", artist: String = "Artist", seconds: Int = 200) =
        JSONObject().put("hash", hash).put("songname", title).put("singername", artist).put("duration", seconds)
    private fun candidate(id: String, duration: Long = 200_000, swapped: Boolean = false) =
        JSONObject().put("id", id).put("accesskey", "public-key-$id").put("duration", duration)
            .put("song", if (swapped) "Artist" else "Song").put("singer", if (swapped) "Song" else "Artist")
    private fun songs(vararg items: JSONObject) = JSONObject().put("status", 1)
        .put("data", JSONObject().put("info", JSONArray(items.toList()))).toString()
    private fun candidates(vararg items: JSONObject) = JSONObject().put("status", 200)
        .put("candidates", JSONArray(items.toList())).toString()
    private fun content(text: String = lyrics) = JSONObject().put("content",
        Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)).toString()
    private fun lrcRecord(title: String = "Song", artist: String = "Artist", seconds: Int = 200,
        synced: String = lyrics, plain: String = "") = JSONObject().put("id", 11).put("trackName", title)
        .put("artistName", artist).put("duration", seconds).put("syncedLyrics", synced).put("plainLyrics", plain)

    @Test fun kugouMatchesRecordingBeforeHashLookupAndPreservesTheOriginalLrc() {
        val calls = mutableListOf<Uri>()
        val repository = DirectLyricsRepository { url, _ ->
            val uri = Uri.parse(url); calls.add(uri)
            val response = when (uri.path) {
                "/api/v3/search/song" -> songs(song("wrong", artist = "Someone else"),
                    song("live", title = "Song (Live)"), song("long", seconds = 240), song("right"))
                "/search" -> { assertEquals("right", uri.getQueryParameter("hash")); candidates(candidate("10")) }
                "/download" -> content()
                else -> error("Unexpected request: ${uri.host}${uri.path}")
            }
            response.toByteArray()
        }
        try {
            val result = requireNotNull(repository.rematch("酷狗音乐", "Song", "Artist", "", 200_000, emptySet()))
            assertEquals("酷狗音乐", result.source)
            assertEquals("10", result.recordId)
            assertEquals(lyrics, result.lyrics)
            assertEquals(200_000L, result.durationMs)
            assertEquals("Song", result.title)
            assertEquals("Artist", result.artist)
            assertEquals(listOf("/api/v3/search/song", "/search", "/download"), calls.map { it.path })
            assertTrue(result.wordLyrics.isEmpty())
        } finally { repository.close() }
    }

    @Test fun kugouFallsBackToKeywordAndAcceptsSwappedNamesAndSecondsDuration() {
        val calls = mutableListOf<Uri>()
        val repository = DirectLyricsRepository { url, _ ->
            val uri = Uri.parse(url); calls.add(uri)
            when (uri.path) {
                "/api/v3/search/song" -> songs()
                "/search" -> {
                    assertEquals("Song - Artist", uri.getQueryParameter("keyword"))
                    assertEquals("200000", uri.getQueryParameter("duration"))
                    candidates(candidate("swapped", 200, swapped = true))
                }
                "/download" -> content()
                else -> error("Unexpected request")
            }.toByteArray()
        }
        try {
            val result = requireNotNull(repository.rematch("酷狗音乐", "Song", "Artist", "", 200_000, emptySet()))
            assertEquals("Song", result.title)
            assertEquals("Artist", result.artist)
            assertEquals(200_000L, result.durationMs)
            assertEquals(3, calls.size)
        } finally { repository.close() }
    }

    @Test fun kugouRejectsCorruptPlaceholderAndExcludedLyricsAndFindsAnotherVersion() {
        val downloaded = mutableListOf<String>()
        val repository = DirectLyricsRepository { url, _ ->
            val uri = Uri.parse(url)
            when (uri.path) {
                "/api/v3/search/song" -> songs(song("right"))
                "/search" -> candidates(candidate("excluded"), candidate("base64"), candidate("utf8"),
                    candidate("placeholder"), candidate("good"))
                "/download" -> {
                    val id = requireNotNull(uri.getQueryParameter("id")); downloaded.add(id)
                    when (id) {
                        "base64" -> "{\"content\":\"%%%\"}"
                        "utf8" -> "{\"content\":\"/w==\"}"
                        "placeholder" -> content("[00:00.00]纯音乐，请欣赏")
                        "good" -> content()
                        else -> error("Excluded record downloaded")
                    }
                }
                else -> error("Unexpected request")
            }.toByteArray()
        }
        try {
            val result = requireNotNull(repository.rematch("酷狗音乐", "Song", "Artist", "", 200_000, setOf("excluded")))
            assertEquals("good", result.recordId)
            assertEquals(listOf("base64", "utf8", "placeholder", "good"), downloaded)
        } finally { repository.close() }
    }

    @Test fun kugouHashCanOmitNamesButKeywordCannotIdentifyAnUnknownRecording() {
        var hashMode = true
        val nameless = JSONObject().put("id", "nameless").put("accesskey", "public-key").put("duration", 200_000)
        val repository = DirectLyricsRepository { url, _ ->
            val uri = Uri.parse(url)
            when {
                uri.host == "itunes.apple.com" -> "{\"results\":[]}"
                uri.path == "/api/v3/search/song" -> if (hashMode) songs(song("right")) else songs()
                uri.path == "/search" -> candidates(nameless)
                uri.path == "/download" -> { assertTrue(hashMode); content() }
                else -> error("Unexpected request")
            }.toByteArray()
        }
        try {
            assertNotNull(repository.rematch("酷狗音乐", "Song", "Artist", "", 200_000, emptySet()))
            hashMode = false
            assertNull(repository.rematch("酷狗音乐", "Song", "Artist", "", 200_000, emptySet()))
        } finally { repository.close() }
    }

    @Test fun kugouRejectsLyricsFromADifferentDurationEvenWithAValidHash() {
        val repository = DirectLyricsRepository { url, _ ->
            val uri = Uri.parse(url)
            when {
                uri.host == "itunes.apple.com" -> "{\"results\":[]}"
                uri.path == "/api/v3/search/song" -> songs(song("right"))
                uri.path == "/search" -> candidates(candidate("too-long", 240_000))
                else -> error("Wrong-duration lyrics must not be downloaded")
            }.toByteArray()
        }
        try { assertNull(repository.rematch("酷狗音乐", "Song", "Artist", "", 200_000, emptySet())) }
        finally { repository.close() }
    }

    @Test fun lrcLibUsesAlbumFirstThenFallsBackWithoutItAndPrefersSyncedLyrics() {
        val calls = mutableListOf<Uri>()
        val repository = DirectLyricsRepository { url, headers ->
            assertTrue(headers.getValue("User-Agent").contains("lyrics-android"))
            val uri = Uri.parse(url); calls.add(uri)
            if (uri.getQueryParameter("album_name") != null) "[]".toByteArray() else
                JSONArray(listOf(lrcRecord(synced = "", plain = "Untimed text"), lrcRecord())).toString().toByteArray()
        }
        try {
            val result = requireNotNull(repository.rematch("LRCLIB", "Song", "Artist", "Album", 200_000, emptySet()))
            assertEquals(lyrics, result.lyrics)
            assertEquals(2, calls.size)
            assertEquals("Album", calls.first().getQueryParameter("album_name"))
            assertNull(calls.last().getQueryParameter("album_name"))
        } finally { repository.close() }
    }

    @Test fun lrcLibCombinedQueryFallbackStillChecksArtistVersionAndDuration() {
        val calls = mutableListOf<Uri>()
        val repository = DirectLyricsRepository { url, _ ->
            val uri = Uri.parse(url); calls.add(uri)
            if (uri.getQueryParameter("q") == "Artist Song") JSONArray(listOf(
                lrcRecord(artist = "Someone else"), lrcRecord(title = "Song (Live)"),
                lrcRecord(seconds = 240), lrcRecord())).toString().toByteArray() else "[]".toByteArray()
        }
        try {
            val result = requireNotNull(repository.rematch("LRCLIB", "Song", "Artist", "", 200_000, emptySet()))
            assertEquals(lyrics, result.lyrics)
            assertEquals("Artist", result.artist)
            assertEquals(200_000L, result.durationMs)
            assertEquals("Artist Song", calls.last().getQueryParameter("q"))
        } finally { repository.close() }
    }

    @Test fun providerInjectionCannotBypassTheResponseSizeLimit() {
        val repository = DirectLyricsRepository { _, _ -> ByteArray(2 * 1024 * 1024 + 1) }
        try {
            assertThrows(IllegalArgumentException::class.java) {
                repository.rematch("酷狗音乐", "Song", "Artist", "", 0, emptySet())
            }
        } finally { repository.close() }
    }

    @Test fun normalMultiProviderResolutionIncludesKugouWhenOtherServicesHaveNoLyrics() {
        val repository = DirectLyricsRepository { url, _ ->
            val uri = Uri.parse(url)
            when {
                uri.host == "lrclib.net" -> "[]"
                uri.path == "/api/v3/search/song" -> songs(song("right"))
                uri.host == "lyrics.kugou.com" && uri.path == "/search" -> candidates(candidate("10"))
                uri.host == "lyrics.kugou.com" && uri.path == "/download" -> content()
                else -> "{}"
            }.toByteArray()
        }
        try {
            val result = repository.resolveLyrics("Song", "Artist", expectedDurationMs = 200_000)
            assertEquals("酷狗音乐", result.source)
            assertEquals(lyrics, result.lyrics)
        } finally { repository.close() }
    }

    @Test fun kugouDoesNotDownloadTheSameBrokenCandidateAgainDuringSearchFallbacks() {
        var downloads = 0
        var hashes = 0
        val repository = DirectLyricsRepository { url, _ ->
            val uri = Uri.parse(url)
            when {
                uri.host == "itunes.apple.com" -> "{\"results\":[]}"
                uri.path == "/api/v3/search/song" -> songs(song("right"))
                uri.path == "/search" -> {
                    if (uri.getQueryParameter("hash") != null) hashes++
                    candidates(candidate("broken"))
                }
                uri.path == "/download" -> { downloads++; "{\"content\":\"%%%\"}" }
                else -> error("Unexpected request")
            }.toByteArray()
        }
        try {
            assertNull(repository.rematch("酷狗音乐", "Song", "Artist", "", 200_000, emptySet()))
            assertEquals(1, downloads)
            assertEquals(1, hashes)
        } finally { repository.close() }
    }

    @Test fun anInterruptedLookupStopsBeforeSendingAnotherRequest() {
        val repository = DirectLyricsRepository { _, _ -> error("Cancelled lookup sent a request") }
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                repository.rematch("酷狗音乐", "Song", "Artist", "", 0, emptySet())
            }
        } finally {
            Thread.interrupted()
            repository.close()
        }
    }
}
