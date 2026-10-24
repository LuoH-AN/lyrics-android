package com.luoh.music.lrc

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeAlbumBackgroundTest {
    @Test fun repeatedArtworkKeepsThePreparedBitmapEvenWhenMetadataUsesANewInstance() {
        val cover = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
        cover.eraseColor(Color.CYAN)
        val background = requireNotNull(prepareHomeAlbumBackground(cover))
        val resent = requireNotNull(cover.copy(Bitmap.Config.ARGB_8888, false))
        assertNotSame(cover, resent)
        assertSame(background, prepareHomeAlbumBackground(resent, background))
        assertFalse(cover.isRecycled)
        assertFalse(resent.isRecycled)
        assertFalse(background.isRecycled)
        resent.recycle()
        background.recycle()
        cover.recycle()
    }

    @Test fun changedArtworkReplacesThePreparedBitmapWithoutClearingThePreviousOne() {
        val cover = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        cover.eraseColor(Color.RED)
        val previous = requireNotNull(prepareHomeAlbumBackground(cover))
        cover.eraseColor(Color.BLUE)
        val next = requireNotNull(prepareHomeAlbumBackground(cover, previous))
        assertNotSame(previous, next)
        assertEquals(Color.RED, previous.getPixel(80, 80))
        assertEquals(Color.BLUE, next.getPixel(80, 80))
        assertFalse(previous.isRecycled)
        next.recycle()
        previous.recycle()
        cover.recycle()
    }

    @Test fun coverEdgesAreBlurredWithoutChangingSharedArtwork() {
        val cover = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        for (y in 0 until 160) for (x in 0 until 160) {
            cover.setPixel(x, y, if (x < 80) Color.BLACK else Color.WHITE)
        }
        val blurred = requireNotNull(prepareHomeAlbumBackground(cover))
        assertTrue(Color.red(blurred.getPixel(79, 80)) > 0)
        assertTrue(Color.red(blurred.getPixel(80, 80)) < 255)
        assertEquals(Color.BLACK, cover.getPixel(79, 80))
        assertEquals(Color.WHITE, cover.getPixel(80, 80))
        assertFalse(cover.isRecycled)
        blurred.recycle()
        cover.recycle()
    }

    @Test fun missingRecycledAndNeutralArtworkKeepTheirExpectedFallbackAndColors() {
        assertNull(prepareHomeAlbumBackground(null))
        val cover = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
        cover.eraseColor(Color.GRAY)
        val blurred = requireNotNull(prepareHomeAlbumBackground(cover))
        assertEquals(240, blurred.width)
        assertEquals(120, blurred.height)
        assertEquals(Color.GRAY, blurred.getPixel(120, 60))
        assertFalse(cover.isRecycled)
        blurred.recycle()
        cover.recycle()
        assertNull(prepareHomeAlbumBackground(cover))
    }
}
