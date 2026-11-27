package com.luoh.music.lrc.ui.settings

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.LyricsTileService
import com.luoh.music.lrc.diagnostics.UsageAnalytics
import com.luoh.music.lrc.overlay.OverlayAppearance
import com.luoh.music.lrc.overlay.OverlayNotificationAccess
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.icons.filled.ColorLens
import com.luoh.music.lrc.ui.icons.filled.Contrast
import com.luoh.music.lrc.ui.icons.filled.FormatAlignLeft
import com.luoh.music.lrc.ui.icons.filled.FormatSize
import com.luoh.music.lrc.ui.icons.filled.Layers
import com.luoh.music.lrc.ui.icons.filled.Lyrics
import com.luoh.music.lrc.ui.icons.filled.Palette
import com.luoh.music.lrc.ui.icons.filled.Remove
import com.luoh.music.lrc.ui.icons.filled.Style
import com.luoh.music.lrc.ui.icons.filled.TextFields
import kotlin.math.roundToInt

@Composable
private fun overlaySettingsRevision(context: Context): Int {
    val owner = LocalLifecycleOwner.current
    val active = LocalSettingsScrollRetentionActive.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(context, owner, active) {
        if (!active) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { revision++ }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        owner.lifecycle.addObserver(observer)
        revision++
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(LyricsOverlayService.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            owner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    return revision
}

@Composable
private fun DesktopPositionSettingsItem(context: Context, prefs: SharedPreferences) {
    val revision = overlaySettingsRevision(context) + settingsPreferenceRevision(prefs)
    val running = remember(revision) { LyricsOverlayService.isRunning }
    val locked = remember(revision) { prefs.getBoolean(LyricsOverlayService.PREF_POSITION_LOCKED, false) }
    SettingsNavigationItem(
        if (locked) "解锁" else "锁定",
        icon = Icons.Default.Lock,
        enabled = running || locked,
    ) {
        if (LyricsOverlayService.isRunning) {
            settingsOverlayAction(context, if (locked) LyricsOverlayService.ACTION_UNLOCK else LyricsOverlayService.ACTION_LOCK)
        } else {
            prefs.edit().putBoolean(LyricsOverlayService.PREF_POSITION_LOCKED, false).apply()
        }
    }
}

@Composable
private fun DesktopNotificationSettingsItem(context: Context) {
    val revision = overlaySettingsRevision(context)
    val allowed = remember(revision) { OverlayNotificationAccess.isEnabled(context) }
    val notificationSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (OverlayNotificationAccess.isEnabled(context)) {
            settingsOverlayAction(context, LyricsOverlayService.ACTION_REFRESH_NOTIFICATION)
        }
    }
    SettingsNavigationItem(
        "桌面歌词通知",
        if (allowed) "已开启" else "未开启",
        Icons.Default.Notifications,
    ) {
        if (allowed && LyricsOverlayService.isRunning) {
            settingsOverlayAction(context, LyricsOverlayService.ACTION_REFRESH_NOTIFICATION)
        } else {
            runCatching { notificationSettings.launch(OverlayNotificationAccess.settingsIntent(context)) }
                .onFailure { settingsToast(context, "无法打开此页面") }
        }
    }
}

@Composable
internal fun DesktopAccessSettings(context: Context) {
    val revision = overlaySettingsRevision(context)
    val running = remember(revision) { LyricsOverlayService.isRunning }
    val prefs = remember(context) { context.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        LyricsOverlayService.start(context)
    }
    val notificationAllowed = remember(revision) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val overlayAllowed = remember(revision) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }
    SettingsGroup(title = "桌面歌词", items = buildList<@Composable () -> Unit> {
        add {
            SettingsNavigationItem("添加快捷开关", "在系统快捷设置中显示", Icons.Default.Lyrics) {
                UsageAnalytics.feature(UsageAnalytics.Feature.ADD_TILE)
                LyricsTileService.requestAdd(context)
            }
        }
        add {
            SettingsSwitchItem("桌面歌词", "在其他应用上显示歌词", Icons.Default.Lyrics, running) {
                UsageAnalytics.feature(if (running) UsageAnalytics.Feature.OVERLAY_STOP else UsageAnalytics.Feature.OVERLAY_START)
                when {
                    running -> context.stopService(Intent(context, LyricsOverlayService::class.java))
                    !notificationAllowed -> {
                        settingsToast(context, "请先授予通知使用权")
                        settingsOpenIntent(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    !overlayAllowed -> {
                        settingsToast(context, "请先授予悬浮窗权限")
                        settingsOpenIntent(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    }
                    OverlayNotificationAccess.needsPermission(context) -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> LyricsOverlayService.start(context)
                }
            }
        }
        add { DesktopPositionSettingsItem(context, prefs) }
        add { DesktopNotificationSettingsItem(context) }
        add {
            SettingsNavigationItem("通知使用权", if (notificationAllowed) "已授权" else "未授权", Icons.Default.Notifications) {
                settingsOpenIntent(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        add {
            SettingsNavigationItem("悬浮窗权限", if (overlayAllowed) "已授权" else "未授权", Icons.Default.Layers) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    settingsOpenIntent(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }
            }
        }
    })
}

@Composable
internal fun DesktopAppearanceSettings(context: Context, prefs: SharedPreferences, revision: Int) {
    val background = remember(revision) {
        when (prefs.getString(LyricsOverlayService.PREF_BACKGROUND_MODE_COMPACT,
            prefs.getString(LyricsOverlayService.PREF_BACKGROUND_MODE, LyricsOverlayService.BACKGROUND_DEFAULT))) {
            LyricsOverlayService.BACKGROUND_HIGH -> LyricsOverlayService.BACKGROUND_HIGH
            LyricsOverlayService.BACKGROUND_MEDIUM, LyricsOverlayService.BACKGROUND_LOW -> LyricsOverlayService.BACKGROUND_LOW
            else -> LyricsOverlayService.BACKGROUND_TRANSPARENT
        }
    }
    val fontScale = remember(revision) {
        prefs.getInt(LyricsOverlayService.PREF_FONT_SCALE_COMPACT_PERCENT,
            prefs.getInt(LyricsOverlayService.PREF_FONT_SCALE_PERCENT, LyricsOverlayService.FONT_SCALE_DEFAULT_PERCENT))
            .coerceIn(LyricsOverlayService.FONT_SCALE_MIN_PERCENT, LyricsOverlayService.FONT_SCALE_MAX_PERCENT)
    }
    val fontWeight = remember(revision) {
        OverlayAppearance.normalizeWeight(prefs.getInt(LyricsOverlayService.PREF_FONT_WEIGHT, OverlayAppearance.DEFAULT_FONT_WEIGHT))
    }
    val color = remember(revision) {
        prefs.getString(LyricsOverlayService.PREF_LYRIC_COLOR_COMPACT,
            prefs.getString(LyricsOverlayService.PREF_LYRIC_COLOR, LyricsOverlayService.LYRIC_COLOR_DEFAULT))
            .orEmpty().ifBlank { LyricsOverlayService.LYRIC_COLOR_DEFAULT }
    }
    val before = remember(revision) { prefs.getInt(LyricsOverlayService.PREF_CONTEXT_BEFORE, 0).coerceIn(0, 2) }
    val after = remember(revision) { prefs.getInt(LyricsOverlayService.PREF_CONTEXT_AFTER, 1).coerceIn(0, 2) }
    val translation = remember(revision) {
        prefs.getString(LyricsOverlayService.PREF_TRANSLATION_MODE, LyricsOverlayService.TRANSLATION_BILINGUAL).orEmpty()
    }
    val style = remember(revision) { OverlayAppearance.textStyle(prefs) }
    val gradientEnd = remember(revision) { prefs.getString(OverlayAppearance.PREF_GRADIENT_END, OverlayAppearance.DEFAULT_GRADIENT_END).orEmpty() }
    val opacity = remember(revision) { prefs.getInt(OverlayAppearance.PREF_BACKGROUND_OPACITY, OverlayAppearance.DEFAULT_BACKGROUND_OPACITY).coerceIn(10, 90) }
    val radius = remember(revision) { prefs.getInt(OverlayAppearance.PREF_CORNER_RADIUS, OverlayAppearance.DEFAULT_CORNER_RADIUS).coerceIn(0, 32) }
    val widthPercent = remember(revision) {
        val display = context.resources.displayMetrics
        val storedWidth = prefs.getInt("compact_width_v1", (360 * display.density).roundToInt())
        prefs.getInt(OverlayAppearance.PREF_WIDTH_PERCENT, (storedWidth * 100f / display.widthPixels.coerceAtLeast(1)).roundToInt()).coerceIn(40, 100)
    }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var pickingGradientEnd by rememberSaveable { mutableStateOf(false) }
    fun refreshAppearance() = settingsOverlayAction(context, LyricsOverlayService.ACTION_REFRESH_APPEARANCE)
    fun saveColor(hex: String) {
        prefs.edit().putString(LyricsOverlayService.PREF_LYRIC_COLOR_COMPACT, hex).apply()
        settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_LYRIC_COLOR) {
            putExtra(LyricsOverlayService.EXTRA_LYRIC_COLOR, hex)
            putExtra(LyricsOverlayService.EXTRA_TARGET_COMPACT, true)
        }
    }
    SettingsGroup(title = "位置与操作", items = listOf(
        { DesktopPositionSettingsItem(context, prefs) },
        { DesktopNotificationSettingsItem(context) },
    ))
    SettingsGroup(title = "歌词预览", items = listOf({
        val foreground = OverlayAppearance.parseColor(color)
        val backgroundColor = Color(OverlayAppearance.backgroundColor(background, foreground, opacity))
        val shape = RoundedCornerShape(radius.dp)
        val brush = style.gradientEnd?.let { end ->
            if (style.gradientVertical) Brush.verticalGradient(listOf(Color(foreground), Color(end)))
            else Brush.horizontalGradient(listOf(Color(foreground), Color(end)))
        } ?: SolidColor(Color(foreground))
        Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(radius.dp)).drawBehind {
            val tile = 12.dp.toPx()
            for (row in 0..(size.height / tile).toInt()) for (column in 0..(size.width / tile).toInt()) {
                drawRect(if ((row + column) % 2 == 0) Color(0xFF506173) else Color(0xFF687B8F),
                    Offset(column * tile, row * tile), Size(tile, tile))
            }
        }) {
            Column(Modifier.fillMaxWidth().background(backgroundColor, shape)
                .border(1.dp, if (backgroundColor.alpha == 0f) Color.Transparent else Color(OverlayAppearance.backgroundBorderColor(foreground)), shape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = when (style.alignment) {
                    OverlayAppearance.CENTER -> Alignment.CenterHorizontally
                    OverlayAppearance.RIGHT -> Alignment.End
                    else -> Alignment.Start
                }) {
                val lines = listOf("City lights" to "街灯轻轻亮起", "A quiet melody" to "旋律停在耳边", "Stay with me" to "让歌词陪着你", "One more song" to "再听一首歌", "Until tomorrow" to "直到明天")
                for (index in (2 - before)..(2 + after)) {
                    val (original, translated) = lines[index]
                    val size = (if (index == 2) 30f else 23f) * fontScale / 100f
                    Text(if (translation == LyricsOverlayService.TRANSLATION_TRANSLATED) translated else original,
                        style = TextStyle(brush = brush, fontSize = size.sp, fontWeight = FontWeight(fontWeight)),
                        modifier = Modifier.alpha(if (index == 2) 1f else .55f), maxLines = 1)
                    if (index == 2 && translation == LyricsOverlayService.TRANSLATION_BILINGUAL) Text(translated,
                        style = TextStyle(brush = brush, fontSize = (23f * fontScale / 100f).sp, fontWeight = FontWeight(fontWeight)),
                        modifier = Modifier.alpha(.8f), maxLines = 1)
                }
            }
        }
    }))
    SettingsGroup(title = "文字与背景", items = listOf(
        {
            SettingsChoiceItem("对齐方式", style.alignment, listOf(OverlayAppearance.LEFT to "左对齐",
                OverlayAppearance.CENTER to "居中", OverlayAppearance.RIGHT to "右对齐"), Icons.Default.FormatAlignLeft) {
                prefs.edit().putString(OverlayAppearance.PREF_ALIGNMENT, it).apply()
                refreshAppearance()
            }
        },
        {
            SettingsChoiceItem("背景", background, listOf(
                LyricsOverlayService.BACKGROUND_TRANSPARENT to "透明",
                LyricsOverlayService.BACKGROUND_LOW to "半透明",
                LyricsOverlayService.BACKGROUND_HIGH to "不透明",
            ), Icons.Default.Layers) { mode ->
                prefs.edit().putString(LyricsOverlayService.PREF_BACKGROUND_MODE_COMPACT, mode).apply()
                settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_BACKGROUND) {
                    putExtra(LyricsOverlayService.EXTRA_BACKGROUND_MODE, mode)
                    putExtra(LyricsOverlayService.EXTRA_TARGET_COMPACT, true)
                }
            }
        },
        {
            SettingsSliderItem("半透明强度", opacity, 10..90, step = 5, icon = Icons.Default.Contrast,
                enabled = background == LyricsOverlayService.BACKGROUND_LOW) {
                prefs.edit().putInt(OverlayAppearance.PREF_BACKGROUND_OPACITY, it).apply()
                refreshAppearance()
            }
        },
        {
            SettingsSliderItem("背景圆角", radius, 0..32, unit = " dp", icon = Icons.Default.Layers) {
                prefs.edit().putInt(OverlayAppearance.PREF_CORNER_RADIUS, it).apply()
                refreshAppearance()
            }
        },
        {
            SettingsSliderItem("悬浮窗宽度", widthPercent, 40..100, icon = Icons.Default.FormatSize) {
                prefs.edit().putInt(OverlayAppearance.PREF_WIDTH_PERCENT, it).apply()
                refreshAppearance()
            }
        },
        {
            SettingsSliderItem("字号", fontScale, LyricsOverlayService.FONT_SCALE_MIN_PERCENT..LyricsOverlayService.FONT_SCALE_MAX_PERCENT,
                icon = Icons.Default.FormatSize) { value ->
                prefs.edit().putInt(LyricsOverlayService.PREF_FONT_SCALE_COMPACT_PERCENT, value).apply()
                settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_FONT_SCALE) {
                    putExtra(LyricsOverlayService.EXTRA_FONT_SCALE_PERCENT, value)
                    putExtra(LyricsOverlayService.EXTRA_TARGET_COMPACT, true)
                }
            }
        },
        {
            SettingsSliderItem("字重", OverlayAppearance.weightToPercent(fontWeight), OverlayAppearance.MIN_FONT_WEIGHT_PERCENT..OverlayAppearance.MAX_FONT_WEIGHT_PERCENT,
                step = 25, icon = Icons.Default.TextFields) { value ->
                val weight = OverlayAppearance.percentToWeight(value)
                prefs.edit().putInt(LyricsOverlayService.PREF_FONT_WEIGHT, weight).apply()
                settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_FONT_WEIGHT) { putExtra(LyricsOverlayService.EXTRA_FONT_WEIGHT, weight) }
            }
        },
        {
            SettingsItem(
                headlineContent = { Text("歌词颜色") },
                supportingContent = { Text(color.uppercase()) },
                leadingContent = { SettingsLeadingIcon(Icons.Default.Palette) },
                trailingContent = {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(settingsParseColor(color)))
                },
                modifier = Modifier.clickable { pickingGradientEnd = false; showColorPicker = true },
            )
        },
        {
            SettingsSwitchItem("渐变歌词", "双色渐变，逐字高亮", Icons.Default.Palette, style.gradientEnd != null) {
                prefs.edit().putBoolean(OverlayAppearance.PREF_GRADIENT, it).apply()
                refreshAppearance()
            }
        },
    ))
    if (style.gradientEnd != null) SettingsGroup(title = "渐变配色", items = listOf(
        {
            SettingsItem(
                headlineContent = { Text("渐变终点颜色") }, supportingContent = { Text(gradientEnd.uppercase()) },
                leadingContent = { SettingsLeadingIcon(Icons.Default.ColorLens) },
                trailingContent = { Box(Modifier.size(32.dp).clip(CircleShape).background(settingsParseColor(gradientEnd))) },
                modifier = Modifier.clickable { pickingGradientEnd = true; showColorPicker = true },
            )
        },
        {
            SettingsChoiceItem("渐变方向", if (style.gradientVertical) "vertical" else "horizontal",
                listOf("horizontal" to "横向", "vertical" to "纵向"), Icons.Default.Style) {
                prefs.edit().putBoolean(OverlayAppearance.PREF_GRADIENT_VERTICAL, it == "vertical").apply()
                refreshAppearance()
            }
        },
    ))
    SettingsGroup(title = "上下文显示", items = listOf(
        {
            SettingsCountItem("前文行数", before) { value ->
                prefs.edit().putInt(LyricsOverlayService.PREF_CONTEXT_BEFORE, value).apply()
                settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_CONTEXT)
            }
        },
        {
            SettingsCountItem("后文行数", after) { value ->
                prefs.edit().putInt(LyricsOverlayService.PREF_CONTEXT_AFTER, value).apply()
                settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_CONTEXT)
            }
        },
    ))
    if (showColorPicker) SettingsColorDialog(if (pickingGradientEnd) "渐变终点颜色" else "歌词颜色",
        if (pickingGradientEnd) gradientEnd else color, onDismiss = { showColorPicker = false }, onApply = {
            if (pickingGradientEnd) {
                prefs.edit().putString(OverlayAppearance.PREF_GRADIENT_END, it).apply()
                refreshAppearance()
            } else saveColor(it)
        })
}

@Composable
private fun SettingsCountItem(title: String, count: Int, onChange: (Int) -> Unit) {
    SettingsItem(
        headlineContent = { Text(title) },
        supportingContent = { Text("显示 $count 行") },
        leadingContent = { SettingsLeadingIcon(Icons.Default.Lyrics) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChange(count - 1) }, enabled = count > 0) {
                    Icon(Icons.Default.Remove, "减少$title")
                }
                Text("$count", textAlign = TextAlign.Center)
                IconButton(onClick = { onChange(count + 1) }, enabled = count < 2) {
                    Icon(Icons.Default.Add, "增加$title")
                }
            }
        },
    )
}
