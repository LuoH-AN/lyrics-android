package com.luoh.music.lrc

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import java.io.File
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricShareCardTest {
    @Test fun selectingOutOfOrderPreservesSongOrderAndDraftSurvivesTrackChanges() {
        val cover = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val state = HomePresentationState().apply {
            setSnapshot(HomePresentationState.Snapshot(track = "First", artist = "Singer", cover = cover))
            setLyrics(LyricDocument(listOf(LyricLine(0, "first", "一"), LyricLine(1000, "second", "二")), true))
            beginSharing(1)
        }
        val draft = requireNotNull(state.shareDraft)
        cover.eraseColor(Color.BLUE)
        state.setSnapshot(HomePresentationState.Snapshot(track = "Next", artist = "Other"))
        assertSame(draft, state.shareDraft)
        assertEquals("First", draft.title)
        assertEquals(Color.RED, draft.cover!!.getPixel(0, 0))
        assertEquals("first\n一\n\nsecond\n二", LyricShareCard.excerpt(
            LyricShareCard.selectedLines(draft, linkedSetOf(1, 0)), true))
        assertFalse(LyricShareCard.fits(emptyList(), false))
        assertFalse(LyricShareCard.fits(List(7) { LyricLine(0, "line") }, false))
        assertFalse(LyricShareCard.fits(listOf(LyricLine(0, "长".repeat(1201))), false))
    }

    @Test fun renderedCardsWrapLongLyricsAndTheSharedUriDecodesToTheSamePng() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cover = requireNotNull(javaClass.getResourceAsStream("/share-cover.png").use { BitmapFactory.decodeStream(it) })
        val short = listOf(LyricLine(0, "把今天藏进晚风里"), LyricLine(1000, "让明天慢慢有回音"))
        val draft = LyricShareDraft("晚风来信", "林间电台", cover, short, 0)
        val bitmap = LyricShareCard.render(context, draft, short, false)
        val long = listOf(short.first().copy(text = "这是一句需要完整换行显示的歌词".repeat(24)))
        val wrapped = LyricShareCard.render(context, draft, long, false)
        assertTrue(wrapped.height > bitmap.height)
        assertEquals(900, bitmap.width)
        assertEquals(1200, bitmap.height)
        val report = File("build/reports/material-ui").apply { mkdirs() }
        File(report, "lyric-share-card.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(report, "lyric-share-long.png").outputStream().use { wrapped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val translated = short.map { it.copy(translation = "Keep this moment in the evening breeze") }
        val bilingual = LyricShareCard.render(context, draft, translated, true)
        File(report, "lyric-share-translated.png").outputStream().use { bilingual.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val savedFile = File(context.cacheDir, "saved-card.png")
        LyricShareCard.save(context, bitmap, Uri.fromFile(savedFile))
        assertEquals(bitmap.height, requireNotNull(BitmapFactory.decodeFile(savedFile.path)).height)
        val intent = LyricShareCard.shareIntent(context, bitmap)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = intent.clipData!!.getItemAt(0).uri
        assertEquals("content", uri.scheme)
        val decoded = requireNotNull(context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) })
        assertEquals(bitmap.width, decoded.width)
        assertEquals(bitmap.height, decoded.height)
        assertEquals(bitmap.getPixel(0, 0), decoded.getPixel(0, 0))
    }

    @Test fun albumBackgroundIsBlurredWithoutChangingTheCoverAndMarkIsAtBottomRight() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cover = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        Canvas(cover).drawRect(450f, 0f, 900f, 1200f, Paint().apply { color = Color.WHITE })
        val lines = listOf(LyricLine(0, "a lyric"))
        val bitmap = LyricShareCard.render(context, LyricShareDraft("Song", "Artist", cover, lines, 0), lines, false)
        val blurredEdge = Color.red(bitmap.getPixel(445, 350))
        assertTrue("The hard cover boundary must be softened", blurredEdge in 9..145)
        assertEquals(0, Color.red(bitmap.getPixel(75, 350)))
        assertTrue(Color.red(bitmap.getPixel(825, 350)) > blurredEdge)
        assertEquals(Color.BLACK, cover.getPixel(445, 350))
        assertEquals(Color.rgb(244, 244, 244), bitmap.getPixel(793, 1093))
        assertEquals(Color.rgb(23, 23, 23), bitmap.getPixel(785, 1085))
    }

    @Test fun missingAlbumArtUsesAnOpaqueReadableBackgroundAndStillShares() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lines = listOf(LyricLine(0, "line", "translation"))
        val draft = LyricShareDraft("A long song name ".repeat(6), "Artist", null, lines, 0)
        val bitmap = LyricShareCard.render(context, draft, lines, true)
        assertEquals(900, bitmap.width)
        assertTrue(bitmap.height >= 1200)
        assertEquals(255, Color.alpha(bitmap.getPixel(0, 0)))
        assertTrue(Color.red(bitmap.getPixel(0, 0)) < 50)
    }
}
