package com.luoh.music.lrc

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricColorPickerDialogTest {
    private fun preferences() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    private fun open(activity: SettingsActivity): AlertDialog {
        activity.findViewById<View>(R.id.lyric_color_custom).performClick()
        return (ShadowDialog.getLatestDialog() as AlertDialog).also { assertTrue(it.isShowing) }
    }

    private fun savedColor() = preferences().getString(LyricsOverlayService.PREF_LYRIC_COLOR_COMPACT, null)

    @Test fun hexValidationAcceptsOnlySixOpaqueDigits() {
        assertEquals("#12ABEF", LyricColorPickerDialog.normalizeHex(" 12aBeF "))
        assertEquals("#00FF88", LyricColorPickerDialog.normalizeHex("#00ff88"))
        for (invalid in listOf("", "#123", "##123456", "#FFFFFFFF", "#ZZZZZZ", "red")) {
            assertNull(invalid, LyricColorPickerDialog.normalizeHex(invalid))
        }
    }

    @Test fun paletteSelectionStaysLocalUntilApplyAndCancelDoesNotSave() {
        preferences().edit().clear().putString(LyricsOverlayService.PREF_LYRIC_COLOR_COMPACT, "#111111").commit()
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val activity = controller.get()
            var dialog = open(activity)
            val palette = dialog.findViewById<LinearLayout>(R.id.color_palette)!!
            val blue = descendants(palette).filterIsInstance<MaterialButton>().single { it.tag == "#9FD8FF" }
            blue.performClick()
            assertTrue(blue.isChecked)
            assertEquals("#9FD8FF", dialog.findViewById<TextInputEditText>(R.id.color_hex_input)!!.text.toString())
            assertEquals("#9FD8FF", dialog.findViewById<TextView>(R.id.color_picker_preview)!!.text.toString())
            assertEquals("#111111", savedColor())
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse(dialog.isShowing)
            assertEquals("#111111", savedColor())

            dialog = open(activity)
            dialog.findViewById<TextInputEditText>(R.id.color_hex_input)!!.setText("9fd8ff")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertFalse(dialog.isShowing)
            assertEquals("#9FD8FF", savedColor())
            assertTrue(activity.findViewById<MaterialButton>(R.id.lyric_color_blue).isChecked)
            val preview = activity.findViewById<CompactLyricsView>(R.id.overlay_preview)
            val lyricTexts = descendants(preview).filterIsInstance<TextView>().toList()
            assertTrue(lyricTexts.isNotEmpty())
            lyricTexts.forEach { assertEquals(Color.parseColor("#9FD8FF"), it.currentTextColor) }
        } finally { controller.destroy() }
    }

    @Test fun invalidHexDisablesApplyWithoutClosingTheDialog() {
        preferences().edit().clear().putString(LyricsOverlayService.PREF_LYRIC_COLOR_COMPACT, "#FFFFFF").commit()
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val dialog = open(controller.get())
            val input = dialog.findViewById<TextInputEditText>(R.id.color_hex_input)!!
            input.setText("#123")
            assertNotNull(dialog.findViewById<TextInputLayout>(R.id.color_hex_field)!!.error)
            assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue(dialog.isShowing)
            assertEquals("#FFFFFF", savedColor())
            input.setText("#00ff88")
            assertNull(dialog.findViewById<TextInputLayout>(R.id.color_hex_field)!!.error)
            assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertEquals("#00FF88", dialog.findViewById<TextView>(R.id.color_picker_preview)!!.text.toString())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals("#00FF88", savedColor())
        } finally { controller.destroy() }
    }

    @Test fun paletteUsesFourColumnsAndFullTouchTargetsInBothThemes() {
        for (night in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers("w320dp-h640dp${if (night) "-night" else "-notnight"}")
            preferences().edit().clear().commit()
            val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
            try {
                val activity = controller.get()
                val dialog = open(activity)
                val palette = dialog.findViewById<LinearLayout>(R.id.color_palette)!!
                val content = palette.parent as ViewGroup
                val width = NativeUi.dp(activity, 224)
                content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                content.layout(0, 0, width, content.measuredHeight)
                assertFalse(descendants(content).any { it is Slider })
                assertEquals(LyricColorPickerDialog.COLORS.size / 4, palette.childCount)
                for (rowIndex in 0 until palette.childCount) {
                    val row = palette.getChildAt(rowIndex) as LinearLayout
                    assertEquals(4, row.childCount)
                    for (column in 0 until row.childCount) {
                        val cell = row.getChildAt(column) as ViewGroup
                        val swatch = cell.getChildAt(0) as MaterialButton
                        assertEquals(NativeUi.dp(activity, 48), swatch.width)
                        assertEquals(NativeUi.dp(activity, 48), swatch.height)
                        assertTrue(swatch.left >= 0 && swatch.right <= cell.width)
                        assertTrue(swatch.top >= 0 && swatch.bottom <= cell.height)
                        assertTrue(swatch.contentDescription.isNotBlank())
                    }
                }
                val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
                content.draw(Canvas(bitmap))
                val output = File("build/reports/material-ui/color-picker-${if (night) "dark" else "light"}.png")
                output.parentFile.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                dialog.dismiss()
            } finally { controller.destroy() }
        }
    }

    @Test fun openPaletteRetainsAnInvalidDraftWhenThemeChanges() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-notnight")
        preferences().edit().clear().putString(LyricsOverlayService.PREF_LYRIC_COLOR_COMPACT, "#FFFFFF").commit()
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create().start().visible()
        try {
            val activity = controller.get()
            val original = open(activity)
            original.findViewById<TextInputEditText>(R.id.color_hex_input)!!.setText("#12")
            val configuration = Configuration(activity.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            controller.configurationChange(configuration)
            val replacement = ShadowDialog.getLatestDialog() as AlertDialog
            assertNotSame(original, replacement)
            assertFalse(original.isShowing)
            assertTrue(replacement.isShowing)
            assertEquals("#12", replacement.findViewById<TextInputEditText>(R.id.color_hex_input)!!.text.toString())
            assertFalse(replacement.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertEquals("#FFFFFF", savedColor())
            replacement.dismiss()
        } finally { controller.stop().destroy() }
    }
}
