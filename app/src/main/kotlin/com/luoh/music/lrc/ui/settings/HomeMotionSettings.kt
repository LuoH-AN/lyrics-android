package com.luoh.music.lrc.ui.settings

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.R
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.home.HomeMotionPrefs
import com.luoh.music.lrc.ui.home.MotionStyle
import com.luoh.music.lrc.ui.icons.filled.BlurOn
import com.luoh.music.lrc.ui.icons.filled.FormatSize
import com.luoh.music.lrc.ui.icons.filled.Layers
import com.luoh.music.lrc.ui.icons.filled.MotionPhotosOff
import com.luoh.music.lrc.ui.icons.filled.Speed
import com.luoh.music.lrc.ui.theme.ThemePrefs
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
internal fun HomeMotionSettings(context: Context, revision: Int) {
    val style = remember(revision) { HomeMotionPrefs.style(context) }
    val appPrefs = remember(context) { context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE) }
    val reduceMotion = remember(revision) { appPrefs.getBoolean("reduce_motion", false) }
    fun save(transform: (MotionStyle) -> MotionStyle) { HomeMotionPrefs.save(context, transform(HomeMotionPrefs.style(context))) }
    SettingsGroup(title = context.getString(R.string.page_animations), items = listOf({
        SettingsSwitchItem(context.getString(R.string.reduce_motion), context.getString(R.string.reduce_motion_description), Icons.Default.MotionPhotosOff, reduceMotion) {
            appPrefs.edit().putBoolean("reduce_motion", it).apply()
        }
    }))
    SettingsGroup(title = context.getString(R.string.preview), items = listOf({ ComposeMotionPreview(style) }))
    SettingsGroup(title = context.getString(R.string.scrolling_and_stagger), items = listOf(
        { SettingsSwitchItem(context.getString(R.string.smooth_scrolling), context.getString(R.string.animate_transitions_between_lyric_lines), Icons.Default.Speed, style.easedScroll) { value -> save { it.copy(easedScroll = value) } } },
        { SettingsSliderItem(context.getString(R.string.scroll_duration), style.scrollDurationMs, MotionStyle.DURATION_MIN..MotionStyle.DURATION_MAX, MotionStyle.DURATION_STEP,
            "ms", Icons.Default.Speed, style.easedScroll) { value -> save { it.copy(scrollDurationMs = value) } } },
        { SettingsSwitchItem(context.getString(R.string.stagger_lines), context.getString(R.string.move_neighboring_lines_in_sequence), Icons.Default.Layers, style.stagger) { value -> save { it.copy(stagger = value) } } },
        { SettingsSliderItem(context.getString(R.string.stagger_delay), style.staggerDelayMs, MotionStyle.STAGGER_MIN..MotionStyle.STAGGER_MAX, MotionStyle.STAGGER_STEP,
            "ms", Icons.Default.Layers, style.stagger) { value -> save { it.copy(staggerDelayMs = value) } } },
    ))
    SettingsGroup(title = context.getString(R.string.scaling_and_blur), items = listOf(
        { SettingsSwitchItem(context.getString(R.string.scale_inactive_lines), context.getString(R.string.shrink_lines_around_the_current_line), Icons.Default.FormatSize, style.scale) { value -> save { it.copy(scale = value) } } },
        { SettingsSliderItem(context.getString(R.string.scale_amount), style.scalePercent, MotionStyle.PERCENT_MIN..MotionStyle.PERCENT_MAX, MotionStyle.PERCENT_STEP,
            "%", Icons.Default.FormatSize, style.scale) { value -> save { it.copy(scalePercent = value) } } },
        { SettingsSwitchItem(context.getString(R.string.blur_inactive_lines), context.getString(R.string.increase_blur_on_lines_farther_away), Icons.Default.BlurOn, style.depthBlur) { value -> save { it.copy(depthBlur = value) } } },
        { SettingsSliderItem(context.getString(R.string.blur_amount), style.blurPercent, MotionStyle.PERCENT_MIN..MotionStyle.PERCENT_MAX, MotionStyle.PERCENT_STEP,
            "%", Icons.Default.BlurOn, style.depthBlur) { value -> save { it.copy(blurPercent = value) } } },
    ))
}

@Composable
private fun ComposeMotionPreview(style: MotionStyle) {
    val lines = remember { listOf("City lights", "A quiet melody", "Stay with me", "One more song", "Until tomorrow") }
    var active by remember { mutableIntStateOf(2) }
    val screenActive = LocalSettingsScrollRetentionActive.current
    val allowContinuousMotion = LocalAgoraMotionPolicy.current.allowContinuousMotion
    LaunchedEffect(screenActive, allowContinuousMotion) {
        if (!screenActive || !allowContinuousMotion) return@LaunchedEffect
        while (true) { delay(2400); active = if (active == 2) 3 else 2 }
    }
    val baseColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val rowTravelPx = with(LocalDensity.current) { 30.dp.toPx() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lines.forEachIndexed { index, line ->
            val distance = abs(index - active)
            val duration = if (style.easedScroll) style.scrollDurationMs else 0
            val stagger = if (style.stagger) distance * style.staggerDelayMs else 0
            val scale by animateFloatAsState(
                if (style.scale) 1f - (distance * .045f * style.scalePercent / 100f).coerceAtMost(.2f) else 1f,
                tween(duration, delayMillis = stagger, easing = FastOutSlowInEasing), label = "previewScale$index",
            )
            val translation by animateFloatAsState((2 - active) * rowTravelPx,
                tween(duration, delayMillis = stagger, easing = FastOutSlowInEasing), label = "previewScroll$index")
            val color by animateColorAsState(if (distance == 0) activeColor else baseColor,
                tween(duration), label = "previewColor$index")
            val blur by animateFloatAsState(if (style.depthBlur) (distance - 1).coerceAtLeast(0) * style.blurPercent / 100f else 0f,
                tween(duration), label = "previewBlur$index")
            Text(line, fontSize = 19.sp, color = color, fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale; translationY = translation }.blur(blur.dp))
        }
    }
}
