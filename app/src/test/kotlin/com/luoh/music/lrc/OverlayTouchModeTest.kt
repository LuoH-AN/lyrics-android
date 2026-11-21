package com.luoh.music.lrc

import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OverlayTouchModeTest {
    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

    @Test fun lockingPassesTouchesThroughAndHonorsTheSystemOpacityLimit() {
        val params = WindowManager.LayoutParams().apply { flags = baseFlags }
        applyOverlayTouchMode(params, locked = true, maximumObscuringOpacity = 0.65f)
        assertEquals(baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, params.flags)
        assertEquals(0.65f, params.alpha, 0f)
    }

    @Test fun unlockingRestoresDraggingAndFullOpacityWithoutDroppingOtherFlags() {
        val params = WindowManager.LayoutParams().apply { flags = baseFlags }
        applyOverlayTouchMode(params, locked = true, maximumObscuringOpacity = 0.8f)
        // Repeated lock requests must not toggle or corrupt flags.
        applyOverlayTouchMode(params, locked = true, maximumObscuringOpacity = 0.8f)
        applyOverlayTouchMode(params, locked = false, maximumObscuringOpacity = 0.8f)
        assertEquals(baseFlags, params.flags)
        assertEquals(1f, params.alpha, 0f)
    }

    @Test fun olderAndroidDoesNotNeedToDimTheLockedWindow() {
        val params = WindowManager.LayoutParams().apply { flags = baseFlags }
        applyOverlayTouchMode(params, locked = true, maximumObscuringOpacity = null)
        assertEquals(baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, params.flags)
        assertEquals(1f, params.alpha, 0f)
    }
}
