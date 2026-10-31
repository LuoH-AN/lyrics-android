package com.luoh.music.lrc

import android.service.quicksettings.Tile
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.Before
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LyricsTileServiceTest {
    @Before fun grantTheSignaturePermissionUsedByAndroidXOnOlderAndroid() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions("${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        Settings.Secure.putString(application.contentResolver, "enabled_notification_listeners", "")
        ShadowSettings.setCanDrawOverlays(false)
    }

    @After fun resetRunningState() {
        ReflectionHelpers.setStaticField(LyricsOverlayService::class.java, "isRunning", false)
    }

    @Test fun authorizedClickStartsTheForegroundServiceAndStateFollowsItsBroadcast() {
        val service = Robolectric.buildService(LyricsTileService::class.java).create().get()
        Settings.Secure.putString(service.contentResolver, "enabled_notification_listeners",
            ComponentName(service, MediaListenerService::class.java).flattenToString())
        ShadowSettings.setCanDrawOverlays(true)
        service.onStartListening()
        assertEquals(R.drawable.ic_quick_settings, service.qsTile.icon.resId)
        service.onClick()
        val started = shadowOf(service.application).nextStartedService
        assertEquals(LyricsOverlayService::class.java.name, started.component!!.className)
        assertEquals(LyricsOverlayService.ACTION_START, started.action)
        assertEquals(Tile.STATE_INACTIVE, service.qsTile.state)
        ReflectionHelpers.setStaticField(LyricsOverlayService::class.java, "isRunning", true)
        service.sendBroadcast(Intent(LyricsOverlayService.ACTION_STATE_CHANGED).setPackage(service.packageName))
        ShadowLooper.idleMainLooper()
        assertEquals(Tile.STATE_ACTIVE, service.qsTile.state)
        assertEquals(R.drawable.ic_quick_settings, service.qsTile.icon.resId)
        ReflectionHelpers.setStaticField(LyricsOverlayService::class.java, "isRunning", false)
        service.sendBroadcast(Intent(LyricsOverlayService.ACTION_STATE_CHANGED).setPackage(service.packageName))
        ShadowLooper.idleMainLooper()
        assertEquals(Tile.STATE_INACTIVE, service.qsTile.state)
        service.onStopListening()
    }
    @Test fun missingPermissionsOpenTheAccessPageWithoutStartingAnOverlay() {
        val service = Robolectric.buildService(LyricsTileService::class.java).create().get()
        service.onStartListening()
        assertEquals(Tile.STATE_INACTIVE, service.qsTile.state)
        shadowOf(service).setLocked(true)
        service.onClick()
        val activity = shadowOf(service.application).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, activity.component!!.className)
        assertEquals("ACCESS", activity.getStringExtra("settings_section"))
        assertNull(shadowOf(service.application).nextStartedService)
        assertFalse(service.isLocked)
        service.onStopListening()
        // Reopening the panel re-registers exactly once and reads the current service state.
        service.onStartListening()
        assertEquals(Tile.STATE_INACTIVE, service.qsTile.state)
        service.onStopListening()
    }
}
