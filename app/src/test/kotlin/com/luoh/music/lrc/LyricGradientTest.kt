package com.luoh.music.lrc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricGradientTest {
    private fun context(): Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_DesktopLyrics)
    private fun bitmap(view: View, width: Int = 320, height: Int = 100): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    private fun pixels(bitmap: Bitmap): List<Int> = buildList {
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 10) add(pixel)
        }
    }

    @Test fun gradientKeepsKaraokeAlphaAndCanReturnToSolidColor() {
        val line = LyricLine(0, "MMMMMMMM", words = listOf(LyricWord(0, 10000, "MMMMMMMM")))
        val view = LyricLineView(context()).apply {
            textSize = 36f
            setTextColor(Color.RED)
            setGradient(Color.BLUE)
            bind(line, "original")
        }
        view.setPlaybackPosition(0)
        val pending = bitmap(view)
        assertTrue(pixels(pending).maxOf { Color.alpha(it) } < 100)
        pending.recycle()
        view.setPlaybackPosition(5000)
        val halfway = bitmap(view)
        val halfPixels = pixels(halfway)
        assertTrue(halfPixels.any { Color.alpha(it) > 200 && Color.red(it) > Color.blue(it) })
        assertTrue(halfPixels.any { Color.alpha(it) in 50..100 && Color.blue(it) > Color.red(it) })
        halfway.recycle()
        view.setPlaybackPosition(10000)
        val complete = bitmap(view)
        assertTrue(pixels(complete).any { Color.alpha(it) > 200 && Color.blue(it) > Color.red(it) })
        complete.recycle()
        view.setGradient(null)
        val solid = bitmap(view)
        assertTrue(pixels(solid).all { Color.blue(it) < 5 })
        assertNull("Drawing must not leak a shader into later text", view.paint.shader)
        solid.recycle()
    }

    @Test fun plainAndTranslatedLinesAlsoRenderGradientAndScreenshotsCoverAllBackgrounds() {
        val view = CompactLyricsView(context())
        view.setPlayback("preview", 1500, 5000, false, 1f)
        view.setDocument(LyricDocument(listOf(LyricLine(0, "让歌词陪着你", "Stay with me",
            words = listOf(LyricWord(0, 3000, "让歌词陪着你"))), LyricLine(3000, "再听一首歌")), true))
        for ((index, background) in listOf("transparent", "low", "high").withIndex()) {
            view.setAppearance(100, Color.rgb(159, 216, 255), "bilingual", 0, 1,
                style = OverlayTextStyle(listOf("left", "center", "right")[index], Color.rgb(255, 182, 213), index == 1))
            view.background = OverlayAppearance.background(context(), background)
            val output = bitmap(view, 360, 132)
            val file = File("build/reports/material-ui/overlay-$background.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
            output.recycle()
        }
        val plain = LyricLineView(context()).apply {
            textSize = 36f; setTextColor(Color.RED); setGradient(Color.BLUE)
            bind(LyricLine(0, "original", "MMMMMMMM"), "translated")
        }
        val output = bitmap(plain)
        assertTrue(pixels(output).any { Color.red(it) > Color.blue(it) + 30 })
        assertTrue(pixels(output).any { Color.blue(it) > Color.red(it) + 30 })
        output.recycle()
    }
}
