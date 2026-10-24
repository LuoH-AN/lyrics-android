package com.luoh.music.lrc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AgoraScaffoldParityTest {
    @Before fun deterministicFrames() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }
    private fun frames(count: Int) { repeat(count) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) } }
    private fun layout(view: View) {
        val density = view.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        view.viewTreeObserver.dispatchOnPreDraw()
    }
    private fun topIn(root: ViewGroup, view: View): Int = Rect().also {
        view.getDrawingRect(it); root.offsetDescendantRectToMyCoords(view, it)
    }.top

    @Test fun titleAndRowsUseOneScrollCoordinateAndDockAfter66dp() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val root = activity.pageNavigator.currentPage!!.pageRoot
            layout(root)
            val scaffold = root.getChildAt(0) as AgoraSettingsScaffold
            val scroll = scaffold.findViewById<NestedScrollView>(R.id.settings_scroll)
            val row = (scroll.getChildAt(0) as ViewGroup).getChildAt(0)
            val density = root.resources.displayMetrics.density
            val rowTop = topIn(root, row)
            val titleTop = scaffold.titleView.translationY
            val title = scaffold.titleView
            scroll.scrollTo(0, (33 * density).toInt())
            assertEquals("Rows track the same finger distance as the title", 33 * density,
                (rowTop - topIn(root, row)).toFloat(), 1f)
            assertEquals(titleTop - 33 * density, title.translationY, 1f)
            assertTrue(title.scaleX < 1f)
            scroll.scrollTo(0, (66 * density).toInt())
            assertEquals(titleTop - 66 * density, title.translationY, 1f)
            assertEquals(70 * density, title.translationX, 1f)
            assertEquals(22 * density, title.textSize * title.scaleX, 1f)
            val dock = title.translationY
            scroll.scrollTo(0, (90 * density).toInt())
            assertEquals("Title stays docked while rows continue to scroll", dock, title.translationY, 0f)
            assertEquals(90 * density, (rowTop - topIn(root, row)).toFloat(), 1f)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun headerIsARecycledScrollingItemAndControlsSurviveDataReplacement() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val controls = NativeUi.field(activity, "搜索")
            val list = NativeUi.listScreen(activity, "记录", controls)
            fun fill() = NativeUi.items(list, (1..1000).toList(), { LibraryRow(it) }) { view, number ->
                (view as LibraryRow).bind("歌曲 $number", "歌手", action = "编辑")
            }
            fill()
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            layout(root)
            controls.editText!!.setText("保留搜索")
            val scaffold = list.parent as AgoraSettingsScaffold
            val y = scaffold.titleView.translationY
            list.scrollBy(0, NativeUi.dp(activity, 33))
            assertEquals(y - NativeUi.dp(activity, 33), scaffold.titleView.translationY, 1f)
            assertTrue("Lazy list retains only visible rows and the header", list.childCount < 20)
            fill(); layout(root)
            assertEquals("保留搜索", controls.editText!!.text.toString())
            assertTrue(controls.isAttachedToWindow)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun innerCrossfadeNeverLeaksTheContrastingHomeThroughSettingsBackground() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        var overlay: AgoraNavigationOverlay? = null
        try {
            val activity = controller.get()
            val root = FrameLayout(activity).apply { setBackgroundColor(Color.MAGENTA) }
            activity.setContentView(root)
            val outer = AgoraNavigationOverlay(activity) {}
            overlay = outer
            root.addView(outer, ViewGroup.LayoutParams(-1, -1))
            val bg = ContextCompat.getColor(activity, R.color.app_bg)
            fun page() = AgoraPageSurface(activity).apply { setBackgroundColor(bg) }
            outer.host.show(page(), true)
            outer.enter(false)
            layout(root)
            outer.host.show(page(), true)
            frames(10); layout(root)
            assertTrue(outer.host.isTransitioning)
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            try {
                root.draw(Canvas(bitmap))
                for (fraction in listOf(.05f, .1f, .5f, .9f)) {
                    assertEquals("Outer Surface must fill holes in both moving/fading pages", bg,
                        bitmap.getPixel((root.width * fraction).toInt(), root.height / 2))
                }
            } finally { bitmap.recycle() }
        } finally { overlay?.dispose(); controller.pause().stop().destroy() }
    }

    @Test fun reopeningCategoryRestoresSessionScrollIntoANewOwner() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val destination = SettingsActivity.intent(activity, SettingsActivity.Section.OVERLAY)
            activity.pageNavigator.open(destination)
            layout(root); frames(100)
            val old = activity.pageNavigator.currentPage!!.findViewById<NestedScrollView>(R.id.settings_scroll)
            old.scrollTo(0, NativeUi.dp(activity, 180))
            val position = old.scrollY
            assertTrue(position > 0)
            activity.pageNavigator.back(); layout(root); frames(100)
            activity.pageNavigator.open(destination); layout(root)
            val fresh = activity.pageNavigator.currentPage!!.findViewById<NestedScrollView>(R.id.settings_scroll)
            assertNotSame(old, fresh)
            assertEquals(position, fresh.scrollY)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun policyChangeRemovesSpatialTransformsWithoutReplayingEntrance() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val host = AgoraPageHost(activity)
        val key = Settings.Global.WINDOW_ANIMATION_SCALE
        val previous = Settings.Global.getFloat(activity.contentResolver, key, 1f)
        try {
            activity.setContentView(host)
            host.show(AgoraPageSurface(activity), true)
            layout(host)
            val target = AgoraPageSurface(activity)
            host.show(target, true); frames(7)
            val alpha = target.alpha
            Settings.Global.putFloat(activity.contentResolver, key, 0f)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0f, target.translationX, 0f)
            assertEquals(1f, target.scaleX, 0f)
            assertEquals("Policy changes must not replay the initial transparent state", alpha, target.alpha, .0001f)
            frames(100)
            assertFalse(host.isTransitioning)
        } finally {
            Settings.Global.putFloat(activity.contentResolver, key, previous)
            host.dispose(); controller.pause().stop().destroy()
        }
    }

    @Test fun systemInsetsBelongInsideTheFullScreenScaffold() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val root = activity.pageNavigator.currentPage!!.pageRoot
            val scaffold = root.getChildAt(0) as AgoraSettingsScaffold
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 32)).build()
            ViewCompat.dispatchApplyWindowInsets(scaffold, insets)
            layout(root)
            assertEquals(Color.TRANSPARENT, activity.window.statusBarColor)
            assertEquals(24 + NativeUi.dp(activity, 84), scaffold.titleView.translationY.roundToInt())
            val back = scaffold.findViewById<View>(R.id.settings_back)
            assertEquals(24 + NativeUi.dp(activity, 12), back.top)
            val content = scaffold.findViewById<View>(R.id.settings_content)
            assertEquals(32 + NativeUi.dp(activity, 32), content.paddingBottom)
        } finally { controller.pause().stop().destroy() }
    }
}
