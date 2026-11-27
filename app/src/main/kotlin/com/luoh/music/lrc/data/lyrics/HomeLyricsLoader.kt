package com.luoh.music.lrc.data.lyrics

import android.content.Context
import com.luoh.music.lrc.lyrics.LyricDocument
import com.luoh.music.lrc.lyrics.LyricParser

internal data class LyricPayload(
    val lyrics: String,
    val translated: String,
    val word: String,
    val source: String,
    val recordId: String,
) {
    val signature: String
        get() = "$source\u0000$recordId\u0000${lyrics.hashCode()}\u0000${translated.hashCode()}\u0000${word.hashCode()}"
}

internal class HomeLyricsLoader(private val context: Context) {
    private val repository = DirectLyricsRepository()
    private val cache by lazy { SharedLyricsCache(context) }

    /** Local playback prefers a valid custom lyric over a cached provider result. */
    fun readLocal(track: String, artist: String, album: String, durationMs: Long): LyricPayload? {
        CustomLyricsStore.find(context, track, artist)?.let { entry ->
            val parsed = CustomLyricsStore.parse(entry)
            if (parsed.lyrics.isNotBlank()) {
                return LyricPayload(
                    parsed.lyrics, parsed.translatedLyrics, entry.wordLyrics,
                    CustomLyricsStore.SOURCE, entry.recordId,
                )
            }
        }
        val search = SongSearchCorrections.resolve(context, track, artist)
        return cache.read(search.title, search.artist, album, durationMs)?.toPayload()
    }

    fun resolve(
        search: SongSearchCorrections.Search,
        album: String,
        durationMs: Long,
        forceNetwork: Boolean,
    ): LyricPayload = runCatching {
        cache.resolve(search.title, search.artist, album, durationMs, forceNetwork) {
            repository.resolveLyrics(search.title, search.artist, album, durationMs)
        }
    }.getOrNull().toPayload()

    fun close() = repository.close()

    private fun DirectLyricsRepository.Result?.toPayload(): LyricPayload =
        LyricPayload(
            this?.lyrics.orEmpty(), this?.translatedLyrics.orEmpty(), this?.wordLyrics.orEmpty(),
            this?.source.orEmpty(), this?.recordId.orEmpty(),
        )
}

internal fun parseLyricPayload(payload: LyricPayload): LyricDocument =
    if (payload.lyrics.isBlank()) LyricDocument(emptyList(), true)
    else LyricParser.parse(
        payload.lyrics, payload.translated, payload.word,
        allowPartialWords = payload.source == CustomLyricsStore.SOURCE,
    )
