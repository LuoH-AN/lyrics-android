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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.lyrics.LyricLine
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.home.HomeLyricAppearance
import com.luoh.music.lrc.ui.home.HomeMotionPrefs
import com.luoh.music.lrc.ui.home.KaraokeLyricText
import com.luoh.music.lrc.ui.home.MotionStyle
import com.luoh.music.lrc.ui.home.ScrollingCharacterMotion
import com.luoh.music.lrc.ui.icons.filled.BlurOn
import com.luoh.music.lrc.ui.icons.filled.FormatSize
import com.luoh.music.lrc.ui.icons.filled.Layers
import com.luoh.music.lrc.ui.icons.filled.MotionPhotosOff
import com.luoh.music.lrc.ui.icons.filled.Speed
import com.luoh.music.lrc.ui.icons.filled.TextFields
import com.luoh.music.lrc.ui.theme.ThemePrefs
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
internal fun HomeMotionSettings(context: Context, revision: Int) {
    val style = remember(revision) { HomeMotionPrefs.style(context) }
    val appPrefs = remember(context) { context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE) }
    val reduceMotion = remember(revision) { appPrefs.getBoolean("reduce_motion", false) }
    val characters = style.characterMotion
    fun save(transform: (MotionStyle) -> MotionStyle) { HomeMotionPrefs.save(context, transform(HomeMotionPrefs.style(context))) }
    fun saveCharacters(transform: (ScrollingCharacterMotion) -> ScrollingCharacterMotion) =
        save { it.copy(characterMotion = transform(it.characterMotion)) }
    SettingsGroup(title = "页面动画", items = listOf({
        SettingsSwitchItem("减少动效", "减少页面与面板动画", Icons.Default.MotionPhotosOff, reduceMotion) {
            appPrefs.edit().putBoolean("reduce_motion", it).apply()
        }
    }))
    SettingsGroup(title = "预览", items = listOf({ ComposeMotionPreview(style) }))
    SettingsGroup(title = "逐字动效", items = buildList<@Composable () -> Unit> {
        add {
            SettingsSwitchItem("逐字浮光跃动", null, Icons.Default.TextFields, characters.enabled) { enabled ->
                saveCharacters { it.copy(enabled = enabled) }
            }
        }
        if (characters.enabled) {
            add {
                SettingsSliderItem("跃动高度", characters.liftDp, ScrollingCharacterMotion.LIFT_RANGE,
                    unit = "dp", icon = Icons.Default.Layers) { value -> saveCharacters { it.copy(liftDp = value) } }
            }
            add {
                SettingsSliderItem("浮光强度", characters.glowPercent, ScrollingCharacterMotion.GLOW_RANGE,
                    step = 5, unit = "%", icon = Icons.Default.BlurOn) { value -> saveCharacters { it.copy(glowPercent = value) } }
            }
            add {
                SettingsSliderItem("动效时长", characters.durationMs, ScrollingCharacterMotion.DURATION_RANGE,
                    step = 50, unit = "ms", icon = Icons.Default.Speed) { value -> saveCharacters { it.copy(durationMs = value) } }
            }
        }
    })
    SettingsGroup(title = "滚动与错位", items = listOf(
        { SettingsSwitchItem("缓动滚动", "平滑切换歌词行", Icons.Default.Speed, style.easedScroll) { value -> save { it.copy(easedScroll = value) } } },
        { SettingsSliderItem("切行时长", style.scrollDurationMs, MotionStyle.DURATION_MIN..MotionStyle.DURATION_MAX, MotionStyle.DURATION_STEP,
            "ms", Icons.Default.Speed, style.easedScroll) { value -> save { it.copy(scrollDurationMs = value) } } },
        { SettingsSwitchItem("逐行错位", "相邻行依次跟随", Icons.Default.Layers, style.stagger) { value -> save { it.copy(stagger = value) } } },
        { SettingsSliderItem("错位延迟", style.staggerDelayMs, MotionStyle.STAGGER_MIN..MotionStyle.STAGGER_MAX, MotionStyle.STAGGER_STEP,
            "ms", Icons.Default.Layers, style.stagger) { value -> save { it.copy(staggerDelayMs = value) } } },
    ))
    SettingsGroup(title = "缩放与模糊", items = listOf(
        { SettingsSwitchItem("行间缩放", "缩小非当前行", Icons.Default.FormatSize, style.scale) { value -> save { it.copy(scale = value) } } },
        { SettingsSliderItem("缩放强度", style.scalePercent, MotionStyle.PERCENT_MIN..MotionStyle.PERCENT_MAX, MotionStyle.PERCENT_STEP,
            "%", Icons.Default.FormatSize, style.scale) { value -> save { it.copy(scalePercent = value) } } },
        { SettingsSwitchItem("非当前行模糊", "距离越远越模糊", Icons.Default.BlurOn, style.depthBlur) { value -> save { it.copy(depthBlur = value) } } },
        { SettingsSliderItem("模糊强度", style.blurPercent, MotionStyle.PERCENT_MIN..MotionStyle.PERCENT_MAX, MotionStyle.PERCENT_STEP,
            "%", Icons.Default.BlurOn, style.depthBlur) { value -> save { it.copy(blurPercent = value) } } },
    ))
}

@Composable
private fun ComposeMotionPreview(style: MotionStyle) {
    val lines = remember {
        listOf("City lights", "A quiet melody", "让音乐陪着你", "直到天亮", "Until tomorrow").mapIndexed { index, text ->
            val start = if (index == 3) 2400L else 0L
            LyricLine(start, text, endMs = start + 2400L)
        }
    }
    val position = remember { mutableLongStateOf(0L) }
    var active by remember { mutableIntStateOf(2) }
    val screenActive = LocalSettingsScrollRetentionActive.current
    val allowContinuousMotion = LocalAgoraMotionPolicy.current.allowContinuousMotion
    val animateCharacters = style.characterMotion.visible && allowContinuousMotion
    LaunchedEffect(screenActive, allowContinuousMotion, animateCharacters) {
        if (!screenActive || !allowContinuousMotion) return@LaunchedEffect
        if (animateCharacters) {
            val start = withFrameMillis { it }
            while (true) withFrameMillis {
                val now = (it - start) % 4800L
                active = if (now < 2400L) 2 else 3
                position.longValue = now
            }
        } else {
            while (true) { delay(2400); active = if (active == 2) 3 else 2 }
        }
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
            val modifier = Modifier.fillMaxWidth().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = translation
            }.blur(blur.dp)
            if (animateCharacters && distance == 0) {
                KaraokeLyricText(line.text, line, position, fullySung = false, active = true, wordTiming = false,
                    color = color, textAlign = TextAlign.Start, appearance = HomeLyricAppearance(fontSize = 19, fontWeight = 700),
                    modifier = modifier, characterMotion = style.characterMotion, motionEndMs = requireNotNull(line.endMs))
            } else {
                Text(line.text, fontSize = 19.sp, lineHeight = 24.7.sp, color = color,
                    fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Medium, modifier = modifier)
            }
        }
    }
}
