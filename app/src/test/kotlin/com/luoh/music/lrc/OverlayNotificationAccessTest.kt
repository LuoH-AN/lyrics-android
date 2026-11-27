package com.luoh.music.lrc

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.overlay.OverlayNotificationAccess
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OverlayNotificationAccessTest {
    private val application get() = ApplicationProvider.getApplicationContext<Application>()
    private val manager get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before fun allowNotifications() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
    }

    @Test fun notificationListenerAccessDoesNotGrantPostingPermission() {
        Settings.Secure.putString(application.contentResolver, "enabled_notification_listeners", "${application.packageName}/.MediaListenerService")
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(OverlayNotificationAccess.needsPermission(application))
        assertFalse(OverlayNotificationAccess.isEnabled(application))
        val intent = OverlayNotificationAccess.settingsIntent(application)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(application.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test fun anAllowedAppWithABlockedLyricChannelOpensThatChannel() {
        manager.createNotificationChannel(NotificationChannel(LyricsOverlayService.CHANNEL_ID, "桌面歌词", NotificationManager.IMPORTANCE_NONE))
        assertFalse(OverlayNotificationAccess.isEnabled(application))
        val intent = OverlayNotificationAccess.settingsIntent(application)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(LyricsOverlayService.CHANNEL_ID, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test fun appWideBlockingStillOpensTheAppSettingsEvenWhenTheChannelIsBlocked() {
        manager.createNotificationChannel(NotificationChannel(LyricsOverlayService.CHANNEL_ID, "桌面歌词", NotificationManager.IMPORTANCE_NONE))
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(OverlayNotificationAccess.isEnabled(application))
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, OverlayNotificationAccess.settingsIntent(application).action)
    }

    @Test fun notificationsCanBeEnabledBeforeTheServiceCreatesItsChannel() {
        assertFalse(OverlayNotificationAccess.needsPermission(application))
        assertTrue(OverlayNotificationAccess.isEnabled(application))
    }

    @Test @Config(sdk = [28]) fun legacyAndroidDoesNotRequestTheNewRuntimePermission() {
        assertFalse(OverlayNotificationAccess.needsPermission(application))
        assertTrue(OverlayNotificationAccess.isEnabled(application))
    }
}
