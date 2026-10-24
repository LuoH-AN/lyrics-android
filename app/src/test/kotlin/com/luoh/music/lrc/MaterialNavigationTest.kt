package com.luoh.music.lrc

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
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
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers.ClassParameter

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

    @Test fun allWindowAnimationsInflateAndPageMotionStaysSubtle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (id in listOf(R.anim.activity_open_enter, R.anim.activity_open_exit,
            R.anim.activity_close_enter, R.anim.activity_close_exit, R.anim.dialog_enter, R.anim.dialog_exit)) {
            assertNotNull(android.view.animation.AnimationUtils.loadAnimation(context, id))
        }
        for (id in listOf(R.anim.activity_open_enter, R.anim.activity_open_exit,
            R.anim.activity_close_enter, R.anim.activity_close_exit)) {
            val animation = AnimationUtils.loadAnimation(context, id) as android.view.animation.AnimationSet
            assertTrue("Page transitions must not have a long settling tail", animation.computeDurationHint() <= 500L)
            val slide = animation.animations.filterIsInstance<android.view.animation.TranslateAnimation>().single()
            slide.initialize(1000, 1000, 1000, 1000)
            slide.startTime = 0L
            for (time in listOf(0L, slide.duration)) {
                val frame = android.view.animation.Transformation()
                slide.getTransformation(time, frame)
                val matrix = FloatArray(9)
                frame.matrix.getValues(matrix)
                assertTrue("A page must not travel across most of the screen",
                    kotlin.math.abs(matrix[android.graphics.Matrix.MTRANS_X]) <= 300f)
            }
        }
        // Robolectric 4.12 substitutes a LinearInterpolator for every resource. Invoke
        // the framework loader directly so this verifies our actual XML spring path.
        val spring = Shadow.directlyOn<Interpolator, AnimationUtils>(
            AnimationUtils::class.java, "loadInterpolator",
            ClassParameter.from(Context::class.java, context),
            ClassParameter.from(Int::class.javaPrimitiveType!!, R.interpolator.agora_spring)
        )
        assertTrue(spring is PathInterpolator)
        assertEquals(0f, spring.getInterpolation(0f), 0f)
        assertEquals(1f, spring.getInterpolation(1f), 0f)
        val samples = (1..999).map { spring.getInterpolation(it / 1000f) }
        assertTrue("Keep a small spring rebound", samples.any { it > 1f })
        assertTrue("The rebound must stay subtle", samples.maxOrNull()!! <= 1.025f)
        assertTrue("Most motion should complete early", spring.getInterpolation(.3f) > .85f)
        assertEquals("The final frames must be nearly settled", 1f, spring.getInterpolation(.9f), .002f)
    }

    @Test fun overviewDoesNotCreateAnyDetailControls() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
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
                val controller = Robolectric.buildActivity(SettingsActivity::class.java, SettingsActivity.intent(context, section)).create()
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

    @Test fun overviewNavigationUsesASeparateCategoryIntent() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val activity = controller.get()
            val content = activity.findViewById<ViewGroup>(R.id.settings_content)
            val appearance = descendants(content).filterIsInstance<android.widget.TextView>()
                .single { it.text.toString().startsWith("应用外观\n") }
            appearance.performClick()
            val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
            assertEquals(SettingsActivity::class.java.name, intent.component?.className)
            val detail = Robolectric.buildActivity(SettingsActivity::class.java, intent).create()
            try {
                assertNotNull(detail.get().findViewById<View>(R.id.theme_follow))
                assertNull(detail.get().findViewById<View>(R.id.home_motion_preview))
            } finally { detail.destroy() }
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
