package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class ComposeTranslationScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("supplement_translation", Context.MODE_PRIVATE)

    @Before fun configure() {
        prefs.edit().clear().putString("mode", "off").putString("active_api_profile", "glm")
            .putString(TranslationApiProfiles.endpointKey("glm"), "https://example.test/v1")
            .putString(TranslationApiProfiles.modelKey("glm"), "saved-model").commit()
    }

    private fun show(): ComposeNavigator {
        val navigator = ComposeNavigator(initial = ComposeRoute("translation"))
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { Box(Modifier.size(400.dp, 700.dp)) { navigator.Content() } }
            }
        }
        return navigator
    }

    private fun selectMode(value: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("补充翻译方式"))
        compose.onNodeWithText("补充翻译方式").performTouchInput { click() }
        compose.onNodeWithText("选择补充翻译方式").assertExists()
        compose.onNodeWithTag("translation-mode-$value").performTouchInput { click() }
        compose.onNodeWithText("选择补充翻译方式").assertDoesNotExist()
    }

    @Test fun modeDialogShowsInlineConfigurationAndKeepsDraftsAcrossChoices() {
        val navigator = show()
        compose.onNodeWithText("HTTPS 服务地址").assertDoesNotExist()
        selectMode("api")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("saved-model").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("https://example.test/v1").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("API Key"))
        compose.onNodeWithText("API Key").performTextReplacement("draft-key-test")
        selectMode("off")
        compose.onNodeWithText("HTTPS 服务地址").assertDoesNotExist()
        selectMode("api")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("API Key"))
        compose.onNodeWithText("draft-key-test").assertExists()
        selectMode("offline")
        compose.onNodeWithText("离线语言包 · 需要代理").assertExists()
        compose.onNodeWithText("HTTPS 服务地址").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("offline", prefs.getString("mode", null))
            assertEquals(ComposeRoute("translation"), navigator.route)
            assertEquals("https://example.test/v1", prefs.getString(TranslationApiProfiles.endpointKey("glm"), null))
        }
    }

    @Test fun cancellingModeDialogLeavesSelectionAndConfigurationUnchanged() {
        val navigator = show()
        compose.onNodeWithText("补充翻译方式").performTouchInput { click() }
        compose.onNodeWithText("取消").performTouchInput { click() }
        compose.onNodeWithText("选择补充翻译方式").assertDoesNotExist()
        compose.onNodeWithText("HTTPS 服务地址").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("off", prefs.getString("mode", null))
            assertEquals(ComposeRoute("translation"), navigator.route)
        }
    }
}
