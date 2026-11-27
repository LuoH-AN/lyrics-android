package com.luoh.music.lrc.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.lyrics.LyricClock
import com.luoh.music.lrc.lyrics.LyricDocument
import com.luoh.music.lrc.lyrics.LyricTiming
import com.luoh.music.lrc.ui.share.LyricShareCard
import com.luoh.music.lrc.ui.share.LyricShareDraft

@Stable
internal class HomePresentationState(internal val clock: LyricClock = LyricClock()) {
    data class Snapshot(
        val track: String = "",
        val artist: String = "",
        val cover: Bitmap? = null,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val playing: Boolean = false,
        val speed: Float = 1f,
        val canPrevious: Boolean = false,
        val canNext: Boolean = false,
    )

    interface Actions {
        fun toggleOverlay()
        fun openSettings()
        fun seekTo(positionMs: Long)
        fun togglePlay()
        fun skipPrev()
        fun skipNext()
        fun retryLyrics()
        fun correctSongSearch(track: String, artist: String, title: String?, searchArtist: String)
        fun editCustomLyrics()
        fun manageCustomLyrics()
        fun manageLyricSources()
        fun exportLrc()
    }

    var actions: Actions? = null
    var onTranslationSettings: (() -> Unit)? = null
    var gateVisible by mutableStateOf(false)
    var gateStatus by mutableStateOf("")
    internal var snapshot by mutableStateOf(Snapshot())
        private set
    internal var document by mutableStateOf(LyricDocument(emptyList(), true))
        private set
    internal var custom by mutableStateOf(false)
        private set
    internal var lyricStatus by mutableStateOf("loading")
        private set
    internal var translationDisplayMode by mutableStateOf(LyricsOverlayService.TRANSLATION_BILINGUAL)
        private set
    internal var translationMessage by mutableStateOf("")
        private set
    internal var shareDraft by mutableStateOf<LyricShareDraft?>(null)
    internal var displayOffsetMs by mutableIntStateOf(0)
        private set
    internal var textAlignment by mutableStateOf(HomeLyricsPrefs.LEFT)
        private set
    internal var lyricMotionStyle by mutableStateOf(MotionStyle())
        private set
    internal var presentationActive by mutableStateOf(false)
        private set
    internal var playing by mutableStateOf(false)
        private set
    internal var isOverlayRunning by mutableStateOf(false)
        private set
    internal var playbackRevision by mutableIntStateOf(0)
        private set
    internal var seekRevision by mutableIntStateOf(0)
        private set

    fun setSnapshot(value: Snapshot, forcePosition: Boolean = false) {
        val trackChanged = value.track != snapshot.track || value.artist != snapshot.artist
        snapshot = value
        clock.durationMs = value.durationMs.coerceAtLeast(0L)
        clock.sync(value.positionMs, value.playing, value.speed, forcePosition || trackChanged)
        playing = value.playing
        if (value.track.isBlank()) {
            clock.reset()
            playing = false
            showLoading()
        } else if (forcePosition || trackChanged) showLoading()
        playbackRevision++
    }

    fun setProgress(positionMs: Long, playing: Boolean, speed: Float = 1f) {
        clock.sync(positionMs, playing, speed)
        this.playing = playing
        playbackRevision++
    }

    fun setLyrics(value: LyricDocument, isCustom: Boolean = false) {
        document = value
        custom = isCustom
        lyricStatus = if (value.lines.isEmpty()) "empty" else "ready"
    }

    fun showLoading() {
        document = LyricDocument(emptyList(), true)
        custom = false
        lyricStatus = "loading"
    }

    fun setTranslationMode(mode: String) {
        translationDisplayMode = when (mode) {
            LyricsOverlayService.TRANSLATION_ORIGINAL, LyricsOverlayService.TRANSLATION_TRANSLATED -> mode
            else -> LyricsOverlayService.TRANSLATION_BILINGUAL
        }
    }

    fun setTranslationStatus(message: String) { translationMessage = message }
    fun beginSharing(index: Int? = null) {
        if (document.lines.isEmpty() || snapshot.track.isBlank()) return
        val selected = index ?: homeActiveLine(document, clock.positionMs() + displayOffsetMs, clock.durationMs).coerceAtLeast(0)
        shareDraft = LyricShareDraft(snapshot.track, snapshot.artist, LyricShareCard.snapshotCover(snapshot.cover),
            document.lines.toList(), selected)
    }
    fun setAlignment(value: String) {
        textAlignment = when (value) {
            HomeLyricsPrefs.CENTER, HomeLyricsPrefs.RIGHT -> value
            else -> HomeLyricsPrefs.LEFT
        }
    }
    fun setLyricOffset(offsetMs: Int) { displayOffsetMs = offsetMs }
    fun setMotionStyle(style: MotionStyle) { lyricMotionStyle = style }
    fun setOverlayState(running: Boolean) { isOverlayRunning = running }
    fun setActive(on: Boolean) { presentationActive = on }

    internal fun seek(positionMs: Long) {
        if (snapshot.track.isBlank()) return
        val target = positionMs.coerceAtLeast(0L).let {
            if (snapshot.durationMs > 0L) it.coerceAtMost(snapshot.durationMs) else it
        }
        actions?.seekTo(target)
        clock.sync(target, playing, clock.speed, force = true)
        seekRevision++
        playbackRevision++
    }
}

internal fun homeActiveLine(document: LyricDocument, position: Long, duration: Long): Int {
    if (document.lines.isEmpty()) return -1
    return if (document.timed) LyricTiming.activeIndex(document.lines, position)
    else if (duration <= 0L) 0
    else ((position.coerceAtLeast(0L).toDouble() / duration) * document.lines.size).toInt().coerceIn(0, document.lines.lastIndex)
}
