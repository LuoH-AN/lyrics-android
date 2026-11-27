package com.luoh.music.lrc

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.overlay.OverlayWindow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LyricsOverlayLockTest {
    @Before fun preparePreferencesAndBroadcastPermission() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        application.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        shadowOf(application).grantPermissions("${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }

    @Test fun notificationUnlockRestoresTouchAndControlsAndRefreshesTheAction() {
        // Inject a plain window so the test exercises service actions without starting
        // media monitoring, networking, or the offscreen lyric-matching WebView.
        val service = Robolectric.buildService(LyricsOverlayService::class.java).get()
        val window = ReflectionHelpers.callInstanceMethod<OverlayWindow>(service, "getOverlayWindow")
        val params = WindowManager.LayoutParams().apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        val chrome = LinearLayout(service)
        ReflectionHelpers.setField(window, "windowParams", params)
        ReflectionHelpers.setField(window, "overlayRoot", FrameLayout(service))
        ReflectionHelpers.setField(window, "chromeBar", chrome)

        service.onStartCommand(Intent(service, LyricsOverlayService::class.java)
            .setAction(LyricsOverlayService.ACTION_LOCK), 0, 1)
        assertTrue(service.isPositionLocked())
        assertTrue(service.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("position_locked", false))
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertEquals(View.INVISIBLE, chrome.visibility)
        ReflectionHelpers.callInstanceMethod<Void>(window, "revealChrome",
            ClassParameter.from(Long::class.javaPrimitiveType!!, 3500L))
        assertEquals("Locked controls must stay hidden", View.INVISIBLE, chrome.visibility)

        val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        fun controls(notification: Notification): View = requireNotNull(notification.contentView)
            .apply(service, FrameLayout(service))
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals("桌面歌词", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(0, notification.actions?.size ?: 0)
        val staleUnlock = controls(notification).findViewById<View>(R.id.notification_lyrics_lock)
        assertEquals("解锁", staleUnlock.contentDescription.toString())
        staleUnlock.performClick()
        val started = shadowOf(service.application).nextStartedService
        assertEquals(LyricsOverlayService.ACTION_UNLOCK, started.action)
        service.onStartCommand(started, 0, 2)
        assertFalse(service.isPositionLocked())
        assertEquals(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, params.flags)
        assertEquals(1f, params.alpha, 0f)
        assertEquals(View.VISIBLE, chrome.visibility)
        assertFalse(service.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("position_locked", true))

        val updated = shadowOf(manager).allNotifications.single()
        val updatedControls = controls(updated)
        val lock = updatedControls.findViewById<View>(R.id.notification_lyrics_lock)
        assertEquals("锁定", lock.contentDescription.toString())
        lock.performClick()
        assertEquals(LyricsOverlayService.ACTION_LOCK, shadowOf(service.application).nextStartedService.action)
        val close = updatedControls.findViewById<View>(R.id.notification_lyrics_close)
        assertEquals("关闭", close.contentDescription.toString())
        close.performClick()
        assertEquals(LyricsOverlayService.ACTION_STOP, shadowOf(service.application).nextStartedService.action)
        // An older unlock PendingIntent remains an unlock, rather than toggling the state.
        staleUnlock.performClick()
        service.onStartCommand(shadowOf(service.application).nextStartedService, 0, 3)
        assertFalse(service.isPositionLocked())

        manager.cancelAll()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        service.onStartCommand(Intent(service, LyricsOverlayService::class.java)
            .setAction(LyricsOverlayService.ACTION_REFRESH_NOTIFICATION), 0, 4)
        assertEquals("锁定", controls(shadowOf(manager).allNotifications.single())
            .findViewById<View>(R.id.notification_lyrics_lock).contentDescription.toString())
        assertFalse(service.isPositionLocked())
        assertEquals(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, params.flags)
    }
}
