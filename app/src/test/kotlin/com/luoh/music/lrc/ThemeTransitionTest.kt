package com.luoh.music.lrc

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ThemeTransitionTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var root: ViewGroup
    private lateinit var transition: ThemeTransition

    @Before fun createContent() {
        controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        replaceContent(Color.RED)
        root = controller.get().findViewById(android.R.id.content)
        layoutContent()
        assertTrue(root.isAttachedToWindow)
        transition = ThemeTransition(root) { true }
    }

    @After fun destroyContent() {
        transition.dispose()
        controller.pause().stop().destroy()
    }

    private fun replaceContent(color: Int) {
        controller.get().setContentView(View(controller.get()).apply { setBackgroundColor(color) })
    }

    private fun layoutContent() {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 320, 640)
    }

    private fun displayedColor(): Int {
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        return image.getPixel(160, 200).also { image.recycle() }
    }

    private fun finishFrames() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

    @Test fun oldFrameCoversNewContentUntilLayoutAndFadeComplete() {
        transition.capture()
        assertTrue(transition.isRunning)
        replaceContent(Color.BLUE)
        layoutContent()
        var restored = false
        transition.finish { restored = true }
        assertFalse(restored)
        assertEquals(Color.RED, displayedColor())
        assertEquals(1f, root.alpha, 0f)
        root.viewTreeObserver.dispatchOnPreDraw()
        assertTrue(restored)
        finishFrames()
        assertFalse(transition.isRunning)
        assertEquals(Color.BLUE, displayedColor())
        assertEquals(1f, root.alpha, 0f)
    }

    @Test fun rapidSwitchDiscardsStaleLayoutCallbackAndSnapshot() {
        var staleRestores = 0
        var currentRestores = 0
        transition.capture()
        replaceContent(Color.BLUE)
        layoutContent()
        transition.finish { staleRestores++ }
        transition.capture()
        replaceContent(Color.GREEN)
        layoutContent()
        transition.finish { currentRestores++ }
        root.viewTreeObserver.dispatchOnPreDraw()
        finishFrames()
        assertEquals(0, staleRestores)
        assertEquals(1, currentRestores)
        assertFalse(transition.isRunning)
        assertEquals(Color.GREEN, displayedColor())
    }

    @Test fun newSwitchCanInterruptAnActiveFade() {
        transition.capture()
        replaceContent(Color.BLUE)
        layoutContent()
        transition.finish()
        root.viewTreeObserver.dispatchOnPreDraw()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        transition.capture()
        replaceContent(Color.GREEN)
        layoutContent()
        transition.finish()
        root.viewTreeObserver.dispatchOnPreDraw()
        finishFrames()
        assertFalse(transition.isRunning)
        assertEquals(Color.GREEN, displayedColor())
    }

    @Test fun disabledAnimationsStillRestoreStateAfterLayout() {
        transition.dispose()
        transition = ThemeTransition(root) { false }
        transition.capture()
        assertFalse(transition.isRunning)
        replaceContent(Color.BLUE)
        layoutContent()
        var restored = false
        transition.finish { restored = true }
        assertFalse(restored)
        root.viewTreeObserver.dispatchOnPreDraw()
        assertTrue(restored)
        assertFalse(transition.isRunning)
        assertEquals(Color.BLUE, displayedColor())
        assertEquals(1f, root.alpha, 0f)
    }

    @Test fun disablingAnimationsBetweenCaptureAndFirstDrawReleasesSnapshot() {
        transition.dispose()
        var enabled = true
        transition = ThemeTransition(root) { enabled }
        transition.capture()
        assertTrue(transition.isRunning)
        replaceContent(Color.BLUE)
        layoutContent()
        transition.finish()
        enabled = false
        root.viewTreeObserver.dispatchOnPreDraw()
        assertFalse(transition.isRunning)
        assertEquals(Color.BLUE, displayedColor())
    }

    @Test fun disposeCancelsPendingDrawAndCannotBeRestarted() {
        transition.capture()
        var restored = false
        transition.finish { restored = true }
        transition.dispose()
        transition.dispose()
        root.viewTreeObserver.dispatchOnPreDraw()
        finishFrames()
        assertFalse(restored)
        assertFalse(transition.isRunning)
        transition.capture()
        assertFalse(transition.isRunning)
    }

    @Test fun detachedRootReleasesSnapshot() {
        transition.capture()
        assertTrue(transition.isRunning)
        (root.parent as ViewGroup).removeView(root)
        assertFalse(transition.isRunning)
    }
}
