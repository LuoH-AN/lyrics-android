package com.luoh.music.lrc.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.home.HomeLyricAppearance
import com.luoh.music.lrc.ui.home.HomeLyricsPrefs
import com.luoh.music.lrc.ui.home.pop.PopLyricPreview
import com.luoh.music.lrc.ui.home.pop.PopLyricSchemes
import com.luoh.music.lrc.ui.home.pop.PopLyricsPrefs
import com.luoh.music.lrc.ui.home.pop.PopLyricsStyle
import com.luoh.music.lrc.ui.icons.filled.BlurOn
import com.luoh.music.lrc.ui.icons.filled.Contrast
import com.luoh.music.lrc.ui.icons.filled.FormatAlignLeft
import com.luoh.music.lrc.ui.icons.filled.FormatSize
import com.luoh.music.lrc.ui.icons.filled.Layers
import com.luoh.music.lrc.ui.icons.filled.Lyrics
import com.luoh.music.lrc.ui.icons.filled.MotionPhotosOff
import com.luoh.music.lrc.ui.icons.filled.SettingsBrightness
import com.luoh.music.lrc.ui.icons.filled.Speed
import com.luoh.music.lrc.ui.icons.filled.Style
import com.luoh.music.lrc.ui.icons.filled.Sync
import com.luoh.music.lrc.ui.icons.filled.TextFields
import com.luoh.music.lrc.ui.icons.filled.Timeline
import com.luoh.music.lrc.ui.icons.filled.Translate

@Composable
internal fun HomeAlignmentSettings(context: Context, revision: Int, previewPinned: Boolean = false,
    onPinPreview: () -> Unit = {}) {
    val alignment = remember(revision) { HomeLyricsPrefs.alignment(context) }
    val albumBackground = remember(revision) { HomeLyricsPrefs.albumBackground(context) }
    val appearance = remember(revision) { HomeLyricsPrefs.appearance(context) }
    val displayMode = remember(revision) { HomeLyricsPrefs.displayMode(context) }
    val keepAwake = remember(revision) { HomeLyricsPrefs.immersiveKeepAwake(context) }
    fun saveStyle(change: (HomeLyricAppearance) -> HomeLyricAppearance) {
        HomeLyricsPrefs.saveAppearance(context, change(HomeLyricsPrefs.appearance(context)))
    }
    SettingsGroup(title = "显示模式", items = listOf({
        SettingsChoiceItem("显示方式", displayMode,
            listOf(HomeLyricsPrefs.SCROLL to "滚动", HomeLyricsPrefs.POP to "动态"), Icons.Default.Lyrics) {
            HomeLyricsPrefs.saveDisplayMode(context, it)
        }
    }))
    if (displayMode == HomeLyricsPrefs.POP) PopLyricSettings(context, revision, previewPinned, onPinPreview)
    else ScrollingLyricSettings(context, revision, appearance)
    SettingsGroup(title = "屏幕", items = listOf({
        SettingsSwitchItem("播放时保持亮屏", null,
            Icons.Default.SettingsBrightness, keepAwake) { HomeLyricsPrefs.saveImmersiveKeepAwake(context, it) }
    }))
    SettingsGroup(title = "背景", items = listOf({
        SettingsSwitchItem("封面背景", "模糊显示专辑封面", Icons.Default.BlurOn, albumBackground) {
            HomeLyricsPrefs.saveAlbumBackground(context, it)
        }
    }))
    SettingsGroup(title = "歌词布局", items = listOf({
        SettingsChoiceItem("对齐方式", alignment, listOf(HomeLyricsPrefs.LEFT to "左对齐", HomeLyricsPrefs.CENTER to "居中", HomeLyricsPrefs.RIGHT to "右对齐"),
            Icons.Default.FormatAlignLeft) { HomeLyricsPrefs.save(context, it) }
    }))
    SettingsGroup(title = "歌词文字", items = listOf(
        { SettingsSliderItem("原文字号", appearance.fontSize, 16..48, unit = "sp", icon = Icons.Default.FormatSize) {
            value -> saveStyle { it.copy(fontSize = value) }
        } },
        { SettingsSliderItem("译文字号", appearance.translationSize, 12..32, unit = "sp", icon = Icons.Default.Translate) {
            value -> saveStyle { it.copy(translationSize = value) }
        } },
        { SettingsChoiceItem("字重", appearance.fontWeight.toString(), listOf("300" to "细体", "400" to "常规",
            "500" to "中等", "600" to "半粗", "700" to "粗体", "800" to "特粗"), Icons.Default.TextFields) {
            value -> saveStyle { it.copy(fontWeight = value.toInt()) }
        } },
        { SettingsSliderItem("行间距", appearance.lineSpacing, 0..28, step = 2, unit = "dp", icon = Icons.Default.FormatAlignLeft) {
            value -> saveStyle { it.copy(lineSpacing = value) }
        } },
    ))
    SettingsGroup(title = "文字预览", items = listOf({
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("让音乐陪你到天亮", fontSize = appearance.fontSize.sp, fontWeight = FontWeight(appearance.fontWeight))
            Text("Let the music stay with you", fontSize = appearance.translationSize.sp,
                fontWeight = FontWeight(appearance.fontWeight), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }))
}

@Composable
private fun PopLyricSettings(context: Context, revision: Int, previewPinned: Boolean = false,
    onPinPreview: () -> Unit = {}) {
    val style = remember(revision) { PopLyricsPrefs.style(context) }
    val appearance = remember(revision) { HomeLyricsPrefs.appearance(context) }
    fun save(change: (PopLyricsStyle) -> PopLyricsStyle) {
        PopLyricsPrefs.save(context, change(PopLyricsPrefs.style(context)).copy(preset = "custom"))
    }
    if (!previewPinned) SettingsGroup(title = "预览", items = listOf({ PopLyricPreview(style, appearance, onPin = onPinPreview) }))
    PopSchemeSettings(context, revision, style, appearance)
    SettingsGroup(title = "显示与排版", items = listOf(
        { SettingsChoiceItem("字号模式", style.sizingMode,
            listOf("focus" to "自适应", "classic" to "固定"), Icons.Default.FormatSize) {
            value -> save { it.copy(sizingMode = value) }
        } },
        { if (style.sizingMode == "classic") SettingsSliderItem("字号倍率", style.classicScalePercent,
            80..200, 5, "%", Icons.Default.FormatSize) {
            value -> save { it.copy(classicScalePercent = value) }
        } },
        { SettingsChoiceItem("显示单位", style.reveal, listOf("word" to "逐词", "character" to "逐字"), Icons.Default.TextFields) {
            value -> save { it.copy(reveal = value) }
        } },
        { SettingsChoiceItem("排版", style.layout,
            listOf("staggered" to "错落", "centered" to "居中", "stairs" to "阶梯", "wave" to "波浪", "random" to "随机"), Icons.Default.FormatAlignLeft) {
            value -> save { it.copy(layout = value) }
        } },
    ))
    SettingsGroup(title = "歌词分段", items = listOf(
        { SettingsSwitchItem("分段显示", "换段时前段淡出", Icons.Default.Lyrics, style.paginate) {
            value -> save { it.copy(paginate = value) }
        } },
        { SettingsSliderItem("每段字数", style.pageLength, 4..24, 1, "字", Icons.Default.TextFields,
            style.paginate) { value -> save { it.copy(pageLength = value) } } },
    ))
    SettingsGroup(title = "排版间距", items = listOf(
        { SettingsSwitchItem("词组分段", "按词组和停顿划分", Icons.Default.TextFields, style.semanticGrouping) {
            value -> save { it.copy(semanticGrouping = value) }
        } },
        { SettingsSwitchItem("动态间距", "减少文字重叠", Icons.Default.FormatSize, style.dynamicSpacing) {
            value -> save { it.copy(dynamicSpacing = value) }
        } },
        { SettingsSliderItem("词组内间距", style.innerGapDp, 0..12, 1, "dp", Icons.Default.TextFields) {
            value -> save { it.copy(innerGapDp = value) }
        } },
        { SettingsSliderItem("词组间距", style.groupGapDp, 0..24, 1, "dp", Icons.Default.TextFields) {
            value -> save { it.copy(groupGapDp = value) }
        } },
        { SettingsSliderItem("行距倍率", style.rowGapPercent, 0..250, 10, "%", Icons.Default.FormatAlignLeft) {
            value -> save { it.copy(rowGapPercent = value) }
        } },
        { SettingsSliderItem("行宽比例", style.lineWidthPercent, 60..140, 5, "%", Icons.Default.FormatAlignLeft) {
            value -> save { it.copy(lineWidthPercent = value) }
        } },
        { SettingsSliderItem("错落幅度", style.staggerPercent, 0..200, 10, "%", Icons.Default.Layers,
            style.layout != "centered") { value -> save { it.copy(staggerPercent = value) } } },
    ))
    SettingsGroup(title = "动画控制", items = listOf(
        { SettingsChoiceItem("动画强度", style.motionStrength,
            listOf("gentle" to "低", "balanced" to "中", "strong" to "高"), Icons.Default.Speed) {
            value -> save { it.copy(motionStrength = value) }
        } },
        { SettingsSwitchItem("强度自适应", "快句减小动画幅度", Icons.Default.Timeline, style.adaptiveEmphasis) {
            value -> save { it.copy(adaptiveEmphasis = value) }
        } },
        { SettingsSwitchItem("限制叠加", "减小叠加动画幅度", Icons.Default.Layers, style.coordinateMotion) {
            value -> save { it.copy(coordinateMotion = value) }
        } },
        { SettingsSwitchItem("方向联动", "切换沿用镜头方向", Icons.Default.Sync, style.flowTransitions) {
            value -> save { it.copy(flowTransitions = value) }
        } },
    ))
    SettingsGroup(title = "显示范围", items = listOf(
        { SettingsSliderItem("画面占比", style.stageFillPercent, 65..100, 1, "%", Icons.Default.FormatSize,
            style.sizingMode == "focus") {
            value -> save { it.copy(stageFillPercent = value) }
        } },
        { SettingsSliderItem("水平留白", style.stagePaddingDp, 8..40, 2, "dp", Icons.Default.FormatAlignLeft) {
            value -> save { it.copy(stagePaddingDp = value) }
        } },
        { SettingsSliderItem("译文间距", style.translationGapDp, 4..28, 2, "dp", Icons.Default.Translate) {
            value -> save { it.copy(translationGapDp = value) }
        } },
        { SettingsSliderItem("译文最多行数", style.translationMaxLines, 1..4, 1, "行", Icons.Default.Translate) {
            value -> save { it.copy(translationMaxLines = value) }
        } },
    ))
    SettingsGroup(title = "文字入场", items = listOf(
        { SettingsChoiceItem("入场方式", style.entrance,
            listOf("scatter" to "多向", "rise" to "上移", "zoom" to "缩放",
                "converge" to "聚合", "disperse" to "扩散", "random" to "随机"), Icons.Default.Layers) {
            value -> save { it.copy(entrance = value) }
        } },
        { SettingsSliderItem("入场时长", style.entranceMs, 200..1000, 50, "ms", Icons.Default.Speed) {
            value -> save { it.copy(entranceMs = value) }
        } },
        { SettingsSliderItem("提前入场", style.lookaheadMs, 0..250, 10, "ms", Icons.Default.Timeline) {
            value -> save { it.copy(lookaheadMs = value) }
        } },
        { SettingsSliderItem("入场位移", style.travelPercent, 0..200, 10, "%", Icons.Default.Layers) {
            value -> save { it.copy(travelPercent = value) }
        } },
        { SettingsSliderItem("当前字词放大", style.activeScalePercent, 100..180, 5, "%", Icons.Default.FormatSize) {
            value -> save { it.copy(activeScalePercent = value) }
        } },
        { SettingsSliderItem("文字旋转", style.rotationDegrees, 0..30, 1, "°", Icons.Default.Sync) {
            value -> save { it.copy(rotationDegrees = value) }
        } },
        { SettingsSliderItem("长音振幅", style.sustainShakePercent, 0..200, 10, "%", Icons.Default.Sync) {
            value -> save { it.copy(sustainShakePercent = value) }
        } },
    ))
    SettingsGroup(title = "镜头", items = listOf(
        { SettingsChoiceItem("镜头运动", style.camera,
            listOf("off" to "固定", "drift" to "漂移", "follow" to "跟随", "orbit" to "环绕"), Icons.Default.MotionPhotosOff) {
            value -> save { it.copy(camera = value) }
        } },
        { SettingsSliderItem("镜头幅度", style.cameraPercent, 0..200, 10, "%", Icons.Default.Speed,
            style.camera != "off") { value -> save { it.copy(cameraPercent = value) } } },
        { SettingsSwitchItem("循环缩放", null, Icons.Default.Sync, style.breathing) {
            value -> save { it.copy(breathing = value) }
        } },
        { SettingsSliderItem("跟随速度", style.cameraSpeedPercent, 50..180, 5, "%", Icons.Default.Speed,
            style.camera in listOf("follow", "orbit")) { value -> save { it.copy(cameraSpeedPercent = value) } } },
    ))
    SettingsGroup(title = "高亮与切换", items = listOf(
        { SettingsSliderItem("整行运动", style.rowMotionPercent, 0..100, 5, "%", Icons.Default.Layers) {
            value -> save { it.copy(rowMotionPercent = value) }
        } },
        { SettingsSliderItem("高亮延时", style.textHoldMs, 0..800, 50, "ms", Icons.Default.Timeline) {
            value -> save { it.copy(textHoldMs = value) }
        } },
        { SettingsSliderItem("已唱不透明度", style.passedOpacityPercent, 0..100, 5, "%", Icons.Default.Contrast) {
            value -> save { it.copy(passedOpacityPercent = value) }
        } },
        { SettingsSwitchItem("时长自适应", "快句缩短入场时间", Icons.Default.Speed, style.adaptiveTiming) {
            value -> save { it.copy(adaptiveTiming = value) }
        } },
        { SettingsChoiceItem("整句切换", style.transition,
            listOf("fade" to "淡入淡出", "slide" to "滑动", "zoom" to "缩放",
                "converge" to "聚合", "disperse" to "扩散", "random" to "随机"), Icons.Default.Layers) {
            value -> save { it.copy(transition = value) }
        } },
        { if (style.transition in listOf("slide", "random"))
            SettingsChoiceItem("滑动方向", style.pushDirection,
                listOf("left" to "向左", "right" to "向右", "up" to "向上", "down" to "向下",
                    "horizontal" to "左右随机", "vertical" to "上下随机", "random" to "四向随机"), Icons.Default.Layers) {
                value -> save { it.copy(pushDirection = value) }
            } },
    ))
}

@Composable
private fun PopSchemeSettings(context: Context, revision: Int, style: PopLyricsStyle, appearance: HomeLyricAppearance) {
    val saved = remember(revision) { PopLyricSchemes.saved(context) }
    val schemes = PopLyricSchemes.builtIns + saved
    val selectedScheme = (saved + PopLyricSchemes.builtIns).firstOrNull {
        it.motion.copy(preset = "custom") == style.copy(preset = "custom") && it.text == appearance }
    val selected = selectedScheme?.id ?: "custom"
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val savedRows: List<@Composable () -> Unit> = saved.map { scheme -> {
        SettingsItem(headlineContent = { Text(scheme.name) },
            modifier = Modifier.clickable { PopLyricSchemes.apply(context, scheme) },
            trailingContent = { TextButton(onClick = { PopLyricSchemes.delete(context, scheme.id) }) { Text("删除") } })
    } }
    SettingsGroup(title = "方案", items = listOf<@Composable () -> Unit>(
        { SettingsChoiceItem("选择方案", selected, listOf("custom" to "自定义") + schemes.map {
            it.id to it.name },
            Icons.Default.Style, showSummary = false, summary = selectedScheme?.description) {
            id -> schemes.firstOrNull { it.id == id }?.let { PopLyricSchemes.apply(context, it) } } },
        { SettingsNavigationItem("恢复默认", "重置动画和文字设置", Icons.Default.Sync) {
            PopLyricSchemes.apply(context, PopLyricSchemes.recommended)
        } },
        { SettingsNavigationItem("保存方案", icon = Icons.Default.Add) {
            name = ""; naming = true
        } },
    ) + savedRows)
    if (naming) {
        val normalized = name.trim()
        val replacing = saved.any { it.name == normalized }
        val full = !replacing && saved.size >= PopLyricSchemes.LIMIT
        AlertDialog(onDismissRequest = { naming = false }, title = { Text(if (full) "方案已满" else "保存方案") },
            text = { Column {
                OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, singleLine = true,
                    label = { Text("方案名称") })
            } },
            confirmButton = { TextButton(enabled = normalized.isNotBlank() && !full, onClick = {
                if (PopLyricSchemes.save(context, normalized)) naming = false
            }) { Text(when { full -> "已满"; replacing -> "覆盖"; else -> "保存" }) } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("取消") } })
    }
}
