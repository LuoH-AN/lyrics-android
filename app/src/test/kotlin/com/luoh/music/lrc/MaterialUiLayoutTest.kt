package com.luoh.music.lrc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.card.MaterialCardView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialUiLayoutTest {
    private fun context(): Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(), R.style.Theme_DesktopLyrics
    )

    private fun layout(view: View, widthDp: Int = 320, heightDp: Int = 640, preview: String? = null) {
        val density = view.resources.displayMetrics.density
        val host = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        (view.parent as? ViewGroup)?.removeView(view)
        host.get().setContentView(view)
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        if (preview != null) {
            val output = File("build/reports/material-ui/$preview.png")
            output.parentFile.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
        (view.parent as? ViewGroup)?.removeView(view)
        host.pause().stop().destroy()
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }

    private fun boundsIn(root: ViewGroup, view: View): android.graphics.Rect =
        android.graphics.Rect().also {
            view.getDrawingRect(it)
            root.offsetDescendantRectToMyCoords(view, it)
        }

    private fun assertNativeSettings(preview: String) {
        val root = LayoutInflater.from(context()).inflate(R.layout.activity_settings, null)
        layout(root, preview = preview)
        val toggle = root.findViewById<View>(R.id.overlay_toggle)
        assertTrue("Overlay switch must be Material", toggle is MaterialSwitch)
        val thumbBounds = requireNotNull((toggle as MaterialSwitch).thumbDrawable).bounds
        assertTrue("Material switch thumb must be drawn", thumbBounds.width() > 0)
        assertEquals("Switch thumb must remain circular", thumbBounds.width(), thumbBounds.height())
        assertTrue("Font slider must be Material", root.findViewById<View>(R.id.seek_font_size) is Slider)
        for (id in listOf(R.id.lyric_color_white, R.id.lyric_color_blue, R.id.lyric_color_black,
            R.id.lyric_color_pink, R.id.lyric_color_custom)) {
            val choice = root.findViewById<MaterialButton>(id)
            assertNotNull(choice)
            assertTrue("Color target must be measured", choice.width > 0)
            assertEquals("Color/add selector must stay square on narrow screens", choice.width, choice.height)
        }
        fun hasWebView(view: View): Boolean = view is WebView ||
            (view is ViewGroup && (0 until view.childCount).any { hasWebView(view.getChildAt(it)) })
        assertFalse("Settings must remain native", hasWebView(root))
    }

    @Test fun settingsInflateAndKeepCirclesOnSmallScreen() = assertNativeSettings("settings-light")

    @Test fun settingsInflateInDarkTheme() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-night")
        assertNativeSettings("settings-dark")
    }

    private class Actions : HomeLyricsView.Actions {
        var overlays = 0
        var seekPosition = -1L
        var settingsOpened = 0
        override fun toggleOverlay() { overlays++ }
        override fun openSettings() { settingsOpened++ }
        override fun seekTo(positionMs: Long) { seekPosition = positionMs }
        override fun togglePlay() {}
        override fun skipPrev() {}
        override fun skipNext() {}
        override fun editCustomLyrics() {}
        override fun manageCustomLyrics() {}
    }

    private fun assertHome(preview: String, widthDp: Int = 320, heightDp: Int = 640) {
        val home = HomeLyricsView(context())
        val actions = Actions()
        home.actions = actions
        val snapshot = HomeLyricsView.Snapshot(
            track = "这是一首名字很长的歌曲 · Live at Somewhere",
            artist = "歌手 / Artist", positionMs = 10000L, durationMs = 100000L,
            canPrevious = true, canNext = true
        )
        home.setSnapshot(snapshot, forcePosition = true)
        home.setLyrics(LyricDocument(listOf(
            LyricLine(0, "晚风轻轻吹过街角"),
            LyricLine(10000, "让歌词陪你走过每一天"),
            LyricLine(20000, "下一句，也在这里等你")
        ), true))
        home.setOverlayState(false)
        layout(home, widthDp, heightDp)
        val panel = home.findViewById<MaterialCardView>(R.id.home_player_card)
        assertTrue(panel.radius >= 24 * home.resources.displayMetrics.density)
        assertTrue(panel.width < home.width)
        assertFalse("Home must not reserve space for a toolbar", descendants(home).any { it is MaterialToolbar })
        assertFalse("Playback status chip must be removed", descendants(home).any { it is com.google.android.material.chip.Chip })
        val mainPlay = home.findViewById<MaterialButton>(R.id.home_play)
        assertEquals(255, android.graphics.Color.alpha(mainPlay.backgroundTintList!!.defaultColor))
        assertEquals(0, mainPlay.strokeWidth)
        val playBounds = android.graphics.Rect(0, 0, mainPlay.width, mainPlay.height)
        home.offsetDescendantRectToMyCoords(mainPlay, playBounds)
        assertTrue("Playback controls must fit vertically", playBounds.bottom <= home.height)
        assertTrue("Playback controls must fit horizontally", playBounds.left >= 0 && playBounds.right <= home.width)
        assertTrue("Lyrics must retain a viewport", home.findViewById<View>(R.id.home_lyrics_scroll).height > 0)
        val overlay = home.findViewById<MaterialButton>(R.id.home_overlay)
        val more = home.findViewById<MaterialButton>(R.id.home_more)
        assertEquals(more.width, overlay.width)
        assertEquals(more.height, overlay.height)
        assertEquals(more.top, overlay.top)
        assertEquals(more.iconSize, overlay.iconSize)
        val cover = home.findViewById<View>(R.id.home_cover)
        val song = home.findViewById<View>(R.id.home_song)
        val metadataText = song.parent as View
        val coverBounds = boundsIn(home, cover)
        val textBounds = boundsIn(home, metadataText)
        val overlayBounds = boundsIn(home, overlay)
        val moreBounds = boundsIn(home, more)
        assertEquals("Cover and actions must align vertically", coverBounds.exactCenterY(), overlayBounds.exactCenterY(), 1f)
        assertEquals("Song metadata and actions must align vertically", textBounds.exactCenterY(), overlayBounds.exactCenterY(), 1f)
        assertTrue("Song metadata must retain readable space", song.width >= 64 * home.resources.displayMetrics.density)
        assertTrue("Cover must not overlap song metadata", coverBounds.right <= textBounds.left)
        assertTrue("Song metadata must not overlap actions", textBounds.right <= overlayBounds.left)
        assertTrue("Actions must not overlap", overlayBounds.right <= moreBounds.left)
        assertTrue("Actions must remain inside the player", moreBounds.right <= boundsIn(home, panel).right - 16 * home.resources.displayMetrics.density)
        assertTrue("Cover placeholder must retain its visible size", cover.width - cover.paddingLeft - cover.paddingRight >= 24 * home.resources.displayMetrics.density)
        val slider = home.findViewById<Slider>(R.id.home_progress)
        assertEquals((6 * home.resources.displayMetrics.density).toInt(), slider.trackHeight)
        assertEquals(3 * home.resources.displayMetrics.density, slider.thumbStrokeWidth, 1f)
        slider.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals("Styled slider must preserve keyboard seeking", 15000L, actions.seekPosition)
        val downTime = android.os.SystemClock.uptimeMillis()
        listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP)
            .forEachIndexed { index, action ->
                val x = slider.width * if (index == 0) .35f else .5f
                val event = android.view.MotionEvent.obtain(downTime, downTime + index * 16L, action, x, slider.height / 2f, 0)
                slider.dispatchTouchEvent(event)
                event.recycle()
            }
        assertTrue("Styled slider must preserve drag seeking", actions.seekPosition in 49000L..51000L)
        assertTrue("Material padding must not clip the lyric icon", overlay.paddingStart + overlay.paddingEnd + overlay.iconSize <= overlay.width)
        assertTrue("Material padding must not clip the more icon", more.paddingStart + more.paddingEnd + more.iconSize <= more.width)
        assertEquals(0, overlay.strokeWidth)
        assertTrue("Actions must stay within the narrow screen", more.right <= (more.parent as View).width + 8 * home.resources.displayMetrics.density)
        overlay.performClick()
        assertEquals(1, actions.overlays)
        assertFalse("Home waits for the real service state", overlay.isSelected)
        home.setOverlayState(true)
        assertTrue(overlay.isSelected)
        assertTrue(android.graphics.Color.alpha(overlay.backgroundTintList!!.defaultColor) in 1..100)
        layout(home, widthDp, heightDp, preview = preview)
        home.setLyrics(LyricDocument(emptyList(), true))
        home.setSnapshot(snapshot.copy(playing = false))
        assertEquals("找不到歌词", home.findViewById<android.widget.TextView>(R.id.home_empty).text.toString())
        home.setSnapshot(HomeLyricsView.Snapshot())
        assertFalse(home.findViewById<MaterialButton>(R.id.home_play).isEnabled)
        assertTrue(overlay.isEnabled)
        assertTrue(more.isEnabled)
        overlay.performClick()
        assertEquals(2, actions.overlays)
        home.setActive(false)
    }

    @Test fun nativeHomeControlsAndEmptyStates() = assertHome("home-light")

    @Test fun nativeHomeControlsInDarkTheme() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-night")
        assertHome("home-dark")
    }

    @Test fun modernHomeOnLargerPhone() {
        RuntimeEnvironment.setQualifiers("w390dp-h780dp")
        assertHome("home-large-light", 390, 780)
    }

    @Test fun modernHomeOnLargerPhoneInDarkTheme() {
        RuntimeEnvironment.setQualifiers("w390dp-h780dp-night")
        assertHome("home-large-dark", 390, 780)
    }

    @Test fun modernHomeFitsLandscape() {
        RuntimeEnvironment.setQualifiers("w780dp-h360dp")
        assertHome("home-landscape", 780, 360)
    }

    @Test fun modernHomeFitsShortWindow() {
        RuntimeEnvironment.setQualifiers("w320dp-h480dp")
        assertHome("home-short", 320, 480)
    }

    @Test fun modernEmptyStateKeepsControlsAvailable() {
        val home = HomeLyricsView(context())
        home.setSnapshot(HomeLyricsView.Snapshot())
        layout(home, preview = "home-empty")
        assertEquals("未在播放", home.findViewById<android.widget.TextView>(R.id.home_empty).text.toString())
        assertTrue(home.findViewById<android.widget.TextView>(R.id.home_empty_detail).text.isNotBlank())
        assertTrue(home.findViewById<View>(R.id.home_more).isEnabled)
    }

    @Test fun modernMoreSheetOpensDetailedSettings() {
        val host = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val home = HomeLyricsView(ContextThemeWrapper(host.get(), R.style.Theme_DesktopLyrics))
        val actions = Actions()
        home.actions = actions
        host.get().setContentView(home)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        home.findViewById<View>(R.id.home_more).performClick()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as BottomSheetDialog
        try {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            assertTrue(dialog.isShowing)
            assertNotNull(dialog.findViewById<View>(R.id.home_more_sheet))
            val decor = requireNotNull(dialog.window).decorView
            if (decor.width > 0 && decor.height > 0) {
                val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                decor.draw(Canvas(bitmap))
                val output = File("build/reports/material-ui/home-more.png")
                output.parentFile.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            requireNotNull(dialog.findViewById<MaterialButton>(R.id.home_more_settings)).performClick()
            assertEquals(1, actions.settingsOpened)
            assertFalse(dialog.isShowing)
        } finally {
            dialog.dismiss()
            host.pause().stop().destroy()
        }
    }

    private fun assertLyricEditor(preview: String, editing: Boolean = false) {
        val context = context()
        val title = "布局测试歌曲"
        val artist = "测试歌手"
        val lyrics = "[00:01.00]保存后的歌词"
        val existing = if (editing) CustomLyricsStore.save(context, title, artist, "[00:00.00]原歌词") else null
        val intent = if (existing != null) CustomLyricsEditActivity.editIntent(context, existing.id)
            else CustomLyricsEditActivity.intent(context, title, artist)
        val controller = org.robolectric.Robolectric.buildActivity(CustomLyricsEditActivity::class.java, intent).setup()
        try {
            val activity = controller.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
            layout(root, preview = preview)
            val toolbar = descendants(root).filterIsInstance<MaterialToolbar>().single()
            val save = descendants(toolbar).filterIsInstance<MaterialButton>().single { it.text.toString() == "保存" }
            val saveBounds = boundsIn(root, save)
            val toolbarBounds = boundsIn(root, toolbar)
            val density = root.resources.displayMetrics.density
            assertEquals("Save must be vertically centered", toolbarBounds.exactCenterY(), saveBounds.exactCenterY(), 1f)
            assertTrue("Save must retain a trailing safe margin", saveBounds.right <= toolbarBounds.right - 12 * density)
            assertTrue("Save must fit inside the toolbar", saveBounds.left >= toolbarBounds.left &&
                saveBounds.top >= toolbarBounds.top && saveBounds.bottom <= toolbarBounds.bottom)
            assertTrue("Save must retain a full touch target", save.width >= 48 * density && save.height >= 48 * density)
            val toolbarTitle = descendants(toolbar).filterIsInstance<android.widget.TextView>()
                .single { it.text == toolbar.title }
            val titleBounds = boundsIn(root, toolbarTitle)
            assertTrue("Toolbar title $titleBounds must not overlap Save $saveBounds", titleBounds.right <= saveBounds.left)
            val lyricsField = descendants(root).filterIsInstance<com.google.android.material.textfield.TextInputLayout>()
                .single { it.hint.toString() == "LRC 时间轴歌词" }.editText!!
            lyricsField.setText(lyrics)
            save.performClick()
            assertTrue("Save action must still close the editor", activity.isFinishing)
            assertEquals(lyrics, CustomLyricsStore.find(context, title, artist)?.lyrics)
        } finally {
            controller.pause().stop().destroy()
            CustomLyricsStore.find(context, title, artist)?.let { CustomLyricsStore.delete(context, it.id) }
        }
    }

    @Test fun lyricEditorSaveFitsSmallScreen() = assertLyricEditor("lyric-editor-light")

    @Test fun existingLyricEditorSaveFitsInDarkTheme() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-night")
        assertLyricEditor("lyric-editor-dark", editing = true)
    }

    @Test fun iconButtonsHaveEqualTargetsAndConfirmedTranslucentState() {
        val context = context()
        var requests = 0
        val lyrics = NativeUi.iconButton(context, android.R.drawable.ic_menu_edit, "显示桌面歌词") { requests++ }
        val more = NativeUi.iconButton(context, android.R.drawable.ic_menu_more, "更多") {}
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(lyrics)
            addView(more)
        }
        layout(row, heightDp = 64)
        assertEquals(lyrics.width, more.width)
        assertEquals(lyrics.height, more.height)
        assertEquals(lyrics.top, more.top)
        assertEquals(lyrics.iconSize, more.iconSize)
        assertEquals(0, lyrics.strokeWidth)
        assertEquals(0, more.strokeWidth)
        assertFalse(lyrics.isChecked)
        lyrics.performClick()
        assertEquals(1, requests)
        assertFalse("Wait for service state instead of optimistic selection", lyrics.isChecked)
        lyrics.isChecked = true
        val checkedColor = lyrics.backgroundTintList!!.getColorForState(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked), 0
        )
        val alpha = android.graphics.Color.alpha(checkedColor)
        assertTrue("Active fill must be visible but translucent", alpha in 1..100)
        lyrics.isChecked = false
        val normalColor = lyrics.backgroundTintList!!.getColorForState(
            intArrayOf(android.R.attr.state_enabled), 0
        )
        assertEquals("Inactive icon must have no background", 0, android.graphics.Color.alpha(normalColor))
    }
}
