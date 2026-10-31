package com.luoh.music.lrc

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class LyricShareSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longPressOpensAFrozenSelection() {
        val home = HomePresentationState().apply {
            setSnapshot(HomePresentationState.Snapshot(track = "First Song", artist = "Singer"))
            setLyrics(LyricDocument(listOf(LyricLine(0, "君")), true))
        }
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { ComposeHomeScreen(home, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("君").performTouchInput { longClick() }
        compose.onNodeWithText("歌词摘句").assertIsDisplayed()
        compose.runOnIdle { home.setSnapshot(HomePresentationState.Snapshot(track = "Next Song")) }
        compose.onNodeWithText("First Song").assertIsDisplayed()
        assertEquals("君", requireNotNull(home.shareDraft).lines.single().text)
    }

    @Test fun pickLinesAndCopyInSongOrderWithOptionalTranslation() {
        val draft = LyricShareDraft("Song", "Artist", null,
            listOf(LyricLine(0, "first", "第一句"), LyricLine(1000, "second", "第二句")), 1)
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { LyricShareSheet(draft) {} }
            }
        }
        compose.onNodeWithTag("lyric_share_lines").performScrollToNode(hasText("first"))
        compose.onNodeWithText("first").performClick()
        compose.onNodeWithText("已选 2 / 6 句 · 按歌曲顺序排列").assertIsDisplayed()
        compose.onNodeWithText("复制歌词").performClick()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        compose.runOnIdle { assertEquals("first\n第一句\n\nsecond\n第二句", clipboard.primaryClip!!.getItemAt(0).text.toString()) }
        compose.onNodeWithText("附带译文").performClick()
        compose.onNodeWithText("复制歌词").performClick()
        compose.runOnIdle { assertEquals("first\n\nsecond", clipboard.primaryClip!!.getItemAt(0).text.toString()) }
    }
}
