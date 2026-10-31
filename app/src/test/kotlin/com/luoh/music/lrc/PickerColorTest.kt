package com.luoh.music.lrc

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PickerColorTest {
    @Test fun arbitraryHexColorsRoundTripAndInvalidValuesAreRejected() {
        for (hex in listOf("#123ABC", "#FEDCBA", "#000000", "#FFFFFF", "#7B7B7B", "#03FE81")) {
            assertEquals(hex, PickerColor.parse(hex.lowercase())!!.hex())
        }
        for (invalid in listOf("", "#FFF", "#GG0000", "#FF000000", "#12345Z")) assertNull(PickerColor.parse(invalid))
    }

    @Test fun wheelCoversAllHuesAndClampsOnlySaturationOutsideItsEdge() {
        val white = PickerColor(120f, 0f, 1f)
        assertEquals("#FF0000", white.atPoint(100f, 0f, 100f).hex())
        assertEquals("#00FFFF", white.atPoint(-100f, 0f, 100f).hex())
        assertEquals("#80FF00", white.atPoint(0f, 100f, 100f).hex())
        assertEquals("#FFFFFF", white.atPoint(0f, 0f, 100f).hex())
        assertEquals(120f, white.atPoint(0f, 0f, 100f).hue, 0f)
        assertEquals(1f, white.atPoint(200f, 0f, 100f).saturation, 0f)
        assertEquals("#000000", white.copy(value = 0f).atPoint(100f, 0f, 100f).hex())
    }
}
