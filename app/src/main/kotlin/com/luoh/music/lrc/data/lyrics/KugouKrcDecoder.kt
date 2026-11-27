package com.luoh.music.lrc.data.lyrics

import com.luoh.music.lrc.lyrics.LyricParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.InflaterInputStream

/** KRC1 -> bounded UTF-8 -> the same canonical word timeline used by QQ/YRC. */
internal object KugouKrcDecoder {
    private val key = intArrayOf(0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
        0x51, 0x36, 0x31, 0x2d, 0xce, 0xd2, 0x6e, 0x69)
    private const val MAX_BYTES = 2 * 1024 * 1024

    data class Lyrics(val lineLyrics: String, val wordLyrics: String)

    fun decode(raw: ByteArray): Lyrics {
        require(raw.size in 5..MAX_BYTES && raw[0] == 0x6b.toByte() && raw[1] == 0x72.toByte() &&
            raw[2] == 0x63.toByte() && raw[3] == 0x31.toByte()) { "Invalid KRC1 header" }
        val compressed = ByteArray(raw.size - 4) { index ->
            (raw[index + 4].toInt() xor key[index % key.size]).toByte()
        }
        val bytes = InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val read = input.read(buffer)
                if (read < 0) break
                require(output.size() + read <= MAX_BYTES) { "KRC decompressed response too large" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val imported = CustomLyricsStore.parseImport(text)
        val lines = LyricParser.parse(imported.parsed.lyrics).lines
        require(lines.isNotEmpty()) { "KRC contains no timed lyrics" }
        // Credit rows may have fake 3ms tokens. Do not attach them to nearby sung lines.
        val identities = lines.map { it.startMs to it.text.trim() }.toSet()
        val words = LyricParser.parse("", wordLrc = imported.wordLyrics).lines
            .filter { (it.startMs to it.text.trim()) in identities }
            .joinToString("\n") { line ->
                val end = line.endMs ?: line.words.maxOf { it.startMs + it.durationMs }
                buildString {
                    append('[').append(line.startMs).append(',').append(end - line.startMs).append(']')
                    line.words.forEach { word ->
                        append('(').append(word.startMs).append(',').append(word.durationMs).append(')').append(word.text)
                    }
                }
            }
        return Lyrics(imported.parsed.lyrics, words)
    }
}
