package com.luoh.music.lrc

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialNavigationTest {
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    private fun preview(activity: SettingsActivity, name: String) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val density = root.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        root.viewTreeObserver.dispatchOnPreDraw()
        val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        val output = java.io.File("build/reports/material-ui/$name.png")
        output.parentFile.mkdirs()
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun dialogsKeepTheirOwnWindowAnimations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (id in listOf(R.anim.dialog_enter, R.anim.dialog_exit)) {
            assertNotNull(AnimationUtils.loadAnimation(context, id))
        }
    }

    @Test fun overviewDoesNotCreateAnyDetailControls() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create().start()
        try {
            val content = controller.get().findViewById<ViewGroup>(android.R.id.content)
            assertFalse(descendants(content).any { it is Slider || it is HomeMotionPreview || it is CompactLyricsView })
            assertTrue(descendants(content).count() < 100)
            preview(controller.get(), "settings-overview")
        } finally { controller.destroy() }
    }

    @Test fun everyCategoryOpensIndependentlyInBothThemes() {
        for (night in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers("w320dp-h640dp${if (night) "-night" else "-notnight"}")
            for (section in SettingsActivity.Section.values()) {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val controller = Robolectric.buildActivity(SettingsActivity::class.java, SettingsActivity.intent(context, section)).create().start()
                try {
                    val content = controller.get().findViewById<ViewGroup>(R.id.settings_content)
                    assertEquals("Only the selected category should be inflated", 1, content.childCount)
                    assertTrue(descendants(content).any { it is MaterialCardView })
                    if (section != SettingsActivity.Section.MOTION) assertFalse(descendants(content).any { it is HomeMotionPreview })
                    if (section != SettingsActivity.Section.OVERLAY) assertNull(controller.get().findViewById<View>(R.id.seek_font_weight))
                    preview(controller.get(), "category-${section.name.lowercase()}-${if (night) "dark" else "light"}")
                } finally { controller.destroy() }
            }
        }
    }

    @Test fun overviewNavigationUsesTheSameWindowAndLazyPage() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create().start()
        try {
            val activity = controller.get()
            val window = activity.window
            val content = activity.findViewById<ViewGroup>(R.id.settings_content)
            val appearance = descendants(content).filterIsInstance<android.widget.TextView>()
                .single { it.text.toString().startsWith("应用外观\n") }
            appearance.performClick()
            assertNull(org.robolectric.Shadows.shadowOf(activity).nextStartedActivity)
            assertSame(window, activity.window)
            assertEquals(2, activity.pageNavigator.depth)
            assertNotNull(activity.pageNavigator.currentPage!!.findViewById<View>(R.id.theme_follow))
            assertNull(activity.pageNavigator.currentPage!!.findViewById<View>(R.id.home_motion_preview))
        } finally { controller.destroy() }
    }

    @Test fun thousandRowsOnlyCreateVisibleMaterialCells() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create().start().visible()
        try {
            val list = NativeUi.listScreen(controller.get(), "大量记录")
            var created = 0
            NativeUi.items(list, (1..1000).toList(), { created++; LibraryRow(it) }) { view, number ->
                (view as LibraryRow).bind("歌曲 $number", "歌手", action = "编辑")
            }
            val root = controller.get().findViewById<ViewGroup>(android.R.id.content)
            val density = root.resources.displayMetrics.density
            root.measure(View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            root.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(1000, list.adapter!!.itemCount)
            assertTrue("The list must display cells", created > 0)
            assertTrue("Offscreen records must not inflate", created < 20)
        } finally { controller.stop().destroy() }
    }
}
