package com.luoh.music.lrc.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.R
import com.luoh.music.lrc.lyrics.LyricLine
import com.luoh.music.lrc.lyrics.LyricWord
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.home.HomeLyricAppearance
import com.luoh.music.lrc.ui.home.HomeMotionPrefs
import com.luoh.music.lrc.ui.home.KaraokeLyricText
import com.luoh.music.lrc.ui.home.ScrollingCharacterMotion
import com.luoh.music.lrc.ui.icons.filled.BlurOn
import com.luoh.music.lrc.ui.icons.filled.Layers
import com.luoh.music.lrc.ui.icons.filled.Speed
import com.luoh.music.lrc.ui.icons.filled.TextFields

@Composable
internal fun ScrollingLyricSettings(context: Context, revision: Int, appearance: HomeLyricAppearance) {
    val motion = remember(revision) { HomeMotionPrefs.style(context).characterMotion }
    fun save(transform: (ScrollingCharacterMotion) -> ScrollingCharacterMotion) {
        val style = HomeMotionPrefs.style(context)
        HomeMotionPrefs.save(context, style.copy(characterMotion = transform(style.characterMotion)))
    }
    SettingsGroup(title = context.getString(R.string.scrolling_mode), items = buildList<@Composable () -> Unit> {
        add {
            SettingsSwitchItem(context.getString(R.string.floating_highlights), context.getString(R.string.applies_to_word_timed_lyrics), Icons.Default.TextFields, motion.enabled) {
                enabled -> save { it.copy(enabled = enabled) }
            }
        }
        if (motion.enabled) {
            add { ScrollingLyricPreview(motion, appearance) }
            add {
                SettingsSliderItem(context.getString(R.string.float_height), motion.liftDp, ScrollingCharacterMotion.LIFT_RANGE,
                    unit = "dp", icon = Icons.Default.Layers) { value -> save { it.copy(liftDp = value) } }
            }
            add {
                SettingsSliderItem(context.getString(R.string.glow_intensity), motion.glowPercent, ScrollingCharacterMotion.GLOW_RANGE,
                    step = 5, icon = Icons.Default.BlurOn) { value -> save { it.copy(glowPercent = value) } }
            }
            add {
                SettingsSliderItem(context.getString(R.string.rise_duration), motion.durationMs, ScrollingCharacterMotion.DURATION_RANGE,
                    step = 50, unit = "ms", icon = Icons.Default.Speed) { value -> save { it.copy(durationMs = value) } }
            }
        }
    })
}

@Composable
private fun ScrollingLyricPreview(motion: ScrollingCharacterMotion, appearance: HomeLyricAppearance) {
    val context = LocalContext.current
    val line = remember {
        LyricLine(0L, context.getString(R.string.scrolling_preview_line), words = listOf(
            LyricWord(0L, 380L, context.getString(R.string.scrolling_preview_word_one)),
            LyricWord(380L, 460L, context.getString(R.string.scrolling_preview_word_two)),
            LyricWord(840L, 600L, context.getString(R.string.scrolling_preview_word_three)),
            LyricWord(1440L, 1650L, context.getString(R.string.scrolling_preview_word_four)),
        ))
    }
    val position = remember { mutableLongStateOf(3090L) }
    val previewActive = LocalSettingsScrollRetentionActive.current && LocalAgoraMotionPolicy.current.allowContinuousMotion
    LaunchedEffect(previewActive) {
        if (!previewActive) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { position.longValue = (it - start) % 3900L }
    }
    KaraokeLyricText(line.text, line, position, fullySung = !previewActive, active = previewActive, wordTiming = true,
        color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, appearance = appearance,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        characterMotion = motion.takeIf { previewActive })
}
