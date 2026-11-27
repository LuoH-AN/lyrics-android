package com.luoh.music.lrc

import android.net.Uri
import android.util.Base64
import com.luoh.music.lrc.data.lyrics.DirectLyricsRepository
import com.luoh.music.lrc.data.lyrics.KugouKrcDecoder
import com.luoh.music.lrc.lyrics.LyricParser
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class KugouKrcProviderTest {
    private val source = "[ti:Song]\n[ar:Artist]\n[1000,800]<0,200,0>Hello <200,600,0>world\n[3000,600]<0,300,0>Stay <300,300,0>here"
    private fun krc(text: String = source): ByteArray {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output).use { it.write(text.toByteArray()) }
        val key = intArrayOf(0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
            0x51, 0x36, 0x31, 0x2d, 0xce, 0xd2, 0x6e, 0x69)
        return "krc1".toByteArray() + output.toByteArray().mapIndexed { index, byte ->
            (byte.toInt() xor key[index % 16]).toByte()
        }.toByteArray()
    }
    private fun response(bytes: ByteArray) = JSONObject().put("status", 200)
        .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)).toString()

    @Test fun decoderKeepsAbsoluteWordStartsAndDurations() {
        val decoded = KugouKrcDecoder.decode(krc())
        assertTrue(decoded.lineLyrics.contains("[00:01.000]Hello world"))
        assertTrue(decoded.wordLyrics.contains("[1000,800](1000,200)Hello (1200,600)world"))
        val document = LyricParser.parse(decoded.lineLyrics, wordLrc = decoded.wordLyrics)
        assertEquals(1200L, document.lines.first().words[1].startMs)
        assertEquals(600L, document.lines.first().words[1].durationMs)
    }

    @Test fun decoderRejectsInvalidHeaderAndCompressedData() {
        assertThrows(IllegalArgumentException::class.java) { KugouKrcDecoder.decode("not krc".toByteArray()) }
        assertThrows(Exception::class.java) { KugouKrcDecoder.decode("krc1broken".toByteArray()) }
    }

    private fun repository(brokenKrc: Boolean, calls: MutableList<Uri>): DirectLyricsRepository =
        DirectLyricsRepository { url, _ ->
            val uri = Uri.parse(url); calls += uri
            val candidate = JSONObject().put("id", "10").put("accesskey", "synthetic-key")
                .put("song", "Song").put("singer", "Artist").put("duration", 200000)
            when {
                uri.path == "/api/v3/search/song" -> JSONObject().put("data", JSONObject().put("info", JSONArray().put(
                    JSONObject().put("hash", "right").put("songname", "Song (Live)")
                        .put("singername", "Artist").put("duration", 200).put("album_audio_id", "album-1")))).toString()
                uri.path == "/search" -> {
                    if (uri.host == "krcs.kugou.com") {
                        assertEquals("album-1", uri.getQueryParameter("album_audio_id"))
                        assertEquals("right", uri.getQueryParameter("hash"))
                        assertEquals("200000", uri.getQueryParameter("duration"))
                    }
                    JSONObject().put("status", 200).put("candidates", JSONArray().put(candidate)).toString()
                }
                uri.path == "/download" && uri.getQueryParameter("fmt") == "krc" ->
                    response(if (brokenKrc) "broken".toByteArray() else krc())
                uri.path == "/download" -> response("[00:01.000]Hello world\n[00:03.000]Stay here".toByteArray())
                else -> "{}"
            }.toByteArray()
        }

    @Test fun hashMatchedLiveRecordingAcceptsKrcWithoutLiveSuffix() {
        val calls = mutableListOf<Uri>()
        val repo = repository(false, calls)
        try {
            val result = requireNotNull(repo.rematch("酷狗音乐", "Song (Live)", "Artist", "", 200000, emptySet()))
            assertEquals("酷狗音乐", result.source)
            assertEquals("Song (Live)", result.title)
            assertTrue(result.wordLyrics.isNotBlank())
            assertEquals("krc", calls.last().getQueryParameter("fmt"))
        } finally { repo.close() }
    }

    @Test fun corruptKrcFallsBackToLrcForTheSameCandidateId() {
        val calls = mutableListOf<Uri>()
        val repo = repository(true, calls)
        try {
            val result = requireNotNull(repo.rematch("酷狗音乐", "Song (Live)", "Artist", "", 200000, emptySet()))
            assertTrue(result.wordLyrics.isBlank())
            assertEquals(listOf("krc", "lrc"), calls.filter { it.path == "/download" }.map { it.getQueryParameter("fmt") })
        } finally { repo.close() }
    }
}
