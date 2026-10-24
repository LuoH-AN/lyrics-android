package com.luoh.music.lrc.ui.agora

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class ComposeScrollRetentionTest {
    @get:Rule val compose = createComposeRule()
    private val retention = SettingsScrollRetention()
    private lateinit var scope: CoroutineScope

    @Composable private fun Host(content: @Composable () -> Unit) {
        scope = rememberCoroutineScope()
        CompositionLocalProvider(LocalSettingsScrollRetention provides retention,
            LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
            MaterialTheme { Box(Modifier.size(400.dp, 600.dp)) { content() } }
        }
    }

    @Composable private fun ParentPage() {
        CollapsingSettingsScaffold(title = "Parent", onBack = {}) {
            repeat(40) { Text("Row $it", modifier = Modifier.height(48.dp)) }
        }
    }

    @Test fun backRestoresScrollAndTitleWhileEditorDraftIsDiscarded() {
        var route by mutableStateOf("parent")
        compose.setContent {
            Host {
                GuardedAnimatedContent(route, forward = route == "editor") { page ->
                    if (page == "parent") ParentPage() else {
                        var draft by rememberSaveable { mutableStateOf("saved") }
                        OutlinedTextField(draft, { draft = it }, modifier = Modifier.testTag("draft"))
                    }
                }
            }
        }
        compose.runOnIdle { scope.launch { retention.scrollState(listOf("parent"))!!.scrollTo(37) } }
        val titleTop = compose.onNodeWithText("Parent").getUnclippedBoundsInRoot().top
        compose.runOnIdle { route = "editor" }
        compose.onNodeWithTag("draft").performTextReplacement("discarded")
        compose.runOnIdle { route = "parent" }
        compose.runOnIdle { assertEquals(37, retention.scrollState(listOf("parent"))!!.value) }
        assertEquals(titleTop, compose.onNodeWithText("Parent").getUnclippedBoundsInRoot().top)
        compose.runOnIdle { route = "editor" }
        compose.onNodeWithTag("draft").assertTextContains("saved")
    }

    @Test fun returningToLazyPageRestoresVisibleItemAndOffset() {
        var route by mutableStateOf("list")
        compose.setContent {
            Host {
                GuardedAnimatedContent(route, forward = route == "child") { page ->
                    if (page == "list") {
                        CollapsingSettingsLazyScaffold(title = "Library", onBack = {}) {
                            items(50, key = { "row:$it" }) { Text("Row $it", modifier = Modifier.height(48.dp)) }
                        }
                    } else Text("Child")
                }
            }
        }
        compose.runOnIdle { scope.launch { retention.lazyListState(listOf("list"))!!.scrollToItem(14, 23) } }
        val titleTop = compose.onNodeWithText("Library").getUnclippedBoundsInRoot().top
        compose.runOnIdle { route = "child" }
        compose.runOnIdle { route = "list" }
        compose.runOnIdle {
            val state = retention.lazyListState(listOf("list"))!!
            assertEquals(14, state.firstVisibleItemIndex)
            assertEquals(23, state.firstVisibleItemScrollOffset)
        }
        assertEquals(titleTop, compose.onNodeWithText("Library").getUnclippedBoundsInRoot().top)
    }

    @Test fun rapidBackCreatesANewScrollOwnerAndKeepsTheReturningPagesLatestPosition() {
        var route by mutableStateOf("parent")
        compose.setContent {
            Host {
                GuardedAnimatedContent(route, forward = route == "child") { page ->
                    if (page == "parent") ParentPage() else Text("Child")
                }
            }
        }
        compose.runOnIdle { scope.launch { retention.scrollState(listOf("parent"))!!.scrollTo(420) } }
        lateinit var original: ScrollState
        compose.runOnIdle { original = retention.scrollState(listOf("parent"))!! }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { route = "child" }
        advanceTransition()
        compose.onNodeWithText("Parent").assertExists()
        compose.onNodeWithText("Child").assertExists()
        compose.runOnIdle { route = "parent" }
        advanceTransition()
        compose.runOnIdle {
            val returning = retention.scrollState(listOf("parent"))!!
            assertNotSame(original, returning)
            assertEquals(420, returning.value)
            scope.launch { returning.scrollTo(210) }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { route = "child" }
        compose.runOnIdle { route = "parent" }
        compose.runOnIdle { assertEquals(210, retention.scrollState(listOf("parent"))!!.value) }
    }

    private fun advanceTransition() {
        repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
    }
}
