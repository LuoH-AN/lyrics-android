package com.luoh.music.lrc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.ColorSchemePreset
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.SchemeStyle
import com.luoh.music.lrc.ui.agora.colorSchemeForPreset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeAppearanceRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(dark: Boolean) {
        compose.activity.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val cover = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val tones = intArrayOf(0xFFD36544.toInt(), 0xFF7B568F.toInt(), 0xFFDFB269.toInt(), 0xFF416C82.toInt())
        for (y in 0 until 48) for (x in 0 until 48) cover.setPixel(x, y, tones[(x / 24) + (y / 24) * 2])
        val state = HomePresentationState().apply {
            setSnapshot(HomePresentationState.Snapshot(track = "晚风与回声", artist = "测试专辑", cover = cover,
                positionMs = 60_000, durationMs = 180_000, canPrevious = true, canNext = true))
            setLyrics(LyricDocument(listOf(
                LyricLine(0, "沿着晚风的方向"), LyricLine(30_000, "慢慢走过这一条街"),
                LyricLine(60_000, "让音乐陪你到天亮"), LyricLine(90_000, "灯光在远处轻轻摇晃"),
                LyricLine(120_000, "每一句都有你的回声")), true))
            setActive(true)
        }
        lateinit var root: View
        compose.setContent {
            val view = LocalView.current
            SideEffect { root = view }
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme(colorScheme = colorSchemeForPreset(ColorSchemePreset.OCEAN, SchemeStyle.NEUTRAL, dark)) {
                    Box(Modifier.size(400.dp, 800.dp)) { ComposeHomeScreen(state, {}, {}, {}) }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
        val suffix = if (dark) "dark" else "light"
        fun save(name: String) {
            compose.runOnIdle {
                assertTrue(root.width > 0 && root.height > 0)
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val output = File("build/reports/material-ui/$name.png")
                output.parentFile.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        save("home-cover-expanded-$suffix")
        compose.onNodeWithContentDescription("收起播放控制栏").performTouchInput { click() }
        compose.onNodeWithTag("home_cover_progress").assertExists()
        save("home-cover-compact-$suffix")
        compose.runOnIdle { HomeLyricsPrefs.saveAlbumBackground(compose.activity, false) }
        compose.onNodeWithTag("home_album_background").assertDoesNotExist()
        save("home-background-off-$suffix")
    }

    @Test fun lightHomeRendersExpandedCompactAndDisabledBackground() = render(false)
    @Test fun darkHomeRendersExpandedCompactAndDisabledBackground() = render(true)
}
