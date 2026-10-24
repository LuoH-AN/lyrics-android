package com.luoh.music.lrc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePresentationStateTest {
    @Test fun changingTrackClearsThePreviousLyricsButProgressUpdatesKeepThem() {
        val state = HomePresentationState()
        state.setSnapshot(HomePresentationState.Snapshot(track = "First", artist = "Artist", durationMs = 10_000))
        val document = LyricDocument(listOf(LyricLine(0, "First lyric")), true)
        state.setLyrics(document, isCustom = true)
        state.setProgress(3_000, playing = false)
        assertEquals(document, state.document)
        assertTrue(state.custom)
        assertEquals("ready", state.lyricStatus)

        state.setSnapshot(HomePresentationState.Snapshot(track = "Second", artist = "Artist", durationMs = 8_000, positionMs = 100))
        assertTrue(state.document.lines.isEmpty())
        assertFalse(state.custom)
        assertEquals("loading", state.lyricStatus)
        assertEquals(100L, state.clock.positionMs())
        state.setSnapshot(HomePresentationState.Snapshot())
        assertEquals(0L, state.clock.positionMs())
        assertEquals(0L, state.clock.durationMs)
        assertFalse(state.playing)
    }

    @Test fun explicitLyricSeekImmediatelyMovesClockAndClampsTheMediaCommand() {
        val actions = RecordingHomeActions()
        val state = HomePresentationState().apply { this.actions = actions }
        state.seek(500)
        assertTrue(actions.seeks.isEmpty())
        state.setSnapshot(HomePresentationState.Snapshot(track = "Song", durationMs = 10_000, positionMs = 5_000))
        state.seek(4_800)
        assertEquals(4_800L, state.clock.positionMs())
        state.seek(-100)
        assertEquals(0L, state.clock.positionMs())
        state.seek(12_000)
        assertEquals(10_000L, state.clock.positionMs())
        assertEquals(listOf(4_800L, 0L, 10_000L), actions.seeks)
    }
}
