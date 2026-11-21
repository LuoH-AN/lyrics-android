package com.luoh.music.lrc

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class ComposeOverlayControlsTest {
    @get:Rule val compose = createComposeRule()
    private val application get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = application.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun prepareLockedOverlayWithoutNotifications() {
        prefs.edit().clear().putBoolean(LyricsOverlayService.PREF_POSITION_LOCKED, true).commit()
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReflectionHelpers.setStaticField(LyricsOverlayService::class.java, "isRunning", true)
    }

    @After fun resetServiceState() {
        ReflectionHelpers.setStaticField(LyricsOverlayService::class.java, "isRunning", false)
    }

    private fun show(category: String = "overlay") {
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { Box(Modifier.size(400.dp, 700.dp)) { SettingsCategoryContent(category, {}, {}) } }
            }
        }
    }

    @Test fun lockedLyricsCanBeUnlockedFromTheTopOfAppearanceSettingsWithoutNotifications() {
        show()
        compose.onNodeWithText("解锁并移动歌词").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(LyricsOverlayService.ACTION_UNLOCK, shadowOf(application).nextStartedService.action) }
    }

    @Test fun notificationRowOpensTheSendingPermissionRatherThanTheListenerPermission() {
        show()
        compose.onNodeWithText("桌面歌词通知").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle {
            val intent = shadowOf(application).nextStartedActivity
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
            assertEquals(application.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        }
    }

    @Test fun savedLockCanBeClearedEvenWhenTheServiceHasStopped() {
        ReflectionHelpers.setStaticField(LyricsOverlayService::class.java, "isRunning", false)
        show()
        compose.onNodeWithText("解锁并移动歌词").performTouchInput { click() }
        compose.runOnIdle {
            assertFalse(prefs.getBoolean(LyricsOverlayService.PREF_POSITION_LOCKED, true))
            assertNull(shadowOf(application).nextStartedService)
        }
        compose.onNodeWithText("锁定并点击穿透").assertIsNotEnabled()
    }

    @Test fun theSameUnlockControlRemainsAvailableInAccessSettings() {
        show("access")
        compose.onNodeWithText("解锁并移动歌词").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(LyricsOverlayService.ACTION_UNLOCK, shadowOf(application).nextStartedService.action) }
    }
}
