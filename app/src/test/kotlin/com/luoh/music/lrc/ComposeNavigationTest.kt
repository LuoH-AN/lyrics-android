package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.SettingsOverlayHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class ComposeNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Before fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun show(navigator: ComposeNavigator, width: Int = 400) {
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme {
                    Box(Modifier.size(width.dp, 700.dp)) {
                        SettingsOverlayHost(navigator.isOpen, navigator::dismiss,
                            onExitFinished = navigator::onExitFinished) { navigator.Content() }
                    }
                }
            }
        }
    }

    @Test fun phoneCategoryNavigationShowsAndPersistsTheActualAppearanceControls() {
        val navigator = ComposeNavigator(initial = ComposeRoute("settings"))
        show(navigator)
        compose.onNodeWithText("应用外观").performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(ComposeRoute("category", category = "appearance"), navigator.route) }
        compose.onNodeWithText("主题与颜色").assertExists()
        compose.onNodeWithText("主题", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithText("深色").performTouchInput { click() }
        compose.runOnIdle {
            val context = ApplicationProvider.getApplicationContext<Context>()
            assertEquals(ThemePrefs.DARK, context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE).getString(ThemePrefs.KEY, null))
        }
        compose.onNodeWithContentDescription("返回").performTouchInput { click() }
        compose.runOnIdle { assertEquals("settings", navigator.route.kind); assertTrue(navigator.isOpen) }
        compose.onNodeWithText("应用外观").assertExists()
    }

    @Test @Config(qualifiers = "w1200dp-h800dp-mdpi")
    fun wideCategorySelectionChildBackAndOuterDismissShareOneNavigationSession() {
        val navigator = ComposeNavigator(initial = ComposeRoute("settings"))
        show(navigator, width = 1200)
        compose.runOnIdle { assertTrue(navigator.wide) }
        compose.onNodeWithText("应用外观").performTouchInput { click() }
        compose.runOnIdle { assertEquals("appearance", navigator.selectedCategory) }
        compose.onNodeWithText("主题与颜色").assertExists()
        compose.onNodeWithText("歌词与翻译").performTouchInput { click() }
        compose.runOnIdle { assertEquals("lyrics", navigator.selectedCategory) }
        compose.onNodeWithText("翻译显示", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("自定义歌词").performTouchInput { click() }
        compose.runOnIdle { assertEquals("custom", navigator.route.kind) }
        compose.onNodeWithText("新建自定义歌词", useUnmergedTree = true).assertExists()
        // The category rail owns the first back button; the child pane owns the second.
        compose.onAllNodesWithContentDescription("返回")[1].performTouchInput { click() }
        compose.runOnIdle { assertEquals("settings", navigator.route.kind); assertEquals("lyrics", navigator.selectedCategory) }
        compose.onNodeWithText("翻译显示", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("返回").performTouchInput { click() }
        compose.runOnIdle { assertFalse(navigator.isOpen) }
        compose.onNodeWithText("翻译显示", useUnmergedTree = true).assertDoesNotExist()
    }
}
