package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.ui.agora.AgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class ComposeOverlayAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun clearPreferences() {
        prefs.edit().clear().commit()
    }

    private fun showSettings() {
        compose.setContent {
            CompositionLocalProvider(LocalAgoraMotionPolicy provides AgoraMotionPolicy(reduceMotion = true)) {
                MaterialTheme { Box(Modifier.size(400.dp, 700.dp)) { SettingsCategoryContent("overlay", {}, {}) } }
            }
        }
    }

    private fun clickRow(text: String) = compose.onNodeWithText(text).performScrollTo().performTouchInput { click() }

    @Test fun alignmentGradientAndBackgroundChoicesPersistInTheActualSettings() {
        showSettings()
        clickRow("对齐方式")
        compose.onNodeWithText("居中").performTouchInput { click() }
        compose.runOnIdle { assertEquals("center", prefs.getString(OverlayAppearance.PREF_ALIGNMENT, null)) }
        clickRow("对齐方式")
        compose.onNodeWithText("右对齐").performTouchInput { click() }
        clickRow("渐变歌词")
        compose.onNodeWithText("渐变歌词").assertIsOn()
        clickRow("渐变方向")
        compose.onNodeWithText("纵向").performTouchInput { click() }
        clickRow("背景")
        compose.onNodeWithText("半透明").performTouchInput { click() }
        compose.onNodeWithContentDescription("半透明强度").performScrollTo().performTouchInput {
            swipe(Offset(width * .4f, centerY), Offset(width * .8f, centerY), 300)
        }
        compose.runOnIdle {
            assertEquals("right", prefs.getString(OverlayAppearance.PREF_ALIGNMENT, null))
            assertTrue(prefs.getBoolean(OverlayAppearance.PREF_GRADIENT, false))
            assertTrue(prefs.getBoolean(OverlayAppearance.PREF_GRADIENT_VERTICAL, false))
            assertEquals("low", prefs.getString(LyricsOverlayService.PREF_BACKGROUND_MODE_COMPACT, null))
            assertTrue(prefs.getInt(OverlayAppearance.PREF_BACKGROUND_OPACITY, 0) > 55)
        }
    }

    @Test fun wheelPicksNonPresetColorsAndCancelDoesNotApply() {
        var applied: String? = null
        var dismissed = false
        var picked = ""
        compose.setContent { MaterialTheme { SettingsColorDialog("自由选色", "#FFFFFF", { dismissed = true }, { applied = it }) } }
        compose.onNodeWithTag("color_wheel").performTouchInput {
            picked = PickerColor(0f, 0f, 1f).atPoint(35f, 30f, minOf(width, height) / 2f - 12f).hex()
            swipe(Offset(centerX + 45f, centerY), Offset(centerX + 35f, centerY + 30f), 300)
        }
        compose.onNodeWithContentDescription("当前颜色 $picked").assertExists()
        compose.onNodeWithText("取消").performTouchInput { click() }
        compose.runOnIdle { assertTrue(dismissed); assertNull(applied) }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun colorWheelDialogHasAVisibleDraggablePalette() {
        compose.setContent { MaterialTheme { SettingsColorDialog("歌词颜色", "#7B5FDC", {}, {}) } }
        compose.onNodeWithTag("color_wheel").performTouchInput { click(Offset(centerX + 35f, centerY + 30f)) }
        compose.runOnIdle {
            val root = requireNotNull(ShadowDialog.getLatestDialog().window).decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val file = File("build/reports/material-ui/overlay-color-wheel.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun invalidHexCannotApplyAndArbitraryValidColorIsSaved() {
        var applied: String? = null
        compose.setContent { MaterialTheme { SettingsColorDialog("自由选色", "#FFFFFF", {}, { applied = it }) } }
        compose.onNodeWithText("十六进制颜色").performScrollTo().performTextReplacement("#GG0000")
        compose.onNodeWithText("应用").assertIsNotEnabled()
        compose.onNodeWithText("十六进制颜色").performTextReplacement("#123abc")
        compose.onNodeWithText("应用").performTouchInput { click() }
        compose.runOnIdle { assertEquals("#123ABC", applied) }
    }

    @Test @Config(sdk = [26], qualifiers = "w320dp-h480dp-mdpi")
    fun colorPickerCanScrollAndApplyOnSmallLegacyScreens() = verifyScrollablePicker()

    @Test @Config(sdk = [35], qualifiers = "w640dp-h360dp-mdpi")
    fun colorPickerCanScrollAndApplyInLandscape() = verifyScrollablePicker()

    private fun verifyScrollablePicker() {
        var applied: String? = null
        compose.setContent { MaterialTheme { SettingsColorDialog("歌词颜色", "#FFFFFF", {}, { applied = it }) } }
        compose.onNodeWithTag("color_wheel").assertIsDisplayed()
        compose.onNodeWithText("应用").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText("十六进制颜色").performScrollTo().performTextReplacement("#A72EC3")
        compose.onNodeWithText("应用").performTouchInput { click() }
        compose.runOnIdle { assertEquals("#A72EC3", applied) }
    }

    @Test fun wheelAppliedColorAndGradientEndpointAreSavedIndependently() {
        prefs.edit().putBoolean(OverlayAppearance.PREF_GRADIENT, true).commit()
        showSettings()
        clickRow("歌词颜色")
        compose.onNodeWithTag("color_wheel").performTouchInput { click(Offset(width - 12f, centerY)) }
        compose.onNodeWithText("应用").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("#FF0000", prefs.getString(LyricsOverlayService.PREF_LYRIC_COLOR_COMPACT, null))
        }
        clickRow("渐变终点颜色")
        compose.onNodeWithText("十六进制颜色").performScrollTo().performTextReplacement("#03FE81")
        compose.onNodeWithText("应用").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("#FF0000", prefs.getString(LyricsOverlayService.PREF_LYRIC_COLOR_COMPACT, null))
            assertEquals("#03FE81", prefs.getString(OverlayAppearance.PREF_GRADIENT_END, null))
        }
    }
}
