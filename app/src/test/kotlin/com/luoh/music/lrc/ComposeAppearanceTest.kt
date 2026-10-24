package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.luoh.music.lrc.ui.agora.ColorSchemePreset
import com.luoh.music.lrc.ui.agora.SchemeStyle
import com.luoh.music.lrc.ui.agora.colorSchemeForPreset
import com.luoh.music.lrc.ui.agora.colorSchemeForSeed
import com.luoh.music.lrc.ui.agora.withAmoledBackground
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
class ComposeAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE)
    private lateinit var currentScheme: ColorScheme

    @Before fun clearPreferences() {
        prefs.edit().clear().putBoolean("reduce_motion", true).commit()
    }

    private fun assertScheme(expected: ColorScheme) {
        compose.runOnIdle {
            assertEquals(expected.primary, currentScheme.primary)
            assertEquals(expected.tertiary, currentScheme.tertiary)
            assertEquals(expected.background, currentScheme.background)
            assertEquals(expected.surface, currentScheme.surface)
            assertEquals(expected.surfaceContainer, currentScheme.surfaceContainer)
        }
    }

    private fun showSettings(): ComposeNavigator {
        val navigator = ComposeNavigator(initial = ComposeRoute("settings")).apply {
            navigate(ComposeRoute("category", category = "appearance"))
        }
        compose.setContent {
            LyricsComposeTheme {
                val scheme = MaterialTheme.colorScheme
                SideEffect { currentScheme = scheme }
                Box(Modifier.size(400.dp, 700.dp)) { navigator.Content() }
            }
        }
        return navigator
    }

    private fun clickRow(title: String) {
        compose.onNodeWithText(title).performScrollTo().performTouchInput { click() }
    }

    private fun select(title: String, choice: String) {
        clickRow(title)
        compose.onNodeWithText(choice).performTouchInput { click() }
    }

    @Test fun controlsPersistAndUpdateTheRootThemeWithoutReopeningThePage() {
        prefs.edit().putString(ThemePrefs.KEY, ThemePrefs.LIGHT).commit()
        val navigator = showSettings()
        assertScheme(colorSchemeForPreset(ColorSchemePreset.OCEAN, SchemeStyle.NEUTRAL))
        compose.onNodeWithText("AMOLED").assertIsOff()
        clickRow("AMOLED")
        compose.onNodeWithText("AMOLED").assertIsOn()
        assertScheme(colorSchemeForPreset(ColorSchemePreset.OCEAN, SchemeStyle.NEUTRAL)
            .withAmoledBackground(false, true))
        select("主题", "深色")
        assertScheme(colorSchemeForPreset(ColorSchemePreset.OCEAN, SchemeStyle.NEUTRAL, true)
            .withAmoledBackground(true, true))
        select("配色方案", "日落")
        select("方案风格", "鲜艳")
        assertScheme(colorSchemeForPreset(ColorSchemePreset.SUNSET, SchemeStyle.VIBRANT, true)
            .withAmoledBackground(true, true))
        compose.runOnIdle {
            assertTrue(prefs.getBoolean(ThemePrefs.KEY_AMOLED, false))
            assertEquals(ColorSchemePreset.SUNSET.name, prefs.getString(ThemePrefs.KEY_COLOR_SCHEME, null))
            assertEquals(SchemeStyle.VIBRANT.name, prefs.getString(ThemePrefs.KEY_SCHEME_STYLE, null))
        }
        compose.onNodeWithContentDescription("返回").performTouchInput { click() }
        compose.onNodeWithText("应用外观").performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("AMOLED").assertIsOn()
        compose.runOnIdle { assertEquals("appearance", navigator.route.category) }
    }

    @Test fun pickingPresetClearsCustomSeedAndCustomColorsKeepStyleAndAmoled() {
        prefs.edit().putString(ThemePrefs.KEY, ThemePrefs.LIGHT)
            .putBoolean(ThemePrefs.KEY_AMOLED, true).commit()
        AccentPrefs.set(context, "#AD1457")
        showSettings()
        select("方案风格", "表现力")
        assertScheme(colorSchemeForSeed(android.graphics.Color.parseColor("#AD1457"),
            style = SchemeStyle.EXPRESSIVE).withAmoledBackground(false, true))
        select("配色方案", "森林")
        compose.runOnIdle { assertFalse(prefs.contains(AccentPrefs.KEY)) }
        assertScheme(colorSchemeForPreset(ColorSchemePreset.FOREST, SchemeStyle.EXPRESSIVE)
            .withAmoledBackground(false, true))
    }

    @Test fun dynamicColorsOverrideCustomSeedAndDisablingRestoresIt() {
        prefs.edit().putString(ThemePrefs.KEY, ThemePrefs.LIGHT)
            .putBoolean(ThemePrefs.KEY_AMOLED, true).commit()
        AccentPrefs.set(context, "#AD1457")
        showSettings()
        clickRow("动态颜色")
        assertScheme(dynamicLightColorScheme(context).withAmoledBackground(false, true))
        compose.onNodeWithText("配色方案").assertIsNotEnabled()
        compose.onNodeWithText("方案风格").assertIsNotEnabled()
        compose.onNodeWithText("主调颜色").assertIsNotEnabled()
        select("主题", "深色")
        assertScheme(dynamicDarkColorScheme(context).withAmoledBackground(true, true))
        clickRow("动态颜色")
        assertScheme(colorSchemeForSeed(android.graphics.Color.parseColor("#AD1457"), true,
            SchemeStyle.NEUTRAL).withAmoledBackground(true, true))
        compose.runOnIdle { assertEquals("#AD1457", prefs.getString(AccentPrefs.KEY, null)) }
    }

    @Test fun monochromeStyleCanBeSelectedAndUpdatesAllAccentRoles() {
        prefs.edit().putString(ThemePrefs.KEY, ThemePrefs.LIGHT).commit()
        showSettings()
        select("方案风格", "黑白灰")
        assertScheme(colorSchemeForPreset(ColorSchemePreset.OCEAN, SchemeStyle.MONOCHROME))
        compose.runOnIdle { assertEquals(SchemeStyle.MONOCHROME.name, prefs.getString(ThemePrefs.KEY_SCHEME_STYLE, null)) }
    }

    @Test @Config(sdk = [28])
    fun olderAndroidDisablesDynamicColorsAndRestoredPreferenceFallsBackToPreset() {
        prefs.edit().putString(ThemePrefs.KEY, ThemePrefs.LIGHT)
            .putBoolean(ThemePrefs.KEY_DYNAMIC_COLOR, true).commit()
        showSettings()
        compose.onNodeWithText("动态颜色").assertIsNotEnabled()
        assertScheme(colorSchemeForPreset(ColorSchemePreset.OCEAN, SchemeStyle.NEUTRAL))
        select("配色方案", "玫瑰")
        assertScheme(colorSchemeForPreset(ColorSchemePreset.ROSE, SchemeStyle.NEUTRAL))
    }

    @Test fun followSystemReactsToNightModeAndExplicitThemeOverridesIt() {
        val configuration = mutableStateOf(Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        })
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration.value) {
                LyricsComposeTheme {
                    val scheme = MaterialTheme.colorScheme
                    SideEffect { currentScheme = scheme }
                }
            }
        }
        assertScheme(colorSchemeForPreset(ColorSchemePreset.OCEAN, SchemeStyle.NEUTRAL))
        compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
        }
        assertScheme(colorSchemeForPreset(ColorSchemePreset.OCEAN, SchemeStyle.NEUTRAL, true))
        compose.runOnIdle { prefs.edit().putString(ThemePrefs.KEY, ThemePrefs.LIGHT).commit() }
        assertScheme(colorSchemeForPreset(ColorSchemePreset.OCEAN, SchemeStyle.NEUTRAL))
        compose.runOnIdle { prefs.edit().putBoolean(ThemePrefs.KEY_AMOLED, true).commit() }
        compose.runOnIdle { assertEquals(Color.White, currentScheme.background) }
    }
}
