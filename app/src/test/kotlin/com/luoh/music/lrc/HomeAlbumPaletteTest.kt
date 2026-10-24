package com.luoh.music.lrc

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeAlbumPaletteTest {
    @Test fun colorfulArtworkWinsOverWhiteBordersAndKeepsASecondCoverColor() {
        val cover = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        cover.eraseColor(AndroidColor.WHITE)
        for (y in 12 until 36) for (x in 12 until 36) {
            cover.setPixel(x, y, if (x < 28) AndroidColor.RED else AndroidColor.BLUE)
        }
        val palette = requireNotNull(extractHomeAlbumPalette(cover))
        assertEquals(AndroidColor.RED, palette.primary)
        assertEquals(AndroidColor.BLUE, palette.secondary)
        assertFalse("Sampling must never recycle shared media artwork", cover.isRecycled)
        cover.recycle()
    }

    @Test fun monochromeArtworkStaysNeutral() {
        val cover = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        cover.eraseColor(AndroidColor.GRAY)
        val palette = requireNotNull(extractHomeAlbumPalette(cover))
        assertEquals(AndroidColor.GRAY, palette.primary)
        assertEquals(AndroidColor.GRAY, palette.secondary)
        for (tone in listOf(14.0, 34.0, 70.0, 90.0)) {
            val color = homeAlbumTone(palette.primary, tone, 64.0).toArgb()
            assertEquals(AndroidColor.red(color), AndroidColor.green(color))
            assertEquals(AndroidColor.red(color), AndroidColor.blue(color))
        }
        cover.recycle()
    }

    @Test fun missingTransparentAndRecycledArtworkUseTheThemeFallback() {
        assertNull(extractHomeAlbumPalette(null))
        val cover = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        assertNull(extractHomeAlbumPalette(cover))
        cover.recycle()
        assertNull(extractHomeAlbumPalette(cover))
    }

    @Test fun coverTonesKeepReadableBackgroundsAcrossDarkAndLightThemes() {
        for (seed in listOf(AndroidColor.RED, AndroidColor.BLUE, AndroidColor.GREEN, AndroidColor.WHITE, AndroidColor.BLACK)) {
            val dark = homeAlbumTone(seed, 26.0, 46.0).luminance()
            val light = homeAlbumTone(seed, 85.0, 46.0).luminance()
            assertTrue("Dark gradient must support light text", dark < .08f)
            assertTrue("Light gradient must support dark text", light > .6f)
        }
    }
}
