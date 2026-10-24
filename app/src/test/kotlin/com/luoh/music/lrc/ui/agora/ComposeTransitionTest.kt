package com.luoh.music.lrc.ui.agora

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class ComposeTransitionTest {
    @get:Rule val compose = createComposeRule()

    private fun advanceTransition(milliseconds: Long = 64) {
        // Target slots are created by an effect and measured by BoxWithConstraints.
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
    }

    @Test fun interruptedNavigationAcceptsOnlyTheLatestPageTouches() {
        var route by mutableStateOf("A")
        val clicks = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme {
                    Box(Modifier.size(400.dp, 600.dp)) {
                        GuardedAnimatedContent(route, forward = true) { page ->
                            Box(Modifier.size(400.dp, 600.dp)) {
                                Button(onClick = { clicks += page },
                                    modifier = Modifier.testTag("action:$page").align(
                                        if (page == "A") Alignment.BottomCenter else Alignment.TopCenter)) {
                                    Text(page)
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { route = "B" }
        advanceTransition()
        compose.onNodeWithTag("action:A").assertExists()
        compose.onNodeWithTag("action:A").performTouchInput { click() }
        compose.onNodeWithTag("action:B").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("B"), clicks) }

        compose.runOnIdle { route = "C" }
        advanceTransition()
        compose.onNodeWithTag("action:A").assertDoesNotExist()
        compose.onNodeWithTag("action:B").assertExists()
        compose.onNodeWithTag("action:C").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("B", "C"), clicks) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("action:B").assertDoesNotExist()
        compose.onNodeWithTag("action:C").assertExists()
    }

    @Test fun reducedMotionCrossfadesWithoutMovingTheIncomingPage() {
        var route by mutableStateOf("A")
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme {
                    Box(Modifier.size(400.dp, 600.dp)) {
                        GuardedAnimatedContent(route, forward = true) { page ->
                            Button(onClick = {}, modifier = Modifier.testTag(page)) { Text(page) }
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { route = "B" }
        advanceTransition()
        val initialBounds = compose.onNodeWithTag("B").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("A").assertExists()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertEquals(initialBounds, compose.onNodeWithTag("B").getUnclippedBoundsInRoot())
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("A").assertDoesNotExist()
        assertEquals(initialBounds, compose.onNodeWithTag("B").getUnclippedBoundsInRoot())
    }

    @Test fun closingOverlayBlocksTouchesAndReopeningCancelsTheOldExit() {
        var visible by mutableStateOf(true)
        var policy by mutableStateOf(AgoraMotionPolicy(reduceMotion = true))
        var enters = 0
        var exits = 0
        var clicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides policy) {
                MaterialTheme {
                    Box(Modifier.size(400.dp, 600.dp)) {
                        SettingsOverlayHost(visible, onDismiss = {},
                            onEnterFinished = { enters++ }, onExitFinished = { exits++ }) {
                            Button(onClick = { clicks++ }, modifier = Modifier.testTag("overlay-action")) { Text("Action") }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(1, enters) }
        val settledBounds = compose.onNodeWithTag("overlay-action").getUnclippedBoundsInRoot()
        compose.runOnIdle { policy = AgoraMotionPolicy(reduceMotion = false) }
        compose.runOnIdle { assertEquals(1, enters) }
        assertEquals(settledBounds, compose.onNodeWithTag("overlay-action").getUnclippedBoundsInRoot())
        compose.runOnIdle { policy = AgoraMotionPolicy(reduceMotion = true) }

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { visible = false }
        advanceTransition()
        compose.onNodeWithTag("overlay-action").assertExists()
        compose.onNodeWithTag("overlay-action").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clicks); assertEquals(0, exits) }
        compose.runOnIdle { visible = true }
        advanceTransition()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, enters); assertEquals(0, exits) }
        compose.onNodeWithTag("overlay-action").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.runOnIdle { visible = false }
        compose.onNodeWithTag("overlay-action").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, exits) }
    }
}
