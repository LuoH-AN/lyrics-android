package com.luoh.music.lrc

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/**
 * 独立设置页（黑白灰扁平纯边框分组列表）。
 * 承接原 MainActivity 里的全部设置逻辑；主页改为全屏歌词。
 */
class SettingsActivity : AppCompatActivity() {

    private val overlayPrefs by lazy {
        getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val appPrefs by lazy {
        getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE)
    }
    private val motionPrefs by lazy {
        getSharedPreferences(HomeMotionPrefs.PREFS, Context.MODE_PRIVATE)
    }

    private lateinit var listenerState: TextView
    private lateinit var overlayState: TextView
    private lateinit var overlayToggle: MaterialSwitch
    private var updatingOverlayToggle = false
    private val overlayStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updatePermissionStates()
    }
    private lateinit var themeFollow: MaterialButton
    private lateinit var themeLight: MaterialButton
    private lateinit var themeDark: MaterialButton
    private lateinit var motionFull: MaterialButton
    private lateinit var motionReduced: MaterialButton
    private lateinit var motionOff: MaterialButton
    private lateinit var backgroundModeTransparent: MaterialButton
    private lateinit var backgroundModeLow: MaterialButton
    private lateinit var backgroundModeHigh: MaterialButton
    private lateinit var seekFontSize: Slider
    private lateinit var fontSizeValue: TextView
    private lateinit var seekFontWeight: Slider
    private lateinit var fontWeightValue: TextView
    private lateinit var lyricOffsetValue: TextView
    private var colorPicker: LyricColorPickerDialog? = null
    private lateinit var offsetEarlier: MaterialButton
    private lateinit var offsetLater: MaterialButton
    private lateinit var offsetReset: MaterialButton
    private lateinit var overlayPreview: CompactLyricsView
    private var displayedOffsetMs = 0
    private val offsetPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == LyricsOverlayService.PREF_LYRIC_OFFSET_MS ||
            key == LyricsOverlayService.PREF_ACTIVE_LYRIC_IDENTITY ||
            key == LyricsOverlayService.PREF_ACTIVE_LYRIC_SOURCE ||
            key == LyricsOverlayService.PREF_LYRIC_OFFSET_INDEX) {
            updateLyricOffsetUi()
        }
    }
    private lateinit var lyricColorWhite: MaterialButton
    private lateinit var lyricColorBlue: MaterialButton
    private lateinit var lyricColorBlack: MaterialButton
    private lateinit var lyricColorPink: MaterialButton
    private lateinit var lyricColorCustom: MaterialButton
    private lateinit var translationOriginal: MaterialButton
    private lateinit var translationBilingual: MaterialButton
    private lateinit var translationTranslated: MaterialButton
    private lateinit var versionValue: TextView
    private lateinit var themeTransition: ThemeTransition
    private var appliedNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED
    private var pendingThemeScrollY: Int? = null
    private var pendingThemeFocusId = View.NO_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        bindContent()
        themeTransition = ThemeTransition(findViewById(android.R.id.content))
        ThemePrefs.updateSystemBars(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night == appliedNightMode || !::themeTransition.isInitialized) {
            super.onConfigurationChanged(newConfig)
            return
        }
        if (pendingThemeScrollY == null) {
            pendingThemeScrollY = findViewById<NestedScrollView>(R.id.settings_scroll).scrollY
            pendingThemeFocusId = currentFocus?.id ?: View.NO_ID
        }
        val colorDraft = colorPicker?.takeIf { it.isShowing }?.draft
        colorPicker?.dismiss()
        colorPicker = null
        themeTransition.capture()
        appliedNightMode = night
        super.onConfigurationChanged(newConfig)
        theme.applyStyle(R.style.Theme_DesktopLyrics, true)
        bindContent()
        if (colorDraft != null) showColorPickerDialog(colorDraft)
        ThemePrefs.updateSystemBars(this)
        themeTransition.finish {
            if (pendingThemeFocusId != View.NO_ID) findViewById<View>(pendingThemeFocusId)?.requestFocus()
            findViewById<NestedScrollView>(R.id.settings_scroll).scrollTo(0, pendingThemeScrollY ?: 0)
            pendingThemeScrollY = null
            pendingThemeFocusId = View.NO_ID
        }
    }

    override fun onDestroy() {
        colorPicker?.dismiss()
        colorPicker = null
        if (::themeTransition.isInitialized) themeTransition.dispose()
        super.onDestroy()
    }

    private fun bindContent() {
        setContentView(R.layout.activity_settings)

        findViewById<MaterialToolbar>(R.id.settings_toolbar).setNavigationOnClickListener { finish() }

        listenerState = findViewById(R.id.listener_permission_state)
        overlayState = findViewById(R.id.overlay_permission_state)
        overlayToggle = findViewById(R.id.overlay_toggle)
        themeFollow = findViewById(R.id.theme_follow)
        themeLight = findViewById(R.id.theme_light)
        themeDark = findViewById(R.id.theme_dark)
        motionFull = findViewById(R.id.home_motion_full)
        motionReduced = findViewById(R.id.home_motion_reduced)
        motionOff = findViewById(R.id.home_motion_off)
        backgroundModeTransparent = findViewById(R.id.background_mode_transparent)
        backgroundModeLow = findViewById(R.id.background_mode_low)
        backgroundModeHigh = findViewById(R.id.background_mode_high)
        seekFontSize = findViewById(R.id.seek_font_size)
        fontSizeValue = findViewById(R.id.font_size_value)
        seekFontWeight = findViewById(R.id.seek_font_weight)
        fontWeightValue = findViewById(R.id.font_weight_value)
        lyricOffsetValue = findViewById(R.id.lyric_offset_value)
        offsetEarlier = findViewById(R.id.offset_earlier)
        offsetLater = findViewById(R.id.offset_later)
        offsetReset = findViewById(R.id.offset_reset)
        overlayPreview = findViewById(R.id.overlay_preview)
        overlayPreview.setPlayback("preview", 10000L, 25000L, false, 1f)
        overlayPreview.setDocument(LyricDocument(listOf(
            LyricLine(0L, "City lights", "街灯轻轻亮起"),
            LyricLine(5000L, "A quiet melody", "旋律停在耳边"),
            LyricLine(10000L, "Stay with me", "让歌词陪着你"),
            LyricLine(15000L, "One more song", "再听一首歌"),
            LyricLine(20000L, "Until tomorrow", "直到明天")
        ), true))
        lyricColorWhite = findViewById(R.id.lyric_color_white)
        lyricColorBlue = findViewById(R.id.lyric_color_blue)
        lyricColorBlack = findViewById(R.id.lyric_color_black)
        lyricColorPink = findViewById(R.id.lyric_color_pink)
        lyricColorCustom = findViewById(R.id.lyric_color_custom)
        translationOriginal = findViewById(R.id.translation_original)
        translationBilingual = findViewById(R.id.translation_bilingual)
        translationTranslated = findViewById(R.id.translation_translated)
        versionValue = findViewById(R.id.version_value)
        versionValue.text = currentVersionName

        // 悬浮窗开关
        findViewById<View>(R.id.cell_overlay_toggle).setOnClickListener { toggleOverlay() }
        overlayToggle.setOnCheckedChangeListener { _, _ ->
            if (!updatingOverlayToggle) toggleOverlay()
        }
        // 权限
        findViewById<View>(R.id.cell_listener_permission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<View>(R.id.cell_overlay_permission).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        // 主题
        themeFollow.setOnClickListener { setTheme(ThemePrefs.FOLLOW) }
        themeLight.setOnClickListener { setTheme(ThemePrefs.LIGHT) }
        themeDark.setOnClickListener { setTheme(ThemePrefs.DARK) }

        // 主页歌词动效
        motionFull.setOnClickListener { setHomeMotionMode(HomeMotionPrefs.FULL) }
        motionReduced.setOnClickListener { setHomeMotionMode(HomeMotionPrefs.REDUCED) }
        motionOff.setOnClickListener { setHomeMotionMode(HomeMotionPrefs.OFF) }

        // 背景：透明 / 半透明 / 不透明
        backgroundModeTransparent.setOnClickListener { setBackgroundMode(LyricsOverlayService.BACKGROUND_TRANSPARENT) }
        backgroundModeLow.setOnClickListener { setBackgroundMode(LyricsOverlayService.BACKGROUND_LOW) }
        backgroundModeHigh.setOnClickListener { setBackgroundMode(LyricsOverlayService.BACKGROUND_HIGH) }

        // 字号
        seekFontSize.valueFrom = LyricsOverlayService.FONT_SCALE_MIN_PERCENT.toFloat()
        seekFontSize.valueTo = LyricsOverlayService.FONT_SCALE_MAX_PERCENT.toFloat()
        seekFontSize.stepSize = 1f
        seekFontSize.addOnChangeListener { _, value, fromUser ->
            val percent = value.toInt()
            fontSizeValue.text = "$percent%"
            if (fromUser) setFontScale(percent)
        }

        seekFontWeight.addOnChangeListener { _, value, fromUser ->
            if (fromUser) setFontWeight(OverlayAppearance.percentToWeight(value.toInt()))
        }

        // 正偏移让歌词提前，用自然语言操作，不要求用户理解正负号。
        offsetEarlier.setOnClickListener { setLyricOffset(displayedOffsetMs + 100) }
        offsetLater.setOnClickListener { setLyricOffset(displayedOffsetMs - 100) }
        offsetReset.setOnClickListener { setLyricOffset(0) }
        findViewById<View>(R.id.context_before_less).setOnClickListener { changeContext(true, -1) }
        findViewById<View>(R.id.context_before_more).setOnClickListener { changeContext(true, 1) }
        findViewById<View>(R.id.context_after_less).setOnClickListener { changeContext(false, -1) }
        findViewById<View>(R.id.context_after_more).setOnClickListener { changeContext(false, 1) }

        // 颜色
        listOf(
            lyricColorWhite to "#FFFFFF",
            lyricColorBlue to "#9FD8FF",
            lyricColorBlack to "#111111",
            lyricColorPink to "#FFB6D5"
        ).forEach { (option, color) -> option.setOnClickListener { setLyricColor(color) } }
        lyricColorCustom.setOnClickListener { showColorPickerDialog() }

        // 翻译
        translationOriginal.setOnClickListener { setTranslationMode(LyricsOverlayService.TRANSLATION_ORIGINAL) }
        translationBilingual.setOnClickListener { setTranslationMode(LyricsOverlayService.TRANSLATION_BILINGUAL) }
        translationTranslated.setOnClickListener { setTranslationMode(LyricsOverlayService.TRANSLATION_TRANSLATED) }

        // 管理
        findViewById<View>(R.id.cell_supplement_translation).setOnClickListener {
            startActivity(Intent(this, TranslationSettingsActivity::class.java))
        }
        findViewById<View>(R.id.cell_manage_sources).setOnClickListener {
            startActivity(Intent(this, LyricSourceManagerActivity::class.java))
        }
        findViewById<View>(R.id.cell_manage_offsets).setOnClickListener {
            startActivity(Intent(this, LyricOffsetMemoryActivity::class.java))
        }
        findViewById<View>(R.id.cell_custom_lyrics).setOnClickListener {
            startActivity(Intent(this, CustomLyricsManagerActivity::class.java))
        }
        // 关于
        findViewById<View>(R.id.cell_check_update).setOnClickListener { checkForUpdates() }
        findViewById<View>(R.id.cell_maintainer).setOnClickListener {
            openUrl("https://github.com/LuoH-AN")
        }
        findViewById<View>(R.id.cell_github).setOnClickListener {
            openUrl("https://github.com/LuoH-AN/lyrics-android")
        }
        findViewById<View>(R.id.cell_privacy).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("隐私说明")
                .setMessage(R.string.privacy_summary)
                .setPositiveButton("知道了", null)
                .show()
        }

        refreshAll()
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show() }
    }

    // ---------- 悬浮窗开关 ----------
    private fun toggleOverlay() {
        if (LyricsOverlayService.isRunning) {
            stopService(Intent(this, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_STOP
            })
            overlayToggle.postDelayed({ updatePermissionStates() }, 250)
            return
        }
        if (!hasNotificationListenerAccess()) {
            Toast.makeText(this, "请先授予通知使用权", Toast.LENGTH_SHORT).show()
            updatePermissionStates()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许显示悬浮窗", Toast.LENGTH_SHORT).show()
            updatePermissionStates()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            ); return
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_START
            }
        )
        overlayToggle.postDelayed({ updatePermissionStates() }, 250)
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        overlayPrefs.registerOnSharedPreferenceChangeListener(offsetPreferenceListener)
        ContextCompat.registerReceiver(
            this, overlayStateReceiver, IntentFilter(LyricsOverlayService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        overlayPrefs.unregisterOnSharedPreferenceChangeListener(offsetPreferenceListener)
        unregisterReceiver(overlayStateReceiver)
        super.onPause()
    }

    private fun refreshAll() {
        updatePermissionStates()
        updateThemeUi()
        updateHomeMotionUi()
        updateBackgroundModeUi()
        updateFontSizeUi()
        updateFontWeightUi()
        updateLyricOffsetUi()
        updateLyricColorUi()
        updateTranslationModeUi()
        updateContextUi()
        updateOverlayPreview()
    }

    // ---------- 主题 ----------
    private fun setTheme(mode: String) {
        if (appPrefs.getString(ThemePrefs.KEY, ThemePrefs.FOLLOW) == mode) return
        appPrefs.edit().putString(ThemePrefs.KEY, mode).apply()
        ThemePrefs.apply(mode)
        updateThemeUi()
    }

    private fun updateThemeUi() {
        val mode = appPrefs.getString(ThemePrefs.KEY, ThemePrefs.FOLLOW) ?: ThemePrefs.FOLLOW
        applySeg(
            listOf(
                themeFollow to ThemePrefs.FOLLOW,
                themeLight to ThemePrefs.LIGHT,
                themeDark to ThemePrefs.DARK
            ),
            mode
        )
    }

    // ---------- 主页歌词动效 ----------
    private fun setHomeMotionMode(mode: String) {
        if (HomeMotionPrefs.normalize(motionPrefs.getString(HomeMotionPrefs.KEY, null)) == mode) return
        motionPrefs.edit().putString(HomeMotionPrefs.KEY, mode).apply()
        updateHomeMotionUi()
    }

    private fun updateHomeMotionUi() {
        applySeg(
            listOf(
                motionFull to HomeMotionPrefs.FULL,
                motionReduced to HomeMotionPrefs.REDUCED,
                motionOff to HomeMotionPrefs.OFF
            ),
            HomeMotionPrefs.normalize(motionPrefs.getString(HomeMotionPrefs.KEY, null))
        )
    }

    // ---------- 权限状态 ----------
    private fun hasNotificationListenerAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun updatePermissionStates() {
        val listenerOk = hasNotificationListenerAccess()
        val overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        listenerState.text = if (listenerOk) "已授权" else "去开启"
        overlayState.text = if (overlayOk) "已授权" else "去开启"
        updatingOverlayToggle = true
        overlayToggle.isChecked = LyricsOverlayService.isRunning
        updatingOverlayToggle = false
    }

    // ---------- 小歌词窗上下文与预览 ----------
    private fun contextLines(before: Boolean): Int = overlayPrefs.getInt(
        if (before) LyricsOverlayService.PREF_CONTEXT_BEFORE else LyricsOverlayService.PREF_CONTEXT_AFTER,
        if (before) 0 else 1
    ).coerceIn(0, 2)

    private fun changeContext(before: Boolean, delta: Int) {
        val key = if (before) LyricsOverlayService.PREF_CONTEXT_BEFORE else LyricsOverlayService.PREF_CONTEXT_AFTER
        overlayPrefs.edit().putInt(key, (contextLines(before) + delta).coerceIn(0, 2)).apply()
        updateContextUi()
        updateOverlayPreview()
        if (LyricsOverlayService.isRunning) {
            startService(Intent(this, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_CONTEXT
            })
        }
    }

    private fun updateContextUi() {
        val before = contextLines(true)
        val after = contextLines(false)
        findViewById<TextView>(R.id.context_before_value).text = "$before 句"
        findViewById<TextView>(R.id.context_after_value).text = "$after 句"
        findViewById<View>(R.id.context_before_less).isEnabled = before > 0
        findViewById<View>(R.id.context_before_more).isEnabled = before < 2
        findViewById<View>(R.id.context_after_less).isEnabled = after > 0
        findViewById<View>(R.id.context_after_more).isEnabled = after < 2
    }

    private fun updateOverlayPreview() {
        val percent = overlayPrefs.getInt(fontScalePreferenceKey(), expandedFontScale())
            .coerceIn(LyricsOverlayService.FONT_SCALE_MIN_PERCENT, LyricsOverlayService.FONT_SCALE_MAX_PERCENT)
        val color = runCatching {
            Color.parseColor(overlayPrefs.getString(lyricColorPreferenceKey(), expandedLyricColor()))
        }.getOrDefault(Color.WHITE)
        val mode = overlayPrefs.getString(backgroundPreferenceKey(), expandedBackgroundMode())
        val translation = overlayPrefs.getString(LyricsOverlayService.PREF_TRANSLATION_MODE, LyricsOverlayService.TRANSLATION_BILINGUAL)
            ?: LyricsOverlayService.TRANSLATION_BILINGUAL
        overlayPreview.setAppearance(percent, color, translation, contextLines(true), contextLines(false), fontWeight())
        overlayPreview.background = OverlayAppearance.background(this, mode)
    }

    // ---------- 背景：透明 / 半透明 / 不透明 ----------
    private fun setBackgroundMode(mode: String) {
        val normalized = when (mode) {
            // 旧的 medium 归并到半透明(low)
            LyricsOverlayService.BACKGROUND_MEDIUM,
            LyricsOverlayService.BACKGROUND_LOW -> LyricsOverlayService.BACKGROUND_LOW
            LyricsOverlayService.BACKGROUND_HIGH -> LyricsOverlayService.BACKGROUND_HIGH
            else -> LyricsOverlayService.BACKGROUND_TRANSPARENT
        }
        overlayPrefs.edit().putString(backgroundPreferenceKey(), normalized).apply()
        updateBackgroundModeUi()
        updateOverlayPreview()
        if (LyricsOverlayService.isRunning) {
            startService(Intent(this, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_BACKGROUND
                putExtra(LyricsOverlayService.EXTRA_BACKGROUND_MODE, normalized)
                putExtra(LyricsOverlayService.EXTRA_TARGET_COMPACT, true)
            })
        }
    }

    private fun updateBackgroundModeUi() {
        val stored = overlayPrefs.getString(backgroundPreferenceKey(), expandedBackgroundMode())
        // 旧值 medium → 半透明(low)；未知 → 默认
        val effective = when (stored) {
            LyricsOverlayService.BACKGROUND_TRANSPARENT -> LyricsOverlayService.BACKGROUND_TRANSPARENT
            LyricsOverlayService.BACKGROUND_HIGH -> LyricsOverlayService.BACKGROUND_HIGH
            LyricsOverlayService.BACKGROUND_LOW,
            LyricsOverlayService.BACKGROUND_MEDIUM -> LyricsOverlayService.BACKGROUND_LOW
            else -> LyricsOverlayService.BACKGROUND_DEFAULT
        }
        applySeg(
            listOf(
                backgroundModeTransparent to LyricsOverlayService.BACKGROUND_TRANSPARENT,
                backgroundModeLow to LyricsOverlayService.BACKGROUND_LOW,
                backgroundModeHigh to LyricsOverlayService.BACKGROUND_HIGH
            ),
            effective
        )
    }

    // ---------- 字号 ----------
    private fun setFontScale(percent: Int) {
        val normalized = percent.coerceIn(
            LyricsOverlayService.FONT_SCALE_MIN_PERCENT,
            LyricsOverlayService.FONT_SCALE_MAX_PERCENT
        )
        overlayPrefs.edit().putInt(fontScalePreferenceKey(), normalized).apply()
        updateOverlayPreview()
        fontSizeValue.text = "$normalized%"
        if (LyricsOverlayService.isRunning) {
            startService(Intent(this, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_FONT_SCALE
                putExtra(LyricsOverlayService.EXTRA_FONT_SCALE_PERCENT, normalized)
                putExtra(LyricsOverlayService.EXTRA_TARGET_COMPACT, true)
            })
        }
    }

    private fun updateFontSizeUi() {
        val percent = overlayPrefs.getInt(fontScalePreferenceKey(), expandedFontScale()).coerceIn(
            LyricsOverlayService.FONT_SCALE_MIN_PERCENT,
            LyricsOverlayService.FONT_SCALE_MAX_PERCENT
        )
        fontSizeValue.text = "$percent%"
        seekFontSize.value = percent.toFloat()
    }

    private fun fontWeight(): Int = OverlayAppearance.normalizeWeight(
        overlayPrefs.getInt(LyricsOverlayService.PREF_FONT_WEIGHT, OverlayAppearance.DEFAULT_FONT_WEIGHT)
    )

    private fun setFontWeight(value: Int) {
        val weight = OverlayAppearance.normalizeWeight(value)
        overlayPrefs.edit().putInt(LyricsOverlayService.PREF_FONT_WEIGHT, weight).apply()
        updateFontWeightUi()
        updateOverlayPreview()
        if (LyricsOverlayService.isRunning) {
            startService(Intent(this, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_FONT_WEIGHT
                putExtra(LyricsOverlayService.EXTRA_FONT_WEIGHT, weight)
            })
        }
    }

    private fun updateFontWeightUi() {
        val percent = OverlayAppearance.weightToPercent(fontWeight())
        fontWeightValue.text = "$percent%"
        seekFontWeight.value = percent.toFloat()
    }

    // ---------- 歌词同步 ----------
    private fun setLyricOffset(value: Int) {
        val identity = overlayPrefs.getString(LyricsOverlayService.PREF_ACTIVE_LYRIC_IDENTITY, "").orEmpty()
        val source = overlayPrefs.getString(LyricsOverlayService.PREF_ACTIVE_LYRIC_SOURCE, "").orEmpty()
        if (identity.isBlank() || source.isBlank()) return
        val normalized = value.coerceIn(
            LyricsOverlayService.LYRIC_OFFSET_MIN_MS,
            LyricsOverlayService.LYRIC_OFFSET_MAX_MS
        )
        val title = overlayPrefs.getString(LyricsOverlayService.PREF_ACTIVE_LYRIC_TITLE, "").orEmpty()
        val artist = overlayPrefs.getString(LyricsOverlayService.PREF_ACTIVE_LYRIC_ARTIST, "").orEmpty()
        // 先保存这首歌的数值，快速连点不依赖服务消息的处理时机。
        LyricsOverlayService.writeLyricOffsetMemory(overlayPrefs, identity, source, title, artist, normalized)
        overlayPrefs.edit().putInt(LyricsOverlayService.PREF_LYRIC_OFFSET_MS, normalized).apply()
        showLyricOffset(normalized, true)
        if (LyricsOverlayService.isRunning) {
            startService(Intent(this, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_LYRIC_OFFSET
                putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MS, normalized)
                putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MEMORY_KEY,
                    LyricsOverlayService.lyricOffsetPreferenceKey(identity, source))
            })
        }
    }

    private fun updateLyricOffsetUi() {
        val identity = overlayPrefs.getString(LyricsOverlayService.PREF_ACTIVE_LYRIC_IDENTITY, "").orEmpty()
        val source = overlayPrefs.getString(LyricsOverlayService.PREF_ACTIVE_LYRIC_SOURCE, "").orEmpty()
        val available = identity.isNotBlank() && source.isNotBlank()
        val value = if (available) overlayPrefs.getInt(
            LyricsOverlayService.lyricOffsetPreferenceKey(identity, source), 0
        ) else 0
        showLyricOffset(value, available)
    }

    private fun showLyricOffset(value: Int, available: Boolean) {
        displayedOffsetMs = value.coerceIn(LyricsOverlayService.LYRIC_OFFSET_MIN_MS, LyricsOverlayService.LYRIC_OFFSET_MAX_MS)
        lyricOffsetValue.text = LyricSyncText.format(displayedOffsetMs)
        offsetEarlier.isEnabled = available && displayedOffsetMs < LyricsOverlayService.LYRIC_OFFSET_MAX_MS
        offsetLater.isEnabled = available && displayedOffsetMs > LyricsOverlayService.LYRIC_OFFSET_MIN_MS
        offsetReset.isEnabled = available && displayedOffsetMs != 0
    }

    // ---------- 颜色 ----------
    private fun setLyricColor(color: String) {
        overlayPrefs.edit().putString(lyricColorPreferenceKey(), color).apply()
        updateLyricColorUi()
        updateOverlayPreview()
        if (LyricsOverlayService.isRunning) {
            startService(Intent(this, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_LYRIC_COLOR
                putExtra(LyricsOverlayService.EXTRA_LYRIC_COLOR, color)
                putExtra(LyricsOverlayService.EXTRA_TARGET_COMPACT, true)
            })
        }
    }

    private fun updateLyricColorUi() {
        val selected = overlayPrefs.getString(lyricColorPreferenceKey(), expandedLyricColor()).orEmpty()
        val options = listOf(
            lyricColorWhite to "#FFFFFF",
            lyricColorBlue to "#9FD8FF",
            lyricColorBlack to "#111111",
            lyricColorPink to "#FFB6D5"
        )
        options.forEach { (option, color) ->
            val selectedColor = color.equals(selected, ignoreCase = true)
            option.isChecked = selectedColor
            option.strokeWidth = NativeUi.dp(this, if (selectedColor) 2 else 1)
            option.strokeColor = ColorStateList.valueOf(resolveColor(if (selectedColor) R.color.accent else R.color.app_line))
        }
        val isCustom = options.none { (_, color) -> color.equals(selected, ignoreCase = true) }
        lyricColorCustom.isChecked = isCustom
        lyricColorCustom.strokeWidth = NativeUi.dp(this, if (isCustom) 2 else 1)
        lyricColorCustom.strokeColor = ColorStateList.valueOf(resolveColor(if (isCustom) R.color.accent else R.color.app_line))
    }

    private fun resolveColor(res: Int): Int = ContextCompat.getColor(this, res)

    // ---------- 翻译 ----------
    private fun setTranslationMode(mode: String) {
        val normalized = when (mode) {
            LyricsOverlayService.TRANSLATION_ORIGINAL -> LyricsOverlayService.TRANSLATION_ORIGINAL
            LyricsOverlayService.TRANSLATION_TRANSLATED -> LyricsOverlayService.TRANSLATION_TRANSLATED
            else -> LyricsOverlayService.TRANSLATION_BILINGUAL
        }
        overlayPrefs.edit().putString(LyricsOverlayService.PREF_TRANSLATION_MODE, normalized).apply()
        updateTranslationModeUi()
        updateOverlayPreview()
        if (LyricsOverlayService.isRunning) {
            startService(Intent(this, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_TRANSLATION_MODE
                putExtra(LyricsOverlayService.EXTRA_TRANSLATION_MODE, normalized)
            })
        }
    }

    private fun updateTranslationModeUi() {
        val selected = overlayPrefs.getString(
            LyricsOverlayService.PREF_TRANSLATION_MODE,
            LyricsOverlayService.TRANSLATION_BILINGUAL
        ).orEmpty()
        applySeg(
            listOf(
                translationOriginal to LyricsOverlayService.TRANSLATION_ORIGINAL,
                translationBilingual to LyricsOverlayService.TRANSLATION_BILINGUAL,
                translationTranslated to LyricsOverlayService.TRANSLATION_TRANSLATED
            ),
            selected
        )
    }

    // MaterialButtonToggleGroup owns segment shape, ripple and accessibility state.
    private fun applySeg(options: List<Pair<MaterialButton, String>>, selected: String) {
        options.forEach { (option, value) -> option.isChecked = value == selected }
    }

    // ---------- 调色板 ----------
    private fun showColorPickerDialog(draft: String? = null) {
        colorPicker?.dismiss()
        colorPicker = LyricColorPickerDialog(this,
            overlayPrefs.getString(lyricColorPreferenceKey(), expandedLyricColor()).orEmpty(),
            draft, ::setLyricColor).also { it.show() }
    }

    // ---------- 更新检查 ----------
    private fun checkForUpdates() {
        versionValue.text = "检查中…"
        lifecycleScope.launch {
            val result = UpdateChecker.fetchLatest(currentVersionName)
            versionValue.text = currentVersionName
            result.onSuccess { release ->
                val newer = UpdateChecker.isNewer(release.version, currentVersionName)
                if (newer) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
                } else {
                    Toast.makeText(this@SettingsActivity, "当前已是最新版", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                val message = if (error is UpdateChecker.NoReleaseException) "还没有发布版本" else "暂时无法检查更新，请稍后重试"
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- 偏好 key ----------
    private fun backgroundPreferenceKey(): String = LyricsOverlayService.PREF_BACKGROUND_MODE_COMPACT

    private fun fontScalePreferenceKey(): String = LyricsOverlayService.PREF_FONT_SCALE_COMPACT_PERCENT

    private fun lyricColorPreferenceKey(): String = LyricsOverlayService.PREF_LYRIC_COLOR_COMPACT

    // 小窗尚无独立偏好时，沿用旧安装的外观。
    private fun expandedBackgroundMode(): String = overlayPrefs.getString(
        LyricsOverlayService.PREF_BACKGROUND_MODE,
        LyricsOverlayService.BACKGROUND_DEFAULT
    ).orEmpty().ifBlank { LyricsOverlayService.BACKGROUND_DEFAULT }

    private fun expandedFontScale(): Int = overlayPrefs.getInt(
        LyricsOverlayService.PREF_FONT_SCALE_PERCENT,
        LyricsOverlayService.FONT_SCALE_DEFAULT_PERCENT
    )

    private fun expandedLyricColor(): String = overlayPrefs.getString(
        LyricsOverlayService.PREF_LYRIC_COLOR,
        LyricsOverlayService.LYRIC_COLOR_DEFAULT
    ).orEmpty().ifBlank { LyricsOverlayService.LYRIC_COLOR_DEFAULT }

    private val currentVersionName: String
        get() = packageManager.getPackageInfo(packageName, 0).versionName
            .orEmpty().substringBefore('-').ifBlank { "1.0" }
}
