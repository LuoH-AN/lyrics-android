package com.luoh.music.lrc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.ColorSchemePreset
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.SchemeStyle
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Documentation images rendered by the real screens, with local sample data and no network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadmeScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun resetPreferences() {
        for (name in listOf(ThemePrefs.PREFS, HomeLyricsPrefs.PREFS, HomeMotionPrefs.PREFS,
            LyricsOverlayService.PREFS_NAME, "supplement_translation")) {
            compose.activity.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        compose.activity.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE).edit()
            .putString(ThemePrefs.KEY, ThemePrefs.LIGHT).commit()
    }

    private fun save(name: String, content: @Composable () -> Unit) {
        lateinit var root: View
        compose.setContent {
            val view = LocalView.current
            SideEffect { root = view }
            LyricsComposeTheme {
                CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(root.width > 0 && root.height > 0)
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val output = File("build/reports/material-ui/readme/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun sampleCover(): Bitmap {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 256f, 256f,
            intArrayOf(0xFF27375B.toInt(), 0xFF786B88.toInt(), 0xFFE6AD87.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 256f, 256f, paint)
        paint.shader = null
        paint.color = 0xFFFBE3BE.toInt()
        canvas.drawCircle(172f, 88f, 31f, paint)
        paint.color = 0xFF253C4B.toInt()
        canvas.drawOval(-95f, 168f, 355f, 365f, paint)
        paint.color = 0xFF172A3C.toInt()
        canvas.drawOval(-80f, 205f, 420f, 380f, paint)
        return bitmap
    }

    @Test fun homeWithAlbumBackgroundAndBilingualLyrics() {
        HomeLyricsPrefs.saveAppearance(compose.activity,
            HomeLyricAppearance(fontSize = 25, translationSize = 15, lineSpacing = 10))
        val state = HomePresentationState().apply {
            setSnapshot(HomePresentationState.Snapshot(track = "晚风与回声", artist = "夜间电台",
                cover = sampleCover(), positionMs = 62_300, durationMs = 180_000,
                canPrevious = true, canNext = true))
            setLyrics(LyricDocument(listOf(
                LyricLine(0, "晚风走过长长的街", "The evening walks with us"),
                LyricLine(30_000, "霓虹在雨里慢慢熄灭", "Neon fades into the rain"),
                LyricLine(60_000, "让这首歌陪我久一点", "Let this song stay a little longer",
                    words = listOf(LyricWord(60_000, 1_500, "让这首歌"),
                        LyricWord(61_500, 1_500, "陪我"), LyricWord(63_000, 1_500, "久一点"))),
                LyricLine(90_000, "灯火在远处轻轻摇晃", "Lights are swaying far away"),
                LyricLine(120_000, "天亮之前再听一遍", "One more song before the dawn")), true))
            setActive(true)
        }
        save("home") { ComposeHomeScreen(state, {}, {}, {}) }
        compose.onNodeWithText("晚风与回声").assertIsDisplayed()
    }

    @Test fun desktopLyricsAppearanceAndPreview() {
        compose.activity.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(LyricsOverlayService.PREF_BACKGROUND_MODE_COMPACT, LyricsOverlayService.BACKGROUND_LOW)
            .putString(LyricsOverlayService.PREF_LYRIC_COLOR_COMPACT, "#FFFFFF")
            .putInt(LyricsOverlayService.PREF_CONTEXT_BEFORE, 1)
            .putInt(LyricsOverlayService.PREF_CONTEXT_AFTER, 1).commit()
        save("desktop-lyrics") { SettingsCategoryContent("overlay", {}, {}) }
        compose.onNodeWithText("歌词预览").assertIsDisplayed()
    }

    @Test fun appAppearanceWithDarkTheme() {
        compose.activity.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE).edit()
            .putString(ThemePrefs.KEY, ThemePrefs.DARK)
            .putString(ThemePrefs.KEY_COLOR_SCHEME, ColorSchemePreset.LAVENDER.name)
            .putString(ThemePrefs.KEY_SCHEME_STYLE, SchemeStyle.TONAL_SPOT.name)
            .putBoolean(ThemePrefs.KEY_AMOLED, true).commit()
        save("appearance") { SettingsCategoryContent("appearance", {}, {}) }
        compose.onNodeWithText("AMOLED").assertIsDisplayed()
    }

    @Test fun lyricsAndTranslationOptions() {
        save("lyrics-translation") { SettingsCategoryContent("lyrics", {}, {}) }
        compose.onNodeWithText("补充翻译").assertIsDisplayed()
        compose.onNodeWithText("自定义歌词").assertIsDisplayed()
    }
}
