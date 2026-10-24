package com.luoh.music.lrc

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // Exercise actual RenderNode z-order and transformed hit testing.
class AgoraPageHostTest {
    @Before fun deterministicFrames() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        // Android grants this merged-manifest signature permission; Robolectric requires an explicit grant.
        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        // AndroidX 1.0 keeps a ThreadLocal handler with the previous test's Choreographer.
        // Robolectric resets its clock, so obtain a fresh AndroidX frame provider for this test.
        val handler = Class.forName("androidx.dynamicanimation.animation.AnimationHandler")
        org.robolectric.util.ReflectionHelpers.getStaticField<ThreadLocal<Any>>(handler, "sAnimatorHandler").remove()
    }
    private fun frames(count: Int) { repeat(count) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) } }
    private fun layout(view: View) {
        val d = view.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((320 * d).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((640 * d).toInt(), View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        view.viewTreeObserver.dispatchOnPreDraw()
    }
    private fun click(view: View, x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, time + 1, action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    @Test fun pairedSpringAndFadeKeepsTargetInteractiveAndShieldsOutgoing() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val host = AgoraPageHost(activity)
            activity.setContentView(host)
            var oldClicks = 0
            var targetClicks = 0
            fun page(action: () -> Unit) = AgoraPageSurface(activity).apply {
                addView(Button(activity).apply { setOnClickListener { action() } }, ViewGroup.LayoutParams(-1, -1))
            }
            val old = page { oldClicks++ }
            host.show(old, true)
            layout(host)
            assertFalse(host.isTransitioning)
            assertEquals(1f, old.alpha, 0f)
            assertEquals(0f, old.translationX, 0f)
            val target = page { targetClicks++ }
            host.show(target, true)
            layout(host)
            assertEquals(host.width * .75f, target.translationX, 1f)
            assertEquals(.94f, target.scaleX, .0001f)
            assertEquals(0f, target.alpha, 0f)
            assertEquals("Target must draw and receive input above the shield", 2f, target.z, 0f)
            assertEquals(host.width, target.width)
            assertEquals(host.width, (target.getChildAt(0)).width)
            assertTrue(old.consumesInput)
            click(host, host.width * .9f, host.height * .5f)
            click(host, host.width * .1f, host.height * .5f)
            shadowOf(Looper.getMainLooper()).idle() // View posts its PerformClick after ACTION_UP.
            assertEquals("The incoming page accepts taps before its entrance finishes", 1, targetClicks)
            assertEquals("Uncovered areas must never tap through to outgoing controls", 0, oldClicks)
            frames(8)
            assertTrue(target.alpha > 0f && target.alpha < 1f)
            assertTrue(old.alpha < 1f)
            var crossedTarget = false
            repeat(30) { frames(1); if (target.translationX < 0f) crossedTarget = true }
            assertTrue("Use Agora's live low-bouncy spring, not a sampled window curve", crossedTarget)
            frames(100)
            assertFalse(host.isTransitioning)
            assertEquals(1, host.pageCount)
            assertEquals(1, host.childCount)
            assertEquals(0f, target.translationX, 0f)
            assertEquals(1f, target.alpha, 0f)
            assertEquals(1f, target.scaleX, 0f)
            assertNull(old.parent)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun interruptionDropsStalePageAndPreservesCurrentScaleAndAlpha() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val host = AgoraPageHost(activity)
            activity.setContentView(host)
            val initial = AgoraPageSurface(activity)
            val middle = AgoraPageSurface(activity)
            val last = AgoraPageSurface(activity)
            host.show(initial, true); layout(host)
            host.show(middle, true); layout(host); frames(7)
            val alpha = middle.alpha
            val scale = middle.scaleX
            host.show(last, false); layout(host)
            assertNull(initial.parent)
            assertEquals(2, host.pageCount)
            assertTrue(middle.consumesInput)
            assertEquals(alpha, middle.alpha, 0f)
            assertEquals(scale, middle.scaleX, 0f)
            assertEquals(-host.width * .75f, last.translationX, 1f)
            frames(150)
            assertEquals(1, host.pageCount)
            assertSame(last, host.targetView)
            assertEquals(0f, last.translationX, 0f)
            assertNull(middle.parent)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun reducedMotionKeepsCrossfadeWithoutSpatialTransforms() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        try {
            Settings.Global.putFloat(activity.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 0f)
            val host = AgoraPageHost(activity)
            activity.setContentView(host)
            val old = AgoraPageSurface(activity)
            val target = AgoraPageSurface(activity)
            host.show(old, true); layout(host)
            host.show(target, true); layout(host); frames(8)
            assertEquals(0f, target.translationX, 0f)
            assertEquals(0f, old.translationX, 0f)
            assertEquals(1f, target.scaleX, 0f)
            assertTrue(target.alpha > 0f && target.alpha < 1f)
            frames(30)
            assertEquals(1, host.pageCount)
            assertEquals(1f, target.alpha, 0f)
        } finally {
            Settings.Global.putFloat(activity.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
            controller.pause().stop().destroy()
        }
    }

    @Test fun navigationBackRestoresScrollAndOnlyCurrentPageOwnsControls() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            layout(root)
            val overview = activity.pageNavigator.currentPage!!
            val scroll = overview.findViewById<NestedScrollView>(R.id.settings_scroll)
            scroll.scrollTo(0, 150)
            val y = scroll.scrollY
            assertTrue(y > 0)
            activity.pageNavigator.open(SettingsActivity.intent(activity, SettingsActivity.Section.OVERLAY))
            layout(root); frames(7)
            assertNotNull(activity.pageNavigator.currentPage!!.findViewById<View>(R.id.seek_font_weight))
            activity.onBackPressedDispatcher.onBackPressed()
            layout(root)
            assertEquals(y, activity.pageNavigator.currentPage!!.findViewById<NestedScrollView>(R.id.settings_scroll).scrollY)
            activity.pageNavigator.open(SettingsActivity.intent(activity, SettingsActivity.Section.MOTION))
            layout(root); frames(150)
            assertEquals(2, activity.pageNavigator.depth)
            assertEquals(1, activity.supportFragmentManager.fragments.size)
            assertNotNull(activity.findViewById<View>(R.id.home_motion_preview))
            assertNull(activity.findViewById<View>(R.id.seek_font_weight))
            assertNull(shadowOf(activity).nextStartedActivity)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun homeEntryUsesOverlayAndRetainsHomeOnReturn() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val home = activity.findViewById<View>(R.id.lyric_home)
            val window = activity.window
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            layout(root)
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
            layout(root)
            assertSame(window, activity.window)
            assertSame(home, activity.findViewById<View>(R.id.lyric_home))
            assertEquals(2, root.childCount)
            val overlay = root.getChildAt(1) as AgoraNavigationOverlay
            val surface = overlay.getChildAt(1)
            assertEquals(root.width * .25f, surface.translationX, 1f)
            assertEquals(.92f, surface.scaleX, .0001f)
            frames(6)
            activity.onBackPressedDispatcher.onBackPressed()
            frames(40)
            assertEquals(1, root.childCount)
            assertSame(home, activity.findViewById<View>(R.id.lyric_home))
            assertFalse(activity.pageNavigator.isOpen)
            assertFalse(activity.isFinishing)
            assertNull(shadowOf(activity).nextStartedActivity)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun activityRecreationKeepsNavigationAndUnsavedEditorDraft() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val state = Bundle()
        try {
            val activity = controller.get()
            activity.pageNavigator.open(Intent(activity, CustomLyricsEditActivity::class.java))
            val fields = descendants(activity.pageNavigator.currentPage!!.pageRoot)
                .filterIsInstance<com.google.android.material.textfield.TextInputLayout>().map { it.editText!! }.toList()
            fields[0].setText("未保存的歌名")
            fields[2].setText("[00:01.00]未保存的歌词")
            controller.saveInstanceState(state)
        } finally { controller.pause().stop().destroy() }
        val restored = Robolectric.buildActivity(SettingsActivity::class.java).create(state).start().resume().visible()
        try {
            val activity = restored.get()
            assertEquals(2, activity.pageNavigator.depth)
            assertTrue(activity.pageNavigator.currentPage is CustomLyricsEditPage)
            val fields = descendants(activity.pageNavigator.currentPage!!.pageRoot)
                .filterIsInstance<com.google.android.material.textfield.TextInputLayout>().map { it.editText!! }.toList()
            assertEquals("未保存的歌名", fields[0].text.toString())
            assertEquals("[00:01.00]未保存的歌词", fields[2].text.toString())
        } finally { restored.pause().stop().destroy() }
    }

    @Test fun lyricSourceListAndVersionsUseHostNavigationAndRestoreSearch() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = app.getSharedPreferences(LyricsOverlayService.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val previous = prefs.getString("match_memory_v2", null)
        prefs.edit().putString("match_memory_v2", """[{"key":"song-one","title":"测试歌曲","artist":"测试歌手","source":"qq","candidate":{"recordId":"one","title":"测试歌曲","lyrics":"[00:01.00]歌词预览"}}]""").commit()
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            activity.pageNavigator.open(Intent(activity, LyricSourceManagerActivity::class.java))
            val listPage = activity.pageNavigator.currentPage!!
            val search = descendants(listPage.pageRoot)
                .filterIsInstance<com.google.android.material.textfield.TextInputLayout>().single().editText!!
            search.setText("测试歌曲")
            var action: com.google.android.material.button.MaterialButton? = null
            for (i in 0 until 200) {
                frames(1); layout(root)
                action = descendants(listPage.pageRoot).filterIsInstance<com.google.android.material.button.MaterialButton>()
                    .firstOrNull { it.text.toString() == "查看版本与预览" }
                if (action != null) break
                Thread.sleep(5)
            }
            assertNotNull("The asynchronously loaded song must be visible", action)
            action!!.performClick()
            layout(root)
            assertEquals(3, activity.pageNavigator.depth)
            assertTrue(activity.pageNavigator.currentPage is LyricSourceManagerPage)
            assertEquals("song-one", activity.pageNavigator.currentPage!!.arguments?.getString("source_key"))
            activity.onBackPressedDispatcher.onBackPressed()
            layout(root); frames(150)
            assertEquals(2, activity.pageNavigator.depth)
            assertEquals(1, activity.supportFragmentManager.fragments.size)
            val restoredSearch = descendants(activity.pageNavigator.currentPage!!.pageRoot)
                .filterIsInstance<com.google.android.material.textfield.TextInputLayout>().single().editText!!
            assertEquals("测试歌曲", restoredSearch.text.toString())
            assertNull(shadowOf(activity).nextStartedActivity)
        } finally {
            controller.pause().stop().destroy()
            prefs.edit().putString("match_memory_v2", previous).commit()
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
