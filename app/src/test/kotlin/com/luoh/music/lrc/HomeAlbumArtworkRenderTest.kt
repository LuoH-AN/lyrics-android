package com.luoh.music.lrc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeAlbumArtworkRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun cover(color: Int) = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
        eraseColor(color)
    }

    private fun pixels(root: View): List<Int> {
        var colors = emptyList<Int>()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            colors = listOf(.1f, .5f, .9f).map { fraction ->
                bitmap.getPixel((root.width * fraction).toInt(), (root.height * fraction).toInt())
            }
            bitmap.recycle()
        }
        return colors
    }

    @Test fun coverChangesAndInterruptedFadesNeverExposeTheDarkPageUnderneath() {
        val red = cover(AndroidColor.RED)
        val green = cover(AndroidColor.GREEN)
        var artwork by mutableStateOf<Bitmap?>(red)
        var allowFade by mutableStateOf(true)
        lateinit var root: View
        compose.setContent {
            val view = LocalView.current
            SideEffect { root = view }
            HomeAlbumArtwork(artwork, allowFade, phase = { 0f },
                modifier = Modifier.fillMaxSize().background(Color.Black))
        }
        assertTrue(pixels(root).all { it == AndroidColor.RED })
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { artwork = green }
        repeat(28) { frame ->
            compose.mainClock.advanceTimeBy(40)
            compose.waitForIdle()
            for (color in pixels(root)) {
                val energy = AndroidColor.red(color) + AndroidColor.green(color)
                assertTrue("Cover opacity must stay full during frame $frame: $energy", energy in 253..257)
                assertEquals(255, AndroidColor.alpha(color))
            }
            // A new song can arrive before the previous fade finishes.
            if (frame == 4) compose.runOnIdle { artwork = red }
        }
        assertTrue(pixels(root).all { it == AndroidColor.RED })
        compose.runOnIdle { artwork = green; allowFade = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue(pixels(root).all { it == AndroidColor.GREEN })
        compose.runOnIdle { artwork = null }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue(pixels(root).all { it == AndroidColor.BLACK })
    }
}
