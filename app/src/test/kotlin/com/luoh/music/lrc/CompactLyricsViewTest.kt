package com.luoh.music.lrc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textview.MaterialTextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactLyricsViewTest {
    private fun context(): Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(), R.style.Theme_DesktopLyrics
    )

    private fun rows(view: CompactLyricsView): List<String> = (0 until view.childCount).map { index ->
        val row = view.getChildAt(index) as ViewGroup
        assertTrue(row.getChildAt(0) is MaterialTextView)
        (row.getChildAt(0) as MaterialTextView).text.toString()
    }

    private fun draw(view: View, width: Int = 224, height: Int = 260) {
        repeat(2) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    @Test fun contextAndTranslationAreIndependentNativeRows() {
        val view = CompactLyricsView(context())
        var reportedHeight = 0
        var reportedTranslation = false
        view.onContentSizeChanged = { _, translated, height -> reportedTranslation = translated; reportedHeight = height }
        view.setAppearance(100, Color.WHITE, "bilingual", 2, 2)
        view.setPlayback("song", 2000L, 6000L, false, 1f)
        view.setDocument(LyricDocument((0..5).map { LyricLine(it * 1000L, "line $it", "translation $it") }, true))
        assertEquals(listOf("line 0", "line 1", "line 2", "translation 2", "line 3", "line 4"), rows(view))
        assertTrue(reportedTranslation)
        val fullHeight = reportedHeight
        draw(view)
        assertTrue(view.clipChildren)
        assertFalse((0 until view.childCount).any { view.getChildAt(it) is WebView })

        view.setAppearance(100, Color.WHITE, "original", 0, 0)
        assertEquals(listOf("line 2"), rows(view))
        assertFalse(reportedTranslation)
        assertTrue(reportedHeight < fullHeight)

        view.setAppearance(100, Color.WHITE, "translated", 0, 0)
        assertEquals(listOf("translation 2"), rows(view))
        view.clear("未在播放")
        assertEquals(listOf("未在播放"), rows(view))
        assertFalse(reportedTranslation)
    }

    @Test fun firstAndLastLinesDoNotWrapContext() {
        val view = CompactLyricsView(context())
        view.setAppearance(100, Color.WHITE, "original", 2, 2)
        view.setPlayback("song", 0L, 6000L, false, 1f)
        view.setDocument(LyricDocument((0..5).map { LyricLine(it * 1000L, "line $it") }, true))
        assertEquals(listOf("line 0", "line 1", "line 2"), rows(view))
        view.setPlayback("song", 5900L, 6000L, false, 1f)
        assertEquals(listOf("line 3", "line 4", "line 5"), rows(view))
    }

    @Test fun plainLyricsFollowPlaybackWithoutFakeTimestamps() {
        val document = LyricDocument((0..5).map { LyricLine(it * 100000000L, "line $it") }, false)
        assertEquals(0, CompactLyricSelection.activeIndex(document, 0, 6000))
        assertEquals(3, CompactLyricSelection.activeIndex(document, 3000, 6000))
        assertEquals(5, CompactLyricSelection.activeIndex(document, 6000, 6000))
        assertEquals(0, CompactLyricSelection.activeIndex(document, 3000, 0))
        assertEquals(listOf(0, 1, 2), CompactLyricSelection.contextIndices(6, 0, 2, 2).toList())
        assertTrue(CompactLyricSelection.contextIndices(0, -1, 2, 2).isEmpty())
    }

    @Test fun wordRowsRemainNativeAndCanDrawBeyondTheViewport() {
        val view = CompactLyricsView(context())
        view.setAppearance(100, Color.WHITE, "original", 0, 0)
        view.setPlayback("words", 1500L, 5000L, false, 1f)
        val phrase = "a very long lyric that should scroll without resizing its viewport"
        view.setDocument(LyricDocument(listOf(LyricLine(0L, phrase, words = listOf(LyricWord(0L, 5000L, phrase)))), true))
        draw(view, width = 160)
        val row = view.getChildAt(0) as ViewGroup
        assertTrue(row.clipChildren)
        assertTrue(row.getChildAt(0).width > row.width)
        assertEquals(phrase, rows(view).single())
    }

    @Test fun weightChangesAffectEveryRowAndIdenticalAppearanceKeepsViews() {
        val view = CompactLyricsView(context())
        view.setPlayback("weight", 1000L, 4000L, false, 1f)
        view.setDocument(LyricDocument(listOf(
            LyricLine(0, "Before", "之前"), LyricLine(1000, "Current", "当前"), LyricLine(2000, "After", "之后")
        ), true))
        view.setAppearance(100, Color.WHITE, "bilingual", 1, 1, 300)
        val expectedRows = rows(view)
        fun textViews() = (0 until view.childCount).map { (view.getChildAt(it) as ViewGroup).getChildAt(0) as MaterialTextView }
        textViews().forEach { assertEquals(300, it.typeface.weight) }
        val firstRow = view.getChildAt(0)
        view.setAppearance(100, Color.WHITE, "bilingual", 1, 1, 300)
        assertSame("Unchanged appearance must not rebuild rows", firstRow, view.getChildAt(0))
        view.setAppearance(150, Color.WHITE, "bilingual", 1, 1, 900)
        assertEquals(expectedRows, rows(view))
        textViews().forEach { assertEquals(900, it.typeface.weight) }
        draw(view)
    }

    @Test fun allLyricRowsStayCrispWithoutShadows() {
        val view = CompactLyricsView(context())
        view.setPlayback("crisp", 1000L, 4000L, false, 1f)
        view.setDocument(LyricDocument(listOf(
            LyricLine(0, "Before"),
            LyricLine(1000, "Current", "当前", words = listOf(LyricWord(1000, 1000, "Current"))),
            LyricLine(2000, "After")
        ), true))
        for (color in listOf(Color.WHITE, Color.BLACK, Color.CYAN)) {
            view.setAppearance(100, color, "bilingual", 1, 1)
            draw(view)
            assertEquals(4, view.childCount)
            for (index in 0 until view.childCount) {
                val text = (view.getChildAt(index) as ViewGroup).getChildAt(0) as MaterialTextView
                assertEquals("Current, translated and context rows must not glow", 0f, text.shadowRadius, 0f)
                assertEquals(0f, text.shadowDx, 0f)
                assertEquals(0f, text.shadowDy, 0f)
                assertEquals(Color.TRANSPARENT, text.shadowColor)
            }
        }
    }

    @Test fun documentBridgeKeepsWordsAndToleratesMissingOptionalFields() {
        val result = requireNotNull(OverlayNativeDocument.parse("""
            {"requestId":7,"key":"song","track":"Song","artist":"Artist","durationMs":10000,
             "lines":[{"t":1000,"duration":2000,"text":"hello world","translation":"你好", "words":[
               {"start":1000,"duration":500,"text":"hello "},{"start":1500,"text":"world"}]}]}
        """))
        assertEquals(7, result.requestId)
        assertEquals("Artist", result.artist)
        assertTrue(result.document.timed)
        val line = result.document.lines.single()
        assertEquals(3000L, line.endMs)
        assertEquals("你好", line.translation)
        assertEquals(1500L, line.words[1].startMs)
        assertEquals(1L, line.words[1].durationMs)
        val plain = requireNotNull(OverlayNativeDocument.parse("""{"requestId":8,"lines":[{"text":"plain","plain":true}]}"""))
        assertFalse(plain.document.timed)
        assertTrue(plain.document.lines.single().words.isEmpty())
        assertNull(OverlayNativeDocument.parse("not json"))
    }
}
