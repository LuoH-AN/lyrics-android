package com.luoh.music.lrc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SongSearchCorrectionsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearCorrections() {
        context.getSharedPreferences("song_search_corrections_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun correctionSurvivesReloadWithoutChangingOtherRecordings() {
        SongSearchCorrections.save(context, "wrong name", "Singer A", " Right name ", " Correct artist ")
        val restoredContext = context.createConfigurationContext(context.resources.configuration)
        val search = SongSearchCorrections.resolve(restoredContext, "wrong name", "Singer A")
        assertEquals("Right name", search.title)
        assertEquals("Correct artist", search.artist)
        assertTrue(search.revision > 0L)
        assertEquals("wrong name", SongSearchCorrections.resolve(context, "wrong name", "Singer B").title)
        assertEquals("other song", SongSearchCorrections.resolve(context, "other song", "Singer A").title)
    }

    @Test fun restoreAndRepeatedEditsInvalidatePriorSearchResults() {
        SongSearchCorrections.save(context, "reported", "Artist", "corrected", "")
        val first = SongSearchCorrections.resolve(context, "reported", "Artist")
        assertEquals("", first.artist)
        SongSearchCorrections.save(context, "reported", "Artist", null, "Artist")
        val original = SongSearchCorrections.resolve(context, "reported", "Artist")
        assertEquals("reported", original.title)
        assertEquals("Artist", original.artist)
        assertTrue(original.revision > first.revision)
        SongSearchCorrections.save(context, "reported", "Artist", "corrected", "")
        assertTrue(SongSearchCorrections.resolve(context, "reported", "Artist").revision > original.revision)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankSearchTitleCannotReplaceASavedCorrection() {
        SongSearchCorrections.save(context, "reported", "Artist", "   ", "")
    }
}
