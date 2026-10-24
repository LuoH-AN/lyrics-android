package com.luoh.music.lrc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import com.luoh.music.lrc.ui.agora.AgoraDropdownMenu
import com.luoh.music.lrc.ui.agora.AgoraDropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.luoh.music.lrc.ui.agora.ColorSchemePreset
import com.luoh.music.lrc.ui.agora.SchemeStyle
import com.luoh.music.lrc.ui.agora.ThemeMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luoh.music.lrc.ui.agora.CollapsingSettingsScaffold
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.LocalAgoraMotionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.roundToInt

/** Business settings rendered directly by Agora's Compose scaffolds and grouped rows. */
@Composable
internal fun SettingsCategoryContent(
    category: String,
    onNavigate: (ComposeRoute) -> Unit,
    onBack: () -> Unit,
) {
    val normalizedCategory = category.uppercase(java.util.Locale.ROOT)
    val context = LocalContext.current
    val overlayPrefs = remember(context) {
        context.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val appPrefs = remember(context) {
        context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE)
    }
    val alignmentPrefs = remember(context) {
        context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE)
    }
    val motionPrefs = remember(context) {
        context.getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE)
    }
    val revision = settingsPreferenceRevision(overlayPrefs, appPrefs, alignmentPrefs, motionPrefs)
    // Reading this state keeps business values current when the service or another page changes them.
    val title = remember(normalizedCategory) {
        when (normalizedCategory) {
            "ACCESS" -> "桌面歌词"
            "OVERLAY" -> "桌面歌词外观"
            "SYNC" -> "歌词同步"
            "LYRICS" -> "歌词与翻译"
            "APPEARANCE" -> "应用外观"
            "HOME" -> "主页歌词"
            "MOTION" -> "主页歌词动效"
            "ABOUT" -> "关于"
            else -> "设置"
        }
    }
    CollapsingSettingsScaffold(title = title, onBack = onBack) {
        when (normalizedCategory) {
            "ACCESS" -> DesktopAccessSettings(context)
            "OVERLAY" -> DesktopAppearanceSettings(context, overlayPrefs, revision)
            "SYNC" -> LyricsSyncSettings(context, overlayPrefs, revision, onNavigate)
            "LYRICS" -> LyricsContentSettings(context, overlayPrefs, revision, onNavigate)
            "APPEARANCE" -> AppAppearanceSettings(context, appPrefs, revision)
            "HOME" -> HomeAlignmentSettings(context, revision)
            "MOTION" -> HomeMotionSettings(context, revision)
            "ABOUT" -> AboutSettings(context, onNavigate)
        }
    }
}

@Composable
private fun rememberSettingsBusinessScope(): CoroutineScope {
    val scope = rememberCoroutineScope()
    val active = LocalSettingsScrollRetentionActive.current
    LaunchedEffect(active) {
        if (!active) scope.coroutineContext.cancelChildren()
    }
    return scope
}

@Composable
private fun settingsPreferenceRevision(vararg prefs: SharedPreferences): Int {
    var revision by remember { mutableIntStateOf(0) }
    val active = LocalSettingsScrollRetentionActive.current
    DisposableEffect(prefs.toList(), active) {
        if (!active) return@DisposableEffect onDispose { }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.forEach { it.registerOnSharedPreferenceChangeListener(listener) }
        revision++
        onDispose { prefs.forEach { it.unregisterOnSharedPreferenceChangeListener(listener) } }
    }
    return revision
}

@Composable
private fun SettingsLeadingIcon(icon: ImageVector) {
    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
}

@Composable
private fun SettingsNavigationItem(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { SettingsLeadingIcon(icon) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    summary: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { SettingsLeadingIcon(icon) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.alpha(if (enabled) 1f else 0.38f)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
private fun SettingsChoiceItem(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    icon: ImageVector,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: options.first().second
    SettingsItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(label) },
        leadingContent = { SettingsLeadingIcon(icon) },
        trailingContent = {
            Box {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                AgoraDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 16.dp,
                ) {
                    options.forEach { (value, text) ->
                        AgoraDropdownMenuItem(
                            text = { Text(text) },
                            leadingIcon = {
                                if (value == selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = { expanded = false; onSelect(value) },
                        )
                    }
                }
            }
        },
        modifier = Modifier.alpha(if (enabled) 1f else 0.38f).clickable(enabled = enabled) { expanded = true },
    )
}

/** Commit slider changes on release; dragging updates the display without flooding service intents. */
@Composable
private fun SettingsSliderItem(
    title: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    unit: String = "%",
    icon: ImageVector,
    enabled: Boolean = true,
    onCommit: (Int) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        SettingsItem(
            headlineContent = { Text(title) },
            leadingContent = { SettingsLeadingIcon(icon) },
            trailingContent = { Text("${draft.roundToInt()}$unit", color = MaterialTheme.colorScheme.primary) },
        )
        Slider(
            value = draft.coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            enabled = enabled,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = title },
        )
    }
}

@Composable
private fun DesktopAccessSettings(context: Context) {
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
    val running = remember(revision) { LyricsOverlayService.isRunning }
    val notificationAllowed = remember(revision) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val overlayAllowed = remember(revision) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }
    val scope = rememberSettingsBusinessScope()
    SettingsGroup(title = "桌面歌词", items = listOf(
        {
            SettingsSwitchItem("桌面歌词", "在其他应用上显示当前播放的歌词", Icons.Default.Lyrics, running) {
                when {
                    running -> context.stopService(Intent(context, LyricsOverlayService::class.java))
                    !notificationAllowed -> {
                        settingsToast(context, "请先授予通知使用权")
                        settingsOpenIntent(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    !overlayAllowed -> {
                        settingsToast(context, "请先允许显示悬浮窗")
                        settingsOpenIntent(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    }
                    else -> ContextCompat.startForegroundService(context, Intent(context, LyricsOverlayService::class.java).setAction(LyricsOverlayService.ACTION_START))
                }
                scope.launch { delay(250); revision++ }
            }
        },
        {
            SettingsNavigationItem("通知使用权", if (notificationAllowed) "已授权" else "去开启", Icons.Default.Notifications) {
                settingsOpenIntent(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        },
        {
            SettingsNavigationItem("悬浮窗权限", if (overlayAllowed) "已授权" else "去开启", Icons.Default.Layers) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    settingsOpenIntent(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }
            }
        },
    ))
}

@Composable
private fun DesktopAppearanceSettings(context: Context, prefs: SharedPreferences, revision: Int) {
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
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    fun saveColor(hex: String) {
        prefs.edit().putString(LyricsOverlayService.PREF_LYRIC_COLOR_COMPACT, hex).apply()
        settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_LYRIC_COLOR) {
            putExtra(LyricsOverlayService.EXTRA_LYRIC_COLOR, hex)
            putExtra(LyricsOverlayService.EXTRA_TARGET_COMPACT, true)
        }
    }
    SettingsGroup(title = "歌词预览", items = listOf({
        val lyricColor = remember(color) { settingsParseColor(color) }
        val previewBackground = Color(OverlayAppearance.backgroundColor(background))
        Column(
            Modifier.fillMaxWidth().padding(16.dp).clip(MaterialTheme.shapes.large)
                .background(previewBackground).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val lines = listOf("City lights" to "街灯轻轻亮起", "A quiet melody" to "旋律停在耳边", "Stay with me" to "让歌词陪着你", "One more song" to "再听一首歌", "Until tomorrow" to "直到明天")
            for (index in (2 - before)..(2 + after)) {
                val (original, translated) = lines[index]
                val foreground = lyricColor.copy(alpha = if (index == 2) 1f else .5f)
                Column {
                    if (translation != LyricsOverlayService.TRANSLATION_TRANSLATED) {
                        Text(original, color = foreground, fontSize = (20f * fontScale / 100).sp, fontWeight = FontWeight(fontWeight))
                    }
                    if (translation != LyricsOverlayService.TRANSLATION_ORIGINAL) {
                        Text(translated, color = foreground, fontSize = (if (translation == LyricsOverlayService.TRANSLATION_TRANSLATED) 20f else 15f).times(fontScale / 100f).sp,
                            fontWeight = FontWeight(fontWeight))
                    }
                }
            }
        }
    }))
    SettingsGroup(title = "文字与背景", items = listOf(
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
                modifier = Modifier.clickable { showColorPicker = true },
            )
        },
    ))
    SettingsGroup(title = "上下文", items = listOf(
        {
            SettingsCountItem("上文", before) { value ->
                prefs.edit().putInt(LyricsOverlayService.PREF_CONTEXT_BEFORE, value).apply()
                settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_CONTEXT)
            }
        },
        {
            SettingsCountItem("下文", after) { value ->
                prefs.edit().putInt(LyricsOverlayService.PREF_CONTEXT_AFTER, value).apply()
                settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_CONTEXT)
            }
        },
    ))
    if (showColorPicker) SettingsColorDialog("歌词颜色", color, onDismiss = { showColorPicker = false }, onApply = ::saveColor)
}

@Composable
private fun SettingsCountItem(title: String, count: Int, onChange: (Int) -> Unit) {
    SettingsItem(
        headlineContent = { Text(title) },
        supportingContent = { Text("显示 $count 句") },
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

@Composable
private fun LyricsSyncSettings(context: Context, prefs: SharedPreferences, revision: Int, onNavigate: (ComposeRoute) -> Unit) {
    val identity = remember(revision) { prefs.getString(LyricsOverlayService.PREF_ACTIVE_LYRIC_IDENTITY, "").orEmpty() }
    val source = remember(revision) { prefs.getString(LyricsOverlayService.PREF_ACTIVE_LYRIC_SOURCE, "").orEmpty() }
    val available = identity.isNotBlank() && source.isNotBlank()
    var offset by remember(identity, source) {
        mutableIntStateOf(if (available) prefs.getInt(LyricsOverlayService.lyricOffsetPreferenceKey(identity, source), 0)
            .coerceIn(LyricsOverlayService.LYRIC_OFFSET_MIN_MS, LyricsOverlayService.LYRIC_OFFSET_MAX_MS) else 0)
    }
    var pendingWrites by remember(identity, source) { mutableIntStateOf(0) }
    val screenActive = LocalSettingsScrollRetentionActive.current
    LaunchedEffect(screenActive) { if (!screenActive) pendingWrites = 0 }
    LaunchedEffect(revision, identity, source) {
        if (pendingWrites == 0) {
            offset = if (available) prefs.getInt(LyricsOverlayService.lyricOffsetPreferenceKey(identity, source), 0)
                .coerceIn(LyricsOverlayService.LYRIC_OFFSET_MIN_MS, LyricsOverlayService.LYRIC_OFFSET_MAX_MS) else 0
        }
    }
    val scope = rememberSettingsBusinessScope()
    val offsetWrites = remember { Mutex() }
    fun saveOffset(value: Int) {
        if (!available) return
        val normalized = value.coerceIn(LyricsOverlayService.LYRIC_OFFSET_MIN_MS, LyricsOverlayService.LYRIC_OFFSET_MAX_MS)
        // Capture metadata now, and keep the immediate value independent of service dispatch timing.
        val title = prefs.getString(LyricsOverlayService.PREF_ACTIVE_LYRIC_TITLE, "").orEmpty()
        val artist = prefs.getString(LyricsOverlayService.PREF_ACTIVE_LYRIC_ARTIST, "").orEmpty()
        offset = normalized
        pendingWrites++
        prefs.edit().putInt(LyricsOverlayService.lyricOffsetPreferenceKey(identity, source), normalized)
            .putInt(LyricsOverlayService.PREF_LYRIC_OFFSET_MS, normalized).apply()
        scope.launch {
            try {
                offsetWrites.withLock {
                    // Superseded values need no extra JSON/index work or service refresh.
                    if (normalized == offset) {
                        withContext(Dispatchers.IO) {
                            LyricsOverlayService.writeLyricOffsetMemory(prefs, identity, source, title, artist, normalized)
                        }
                        settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_LYRIC_OFFSET) {
                            putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MS, normalized)
                            putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MEMORY_KEY, LyricsOverlayService.lyricOffsetPreferenceKey(identity, source))
                        }
                    }
                }
            } finally {
                pendingWrites = (pendingWrites - 1).coerceAtLeast(0)
            }
        }
    }
    SettingsGroup(title = "当前歌词", items = listOf({
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            SettingsItem(
                headlineContent = { Text(LyricSyncText.format(offset)) },
                supportingContent = { Text(if (available) "每次调整 100ms，自动记住这首歌的歌词同步" else "开始播放并匹配歌词后，可调整同步") },
                leadingContent = { SettingsLeadingIcon(Icons.Default.Sync) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { saveOffset(offset - 100) }, enabled = available && offset > LyricsOverlayService.LYRIC_OFFSET_MIN_MS,
                    modifier = Modifier.weight(1f)) { Text("延后") }
                TextButton(onClick = { saveOffset(0) }, enabled = available && offset != 0,
                    modifier = Modifier.weight(1f)) { Text("重置") }
                TextButton(onClick = { saveOffset(offset + 100) }, enabled = available && offset < LyricsOverlayService.LYRIC_OFFSET_MAX_MS,
                    modifier = Modifier.weight(1f)) { Text("提前") }
            }
        }
    }))
    SettingsGroup(title = "同步记忆", items = listOf({
        SettingsNavigationItem("同步记忆管理", "查看或清除已记住的单曲偏移", Icons.Default.Timeline) { onNavigate(ComposeRoute("offsets")) }
    }))
}

@Composable
private fun LyricsContentSettings(context: Context, prefs: SharedPreferences, revision: Int, onNavigate: (ComposeRoute) -> Unit) {
    val translation = remember(revision) {
        prefs.getString(LyricsOverlayService.PREF_TRANSLATION_MODE, LyricsOverlayService.TRANSLATION_BILINGUAL).orEmpty()
    }
    SettingsGroup(title = "歌词显示", items = listOf({
        SettingsChoiceItem("翻译显示", translation, listOf(
            LyricsOverlayService.TRANSLATION_ORIGINAL to "原文",
            LyricsOverlayService.TRANSLATION_BILINGUAL to "双语",
            LyricsOverlayService.TRANSLATION_TRANSLATED to "译文",
        ), Icons.Default.Translate) { value ->
            prefs.edit().putString(LyricsOverlayService.PREF_TRANSLATION_MODE, value).apply()
            settingsOverlayAction(context, LyricsOverlayService.ACTION_SET_TRANSLATION_MODE) { putExtra(LyricsOverlayService.EXTRA_TRANSLATION_MODE, value) }
        }
    }))
    SettingsGroup(title = "歌词管理", items = listOf(
        { SettingsNavigationItem("补充翻译", "翻译服务、模型与配置", Icons.Default.Translate) { onNavigate(ComposeRoute("translation")) } },
        { SettingsNavigationItem("自定义歌词", "导入和编辑本地歌词", Icons.Default.Lyrics) { onNavigate(ComposeRoute("custom")) } },
        { SettingsNavigationItem("歌词源管理", "管理匹配记录与歌词版本", Icons.Default.Link) { onNavigate(ComposeRoute("sources")) } },
    ))
}

@Composable
private fun AppAppearanceSettings(context: Context, prefs: SharedPreferences, revision: Int) {
    val appearance = remember(prefs, revision) { ThemePrefs.read(prefs) }
    val theme = when (appearance.mode) {
        ThemeMode.LIGHT -> ThemePrefs.LIGHT
        ThemeMode.DARK -> ThemePrefs.DARK
        ThemeMode.FOLLOW_DEVICE -> ThemePrefs.FOLLOW
    }
    val accent = remember(revision) { AccentPrefs.customColor(context)?.let(::settingsHexColor) }
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicActive = dynamicSupported && appearance.dynamicColor
    val presets = listOf(
        ColorSchemePreset.OCEAN to "海洋", ColorSchemePreset.MIDNIGHT to "午夜",
        ColorSchemePreset.NORDIC to "北欧", ColorSchemePreset.FOREST to "森林",
        ColorSchemePreset.SUNSET to "日落", ColorSchemePreset.ROSE to "玫瑰",
        ColorSchemePreset.LAVENDER to "薰衣草", ColorSchemePreset.SLATE to "岩灰",
    )
    val styles = listOf(
        SchemeStyle.NEUTRAL to "中性", SchemeStyle.TONAL_SPOT to "柔和",
        SchemeStyle.EXPRESSIVE to "表现力", SchemeStyle.VIBRANT to "鲜艳",
        SchemeStyle.MONOCHROME to "黑白灰",
    )
    val presetLabel = presets.first { it.first == appearance.preset }.second
    val primary = MaterialTheme.colorScheme.primary
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(title = "主题与颜色", items = listOf(
        {
            SettingsChoiceItem("主题", theme, listOf(ThemePrefs.FOLLOW to "跟随系统", ThemePrefs.LIGHT to "浅色", ThemePrefs.DARK to "深色"), Icons.Default.SettingsBrightness) {
                prefs.edit().putString(ThemePrefs.KEY, it).apply()
            }
        },
        {
            SettingsSwitchItem("AMOLED", "浅色主题使用纯白背景，深色主题使用纯黑背景",
                Icons.Default.Contrast, appearance.amoled) {
                prefs.edit().putBoolean(ThemePrefs.KEY_AMOLED, it).apply()
            }
        },
        {
            SettingsSwitchItem("动态颜色", "根据壁纸使用系统生成的颜色（Android 12+）",
                Icons.Default.ColorLens, appearance.dynamicColor, enabled = dynamicSupported) {
                prefs.edit().putBoolean(ThemePrefs.KEY_DYNAMIC_COLOR, it).apply()
            }
        },
        {
            SettingsChoiceItem("配色方案", if (accent != null) "custom" else appearance.preset.name,
                (if (accent != null) listOf("custom" to "自定义") else emptyList()) +
                    presets.map { it.first.name to it.second },
                Icons.Default.Palette, enabled = !dynamicActive) { value ->
                if (value != "custom") {
                    prefs.edit().putString(ThemePrefs.KEY_COLOR_SCHEME, value).remove(AccentPrefs.KEY).apply()
                    settingsOverlayAction(context, LyricsOverlayService.ACTION_REFRESH_ACCENT)
                }
            }
        },
        {
            SettingsChoiceItem("方案风格", appearance.style.name, styles.map { it.first.name to it.second },
                Icons.Default.Style, enabled = !dynamicActive) {
                prefs.edit().putString(ThemePrefs.KEY_SCHEME_STYLE, it).apply()
            }
        },
        {
            SettingsItem(
                headlineContent = { Text("主调颜色") },
                supportingContent = { Text(if (dynamicActive) "跟随动态颜色" else accent ?: presetLabel) },
                leadingContent = { SettingsLeadingIcon(Icons.Default.Palette) },
                trailingContent = { Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) },
                modifier = Modifier.alpha(if (dynamicActive) 0.38f else 1f)
                    .clickable(enabled = !dynamicActive) { showColorPicker = true },
            )
        },
    ))
    if (showColorPicker) SettingsColorDialog(
        title = "主调颜色",
        initialColor = accent ?: settingsHexColor(primary.toArgb()),
        onDismiss = { showColorPicker = false },
        onReset = {
            AccentPrefs.clear(context)
            settingsOverlayAction(context, LyricsOverlayService.ACTION_REFRESH_ACCENT)
        },
        onApply = {
            AccentPrefs.set(context, it)
            settingsOverlayAction(context, LyricsOverlayService.ACTION_REFRESH_ACCENT)
        },
    )
}

@Composable
private fun HomeAlignmentSettings(context: Context, revision: Int) {
    val alignment = remember(revision) { HomeLyricsPrefs.alignment(context) }
    val albumBackground = remember(revision) { HomeLyricsPrefs.albumBackground(context) }
    SettingsGroup(title = "背景", items = listOf({
        SettingsSwitchItem("专辑流动背景", "从封面取色，播放时缓慢流动", Icons.Default.BlurOn, albumBackground) {
            HomeLyricsPrefs.saveAlbumBackground(context, it)
        }
    }))
    SettingsGroup(title = "歌词布局", items = listOf({
        SettingsChoiceItem("对齐方式", alignment, listOf(HomeLyricsPrefs.LEFT to "左对齐", HomeLyricsPrefs.CENTER to "居中", HomeLyricsPrefs.RIGHT to "右对齐"),
            Icons.Default.FormatAlignLeft) { HomeLyricsPrefs.save(context, it) }
    }))
}

@Composable
private fun HomeMotionSettings(context: Context, revision: Int) {
    val style = remember(revision) { HomeMotionPrefs.style(context) }
    val appPrefs = remember(context) { context.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE) }
    val reduceMotion = remember(revision) { appPrefs.getBoolean("reduce_motion", false) }
    fun save(transform: (MotionStyle) -> MotionStyle) { HomeMotionPrefs.save(context, transform(HomeMotionPrefs.style(context))) }
    SettingsGroup(title = "应用动效", items = listOf({
        SettingsSwitchItem("减少动效", "减少页面切换和弹出界面的运动", Icons.Default.MotionPhotosOff, reduceMotion) {
            appPrefs.edit().putBoolean("reduce_motion", it).apply()
        }
    }))
    SettingsGroup(title = "动效预览", items = listOf({ ComposeMotionPreview(style) }))
    SettingsGroup(title = "滚动与错位", items = listOf(
        { SettingsSwitchItem("缓动滚动", "让歌词切行沿平滑曲线滚动", Icons.Default.Speed, style.easedScroll) { value -> save { it.copy(easedScroll = value) } } },
        { SettingsSliderItem("切行时长", style.scrollDurationMs, MotionStyle.DURATION_MIN..MotionStyle.DURATION_MAX, MotionStyle.DURATION_STEP,
            "ms", Icons.Default.Speed, style.easedScroll) { value -> save { it.copy(scrollDurationMs = value) } } },
        { SettingsSwitchItem("逐行错位", "相邻歌词按顺序跟随当前行", Icons.Default.Layers, style.stagger) { value -> save { it.copy(stagger = value) } } },
        { SettingsSliderItem("错位延迟", style.staggerDelayMs, MotionStyle.STAGGER_MIN..MotionStyle.STAGGER_MAX, MotionStyle.STAGGER_STEP,
            "ms", Icons.Default.Layers, style.stagger) { value -> save { it.copy(staggerDelayMs = value) } } },
    ))
    SettingsGroup(title = "缩放与模糊", items = listOf(
        { SettingsSwitchItem("行间缩放", "突出当前演唱的歌词", Icons.Default.FormatSize, style.scale) { value -> save { it.copy(scale = value) } } },
        { SettingsSliderItem("缩放强度", style.scalePercent, MotionStyle.PERCENT_MIN..MotionStyle.PERCENT_MAX, MotionStyle.PERCENT_STEP,
            "%", Icons.Default.FormatSize, style.scale) { value -> save { it.copy(scalePercent = value) } } },
        { SettingsSwitchItem("远景模糊", "柔化离当前行较远的歌词", Icons.Default.BlurOn, style.depthBlur) { value -> save { it.copy(depthBlur = value) } } },
        { SettingsSliderItem("模糊强度", style.blurPercent, MotionStyle.PERCENT_MIN..MotionStyle.PERCENT_MAX, MotionStyle.PERCENT_STEP,
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

@Composable
private fun AboutSettings(context: Context, onNavigate: (ComposeRoute) -> Unit) {
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty().substringBefore('-').ifBlank { "1.0" } }
            .getOrDefault("1.0")
    }
    var checking by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    val active = LocalSettingsScrollRetentionActive.current
    LaunchedEffect(active) { if (!active) { checking = false; release = null } }
    val scope = rememberSettingsBusinessScope()
    SettingsGroup(title = "应用", items = listOf(
        {
            SettingsNavigationItem("检查更新", if (checking) "检查中…" else version, Icons.Default.Update, !checking) {
                checking = true
                scope.launch {
                    val result = UpdateChecker.fetchLatest(version)
                    checking = false
                    result.onSuccess {
                        if (UpdateChecker.isNewer(it.version, version)) release = it else settingsToast(context, "当前已是最新版")
                    }.onFailure {
                        settingsToast(context, if (it is UpdateChecker.NoReleaseException) "还没有发布版本" else "暂时无法检查更新，请稍后重试")
                    }
                }
            }
        },
        { SettingsNavigationItem("维护者 · LuoH-AN", icon = Icons.Default.Person) { settingsOpenUrl(context, "https://github.com/LuoH-AN") } },
        { SettingsNavigationItem("项目主页 · GitHub", icon = Icons.Default.Link) { settingsOpenUrl(context, "https://github.com/LuoH-AN/lyrics-android") } },
        { SettingsNavigationItem("隐私说明", "权限与数据使用", Icons.Default.Lock) { onNavigate(ComposeRoute("privacy")) } },
    ))
    release?.let { update ->
        AlertDialog(
            onDismissRequest = { release = null },
            title = { Text("发现新版本 ${update.version}") },
            text = { Text(update.summary) },
            confirmButton = { TextButton(onClick = { release = null; settingsOpenUrl(context, update.pageUrl) }) { Text("前往下载") } },
            dismissButton = { TextButton(onClick = { release = null }) { Text("稍后") } },
        )
    }
}

@Composable
private fun SettingsColorDialog(
    title: String,
    initialColor: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var draft by rememberSaveable(title) { mutableStateOf(initialColor) }
    val normalized = draft.trim().removePrefix("#").uppercase()
    val valid = normalized.matches(Regex("[0-9A-F]{6}"))
    val presets = listOf("#FFFFFF", "#9FD8FF", "#111111", "#FFB6D5", "#3F683D", "#B9CCB3")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { hex ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(settingsParseColor(hex))
                                .clickable { draft = hex }.semantics { contentDescription = "颜色 $hex" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (hex.equals("#$normalized", true)) Icon(Icons.Default.Check, null,
                                tint = if (hex == "#111111" || hex == "#3F683D") Color.White else Color.Black,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(7) },
                    label = { Text("十六进制颜色") },
                    supportingText = { Text("输入 6 位颜色值，例如 #9FD8FF") },
                    isError = !valid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.ColorLens, null, tint = if (valid) settingsParseColor("#$normalized") else MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply("#$normalized"); onDismiss() }, enabled = valid) { Text("应用") }
        },
        dismissButton = {
            Row {
                if (onReset != null) TextButton(onClick = { onReset(); onDismiss() }) { Text("恢复森林") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

private fun settingsOverlayAction(context: Context, action: String, extras: Intent.() -> Unit = {}) {
    if (!LyricsOverlayService.isRunning) return
    runCatching { context.startService(Intent(context, LyricsOverlayService::class.java).setAction(action).apply(extras)) }
        .onFailure { settingsToast(context, "桌面歌词服务暂时不可用") }
}

private fun settingsOpenIntent(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }.onFailure { settingsToast(context, "无法打开此页面") }
}

private fun settingsOpenUrl(context: Context, url: String) = settingsOpenIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
private fun settingsToast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
private fun settingsHexColor(color: Int): String = "#%06X".format(color and 0xFFFFFF)
private fun settingsParseColor(hex: String): Color = Color(runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(android.graphics.Color.WHITE))
