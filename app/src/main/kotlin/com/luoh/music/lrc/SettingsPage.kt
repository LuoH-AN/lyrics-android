package com.luoh.music.lrc

import android.content.Context
import com.luoh.music.lrc.SettingsActivity.Section
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
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.appbar.CollapsingToolbarLayout
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/**
 * Agora 风格设置入口；每个分类按需创建，避免一次加载整页控件。
 * 承接原 MainActivity 里的全部设置逻辑；主页改为全屏歌词。
 */
class SettingsPage : NativePage() {

    private val section by lazy {
        intent.getStringExtra("settings_section")?.let { value -> Section.values().firstOrNull { it.name == value } }
    }

    private val overlayPrefs by lazy {
        getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val appPrefs by lazy {
        getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE)
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
    private lateinit var accentColorValue: TextView
    private lateinit var homeAlignLeft: MaterialButton
    private lateinit var homeAlignCenter: MaterialButton
    private lateinit var homeAlignRight: MaterialButton
    private lateinit var motionEasedScroll: MaterialSwitch
    private lateinit var motionDuration: Slider
    private lateinit var motionDurationValue: TextView
    private lateinit var motionStagger: MaterialSwitch
    private lateinit var motionStaggerDelay: Slider
    private lateinit var motionStaggerValue: TextView
    private lateinit var motionScale: MaterialSwitch
    private lateinit var motionScaleStrength: Slider
    private lateinit var motionScaleValue: TextView
    private lateinit var motionDepthBlur: MaterialSwitch
    private lateinit var motionBlurStrength: Slider
    private lateinit var motionBlurValue: TextView
    private lateinit var motionPreview: HomeMotionPreview
    private var updatingMotionControls = false
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
    private var pendingHeaderOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        bindContent()
        themeTransition = ThemeTransition(pageRoot)
        ThemePrefs.updateSystemBars(uiContext)
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
            pendingHeaderOffset = findViewById<View>(R.id.settings_appbar).top
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
        ThemePrefs.updateSystemBars(uiContext)
        themeTransition.finish {
            if (pendingThemeFocusId != View.NO_ID) findViewById<View>(pendingThemeFocusId)?.requestFocus()
            findViewById<NestedScrollView>(R.id.settings_scroll).scrollTo(0, pendingThemeScrollY ?: 0)
            val appBar = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.settings_appbar)
            val params = appBar.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            (params.behavior as? com.google.android.material.appbar.AppBarLayout.Behavior)?.setTopAndBottomOffset(pendingHeaderOffset)
            (appBar.parent as androidx.coordinatorlayout.widget.CoordinatorLayout).dispatchDependentViewsChanged(appBar)
            pendingThemeScrollY = null
            pendingThemeFocusId = View.NO_ID
            pendingHeaderOffset = 0
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
        val content = findViewById<LinearLayout>(R.id.settings_content)
        findViewById<MaterialToolbar>(R.id.settings_toolbar).setNavigationOnClickListener { finish() }
        findViewById<CollapsingToolbarLayout>(R.id.settings_header).title = section?.title ?: "设置"
        if (section == null) {
            fun label(title: String) = NativeUi.text(uiContext, title, 13f).apply {
                setTextColor(AccentPrefs.accent(uiContext))
                setPadding(NativeUi.dp(context, 16), NativeUi.dp(context, 24), 0, NativeUi.dp(context, 12))
            }
            fun group(title: String, sections: List<Section>) {
                content.addView(label(title))
                content.addView(NativeUi.group(uiContext, sections.map { item ->
                    NativeUi.navigationRow(uiContext, item.title, item.summary, item.icon) {
                        startActivity(SettingsActivity.intent(uiContext, item))
                    }
                }))
            }
            group("歌词", listOf(Section.ACCESS, Section.OVERLAY, Section.SYNC, Section.LYRICS))
            group("显示", listOf(Section.APPEARANCE, Section.HOME, Section.MOTION))
            group("应用", listOf(Section.ABOUT))
            content.addView(NativeUi.navigationRow(uiContext, "隐私说明", "权限与数据使用", R.drawable.ic_home_library) {
                startActivity(Intent(uiContext, PrivacyActivity::class.java))
            }.apply { id = R.id.cell_privacy })
            return
        }
        layoutInflater.inflate(requireNotNull(section).layout, content, true)
        NativeUi.styleGroupRows(content.getChildAt(0) as android.view.ViewGroup)
        findViewById<TextView>(R.id.listener_permission_state)?.let { listenerState = it }
        findViewById<TextView>(R.id.overlay_permission_state)?.let { overlayState = it }
        findViewById<MaterialSwitch>(R.id.overlay_toggle)?.let { overlayToggle = it }
        findViewById<MaterialButton>(R.id.theme_follow)?.let { themeFollow = it }
        findViewById<MaterialButton>(R.id.theme_light)?.let { themeLight = it }
        findViewById<MaterialButton>(R.id.theme_dark)?.let { themeDark = it }
        findViewById<TextView>(R.id.accent_color_value)?.let { accentColorValue = it }
        findViewById<MaterialButton>(R.id.home_align_left)?.let { homeAlignLeft = it }
        findViewById<MaterialButton>(R.id.home_align_center)?.let { homeAlignCenter = it }
        findViewById<MaterialButton>(R.id.home_align_right)?.let { homeAlignRight = it }
        findViewById<MaterialSwitch>(R.id.home_motion_scroll_easing)?.let { motionEasedScroll = it }
        findViewById<Slider>(R.id.home_motion_scroll_duration)?.let { motionDuration = it }
        findViewById<TextView>(R.id.home_motion_scroll_value)?.let { motionDurationValue = it }
        findViewById<MaterialSwitch>(R.id.home_motion_stagger)?.let { motionStagger = it }
        findViewById<Slider>(R.id.home_motion_stagger_delay)?.let { motionStaggerDelay = it }
        findViewById<TextView>(R.id.home_motion_stagger_value)?.let { motionStaggerValue = it }
        findViewById<MaterialSwitch>(R.id.home_motion_scale)?.let { motionScale = it }
        findViewById<Slider>(R.id.home_motion_scale_strength)?.let { motionScaleStrength = it }
        findViewById<TextView>(R.id.home_motion_scale_value)?.let { motionScaleValue = it }
        findViewById<MaterialSwitch>(R.id.home_motion_depth_blur)?.let { motionDepthBlur = it }
        findViewById<Slider>(R.id.home_motion_blur_strength)?.let { motionBlurStrength = it }
        findViewById<TextView>(R.id.home_motion_blur_value)?.let { motionBlurValue = it }
        findViewById<HomeMotionPreview>(R.id.home_motion_preview)?.let { motionPreview = it }
        findViewById<MaterialButton>(R.id.background_mode_transparent)?.let { backgroundModeTransparent = it }
        findViewById<MaterialButton>(R.id.background_mode_low)?.let { backgroundModeLow = it }
        findViewById<MaterialButton>(R.id.background_mode_high)?.let { backgroundModeHigh = it }
        findViewById<Slider>(R.id.seek_font_size)?.let { seekFontSize = it }
        findViewById<TextView>(R.id.font_size_value)?.let { fontSizeValue = it }
        findViewById<Slider>(R.id.seek_font_weight)?.let { seekFontWeight = it }
        findViewById<TextView>(R.id.font_weight_value)?.let { fontWeightValue = it }
        findViewById<TextView>(R.id.lyric_offset_value)?.let { lyricOffsetValue = it }
        findViewById<MaterialButton>(R.id.offset_earlier)?.let { offsetEarlier = it }
        findViewById<MaterialButton>(R.id.offset_later)?.let { offsetLater = it }
        findViewById<MaterialButton>(R.id.offset_reset)?.let { offsetReset = it }
        findViewById<CompactLyricsView>(R.id.overlay_preview)?.let { overlayPreview = it }
        if (::overlayPreview.isInitialized) {
            overlayPreview.setPlayback("preview", 10000L, 25000L, false, 1f)
            overlayPreview.setDocument(LyricDocument(listOf(
            LyricLine(0L, "City lights", "街灯轻轻亮起"),
            LyricLine(5000L, "A quiet melody", "旋律停在耳边"),
            LyricLine(10000L, "Stay with me", "让歌词陪着你"),
            LyricLine(15000L, "One more song", "再听一首歌"),
            LyricLine(20000L, "Until tomorrow", "直到明天")
            ), true))
        }
        findViewById<MaterialButton>(R.id.lyric_color_white)?.let { lyricColorWhite = it }
        findViewById<MaterialButton>(R.id.lyric_color_blue)?.let { lyricColorBlue = it }
        findViewById<MaterialButton>(R.id.lyric_color_black)?.let { lyricColorBlack = it }
        findViewById<MaterialButton>(R.id.lyric_color_pink)?.let { lyricColorPink = it }
        findViewById<MaterialButton>(R.id.lyric_color_custom)?.let { lyricColorCustom = it }
        findViewById<MaterialButton>(R.id.translation_original)?.let { translationOriginal = it }
        findViewById<MaterialButton>(R.id.translation_bilingual)?.let { translationBilingual = it }
        findViewById<MaterialButton>(R.id.translation_translated)?.let { translationTranslated = it }
        findViewById<TextView>(R.id.version_value)?.let { versionValue = it }
        if (::versionValue.isInitialized) versionValue.text = currentVersionName


        if (section == Section.ACCESS) {
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

        }
        if (section == Section.APPEARANCE) {
            // 主题
            themeFollow.setOnClickListener { setTheme(ThemePrefs.FOLLOW) }
            themeLight.setOnClickListener { setTheme(ThemePrefs.LIGHT) }
            themeDark.setOnClickListener { setTheme(ThemePrefs.DARK) }
            findViewById<View>(R.id.cell_accent_color).setOnClickListener { showAccentPicker() }

        }
        if (section == Section.HOME) {
            // 主页歌词对齐
            homeAlignLeft.setOnClickListener { setHomeAlignment(HomeLyricsPrefs.LEFT) }
            homeAlignCenter.setOnClickListener { setHomeAlignment(HomeLyricsPrefs.CENTER) }
            homeAlignRight.setOnClickListener { setHomeAlignment(HomeLyricsPrefs.RIGHT) }

        }
        if (section == Section.MOTION) {
            // 主页歌词动效：每项效果独立开关 + 数值
            motionEasedScroll.setOnCheckedChangeListener { _, value ->
                saveMotion { it.copy(easedScroll = value) }
                motionDuration.isEnabled = value
            }
            motionStagger.setOnCheckedChangeListener { _, value ->
                saveMotion { it.copy(stagger = value) }
                motionStaggerDelay.isEnabled = value
            }
            motionScale.setOnCheckedChangeListener { _, value ->
                saveMotion { it.copy(scale = value) }
                motionScaleStrength.isEnabled = value
            }
            motionDepthBlur.setOnCheckedChangeListener { _, value ->
                saveMotion { it.copy(depthBlur = value) }
                motionBlurStrength.isEnabled = value
            }
            motionDuration.addOnChangeListener { _, value, fromUser ->
                motionDurationValue.text = "${value.toInt()}ms"
                if (fromUser) saveMotion { it.copy(scrollDurationMs = value.toInt()) }
            }
            motionStaggerDelay.addOnChangeListener { _, value, fromUser ->
                motionStaggerValue.text = "${value.toInt()}ms"
                if (fromUser) saveMotion { it.copy(staggerDelayMs = value.toInt()) }
            }
            motionScaleStrength.addOnChangeListener { _, value, fromUser ->
                motionScaleValue.text = "${value.toInt()}%"
                if (fromUser) saveMotion { it.copy(scalePercent = value.toInt()) }
            }
            motionBlurStrength.addOnChangeListener { _, value, fromUser ->
                motionBlurValue.text = "${value.toInt()}%"
                if (fromUser) saveMotion { it.copy(blurPercent = value.toInt()) }
            }

        }
        if (section == Section.OVERLAY) {
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

        }
        if (section == Section.SYNC) {
            // 正偏移让歌词提前，用自然语言操作，不要求用户理解正负号。
            offsetEarlier.setOnClickListener { setLyricOffset(displayedOffsetMs + 100) }
            offsetLater.setOnClickListener { setLyricOffset(displayedOffsetMs - 100) }
            offsetReset.setOnClickListener { setLyricOffset(0) }
        }
        if (section == Section.OVERLAY) {
            findViewById<View>(R.id.context_before_less).setOnClickListener { changeContext(true, -1) }
            findViewById<View>(R.id.context_before_more).setOnClickListener { changeContext(true, 1) }
            findViewById<View>(R.id.context_after_less).setOnClickListener { changeContext(false, -1) }
            findViewById<View>(R.id.context_after_more).setOnClickListener { changeContext(false, 1) }

        }
        if (section == Section.OVERLAY) {
            // 颜色
            listOf(
            lyricColorWhite to "#FFFFFF",
            lyricColorBlue to "#9FD8FF",
            lyricColorBlack to "#111111",
            lyricColorPink to "#FFB6D5"
            ).forEach { (option, color) -> option.setOnClickListener { setLyricColor(color) } }
            lyricColorCustom.setOnClickListener { showColorPickerDialog() }

        }
        if (section == Section.LYRICS) {
            // 翻译
            translationOriginal.setOnClickListener { setTranslationMode(LyricsOverlayService.TRANSLATION_ORIGINAL) }
            translationBilingual.setOnClickListener { setTranslationMode(LyricsOverlayService.TRANSLATION_BILINGUAL) }
            translationTranslated.setOnClickListener { setTranslationMode(LyricsOverlayService.TRANSLATION_TRANSLATED) }

        }
        // 管理
        findViewById<View>(R.id.cell_supplement_translation)?.setOnClickListener {
            startActivity(Intent(uiContext, TranslationSettingsActivity::class.java))
        }
        findViewById<View>(R.id.cell_manage_sources)?.setOnClickListener {
            startActivity(Intent(uiContext, LyricSourceManagerActivity::class.java))
        }
        findViewById<View>(R.id.cell_manage_offsets)?.setOnClickListener {
            startActivity(Intent(uiContext, LyricOffsetMemoryActivity::class.java))
        }
        findViewById<View>(R.id.cell_custom_lyrics)?.setOnClickListener {
            startActivity(Intent(uiContext, CustomLyricsManagerActivity::class.java))
        }
        if (section == Section.ABOUT) {
            // 关于
            findViewById<View>(R.id.cell_check_update).setOnClickListener { checkForUpdates() }
            findViewById<View>(R.id.cell_maintainer).setOnClickListener {
                openUrl("https://github.com/LuoH-AN")
            }
            findViewById<View>(R.id.cell_github).setOnClickListener {
                openUrl("https://github.com/LuoH-AN/lyrics-android")
            }
            findViewById<View>(R.id.cell_privacy).setOnClickListener {
                startActivity(Intent(uiContext, PrivacyActivity::class.java))
            }

        }
        refreshAll()
    }


    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(uiContext, "无法打开链接", Toast.LENGTH_SHORT).show() }
    }

    // ---------- 悬浮窗开关 ----------
    private fun toggleOverlay() {
        if (LyricsOverlayService.isRunning) {
            stopService(Intent(uiContext, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_STOP
            })
            overlayToggle.postDelayed({ updatePermissionStates() }, 250)
            return
        }
        if (!hasNotificationListenerAccess()) {
            Toast.makeText(uiContext, "请先授予通知使用权", Toast.LENGTH_SHORT).show()
            updatePermissionStates()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(uiContext)) {
            Toast.makeText(uiContext, "请先允许显示悬浮窗", Toast.LENGTH_SHORT).show()
            updatePermissionStates()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            ); return
        }
        ContextCompat.startForegroundService(
            uiContext,
            Intent(uiContext, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_START
            }
        )
        overlayToggle.postDelayed({ updatePermissionStates() }, 250)
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        if (::motionPreview.isInitialized) motionPreview.start()
        overlayPrefs.registerOnSharedPreferenceChangeListener(offsetPreferenceListener)
        ContextCompat.registerReceiver(
            uiContext, overlayStateReceiver, IntentFilter(LyricsOverlayService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        if (::motionPreview.isInitialized) motionPreview.stop()
        overlayPrefs.unregisterOnSharedPreferenceChangeListener(offsetPreferenceListener)
        unregisterReceiver(overlayStateReceiver)
        super.onPause()
    }

    private fun refreshAll() {
        updatePermissionStates()
        updateThemeUi()
        updateAccentUi()
        updateHomeAlignmentUi()
        updateHomeMotionUi()
        updateBackgroundModeUi()
        updateFontSizeUi()
        updateFontWeightUi()
        updateLyricOffsetUi()
        updateLyricColorUi()
        updateTranslationModeUi()
        updateContextUi()
        updateOverlayPreview()
        applySegmentAccents()
    }

    // ---------- 主题 ----------
    private fun setTheme(mode: String) {
        if (appPrefs.getString(ThemePrefs.KEY, ThemePrefs.FOLLOW) == mode) return
        appPrefs.edit().putString(ThemePrefs.KEY, mode).apply()
        ThemePrefs.apply(mode)
        updateThemeUi()
    }

    private fun updateThemeUi() {
        if (!::themeFollow.isInitialized) return
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

    // ---------- 主调颜色 ----------
    private fun updateAccentUi() {
        if (!::accentColorValue.isInitialized) return
        accentColorValue.text = AccentPrefs.customColor(uiContext)?.let(::hexColor) ?: "森林"
        accentColorValue.setTextColor(AccentPrefs.textAccent(uiContext, R.color.text_secondary))
    }

    private fun showAccentPicker() {
        LyricColorPickerDialog(
            context = uiContext,
            initialHex = hexColor(AccentPrefs.accent(uiContext)),
            title = "主调颜色",
            onReset = {
                AccentPrefs.clear(uiContext)
                refreshAccentUi()
            },
            onApply = { color ->
                val defaultHex = hexColor(ContextCompat.getColor(uiContext, R.color.accent))
                if (color.equals(defaultHex, true)) AccentPrefs.clear(uiContext) else AccentPrefs.set(uiContext, color)
                refreshAccentUi()
            }
        ).show()
    }

    private fun refreshAccentUi() {
        updateAccentUi()
        updateLyricColorUi()
        applySegmentAccents()
        if (LyricsOverlayService.isRunning) {
            startService(Intent(uiContext, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_REFRESH_ACCENT
            })
        }
    }

    private fun hexColor(color: Int): String =
        "#" + Integer.toHexString(color and 0xFFFFFF).uppercase().padStart(6, '0')

    private fun applySegmentAccents() {
        listOf(R.id.theme_follow, R.id.theme_light, R.id.theme_dark,
            R.id.background_mode_transparent, R.id.background_mode_low, R.id.background_mode_high,
            R.id.translation_original, R.id.translation_bilingual, R.id.translation_translated,
            R.id.home_align_left, R.id.home_align_center, R.id.home_align_right
        ).mapNotNull { findViewById<MaterialButton>(it) }.forEach { AccentPrefs.styleSegment(uiContext, it) }
    }

    // ---------- 主页歌词对齐 ----------
    private fun setHomeAlignment(value: String) {
        HomeLyricsPrefs.save(uiContext, value)
        updateHomeAlignmentUi()
    }

    private fun updateHomeAlignmentUi() {
        if (!::homeAlignLeft.isInitialized) return
        applySeg(
            listOf(
                homeAlignLeft to HomeLyricsPrefs.LEFT,
                homeAlignCenter to HomeLyricsPrefs.CENTER,
                homeAlignRight to HomeLyricsPrefs.RIGHT
            ),
            HomeLyricsPrefs.alignment(uiContext)
        )
    }

    // ---------- 主页歌词动效 ----------
    private fun saveMotion(transform: (MotionStyle) -> MotionStyle) {
        if (updatingMotionControls) return
        val style = transform(HomeMotionPrefs.style(uiContext))
        HomeMotionPrefs.save(uiContext, style)
        motionPreview.applyStyle(style)
    }

    private fun updateHomeMotionUi() {
        if (!::motionPreview.isInitialized) return
        val style = HomeMotionPrefs.style(uiContext)
        updatingMotionControls = true
        motionEasedScroll.isChecked = style.easedScroll
        motionStagger.isChecked = style.stagger
        motionScale.isChecked = style.scale
        motionDepthBlur.isChecked = style.depthBlur
        motionDuration.value = style.scrollDurationMs.toFloat()
        motionStaggerDelay.value = style.staggerDelayMs.toFloat()
        motionScaleStrength.value = style.scalePercent.toFloat()
        motionBlurStrength.value = style.blurPercent.toFloat()
        updatingMotionControls = false
        motionDurationValue.text = "${style.scrollDurationMs}ms"
        motionStaggerValue.text = "${style.staggerDelayMs}ms"
        motionScaleValue.text = "${style.scalePercent}%"
        motionBlurValue.text = "${style.blurPercent}%"
        // 效果关掉后对应的数值控件不可调，避免误解成仍在生效。
        motionDuration.isEnabled = style.easedScroll
        motionStaggerDelay.isEnabled = style.stagger
        motionScaleStrength.isEnabled = style.scale
        motionBlurStrength.isEnabled = style.depthBlur
        motionPreview.applyStyle(style)
    }

    // ---------- 权限状态 ----------
    private fun hasNotificationListenerAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(uiContext).contains(packageName)

    private fun updatePermissionStates() {
        if (!::listenerState.isInitialized) return
        val listenerOk = hasNotificationListenerAccess()
        val overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(uiContext)
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
            startService(Intent(uiContext, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_CONTEXT
            })
        }
    }

    private fun updateContextUi() {
        if (!::overlayPreview.isInitialized) return
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
        if (!::overlayPreview.isInitialized) return
        val percent = overlayPrefs.getInt(fontScalePreferenceKey(), expandedFontScale())
            .coerceIn(LyricsOverlayService.FONT_SCALE_MIN_PERCENT, LyricsOverlayService.FONT_SCALE_MAX_PERCENT)
        val color = runCatching {
            Color.parseColor(overlayPrefs.getString(lyricColorPreferenceKey(), expandedLyricColor()))
        }.getOrDefault(Color.WHITE)
        val mode = overlayPrefs.getString(backgroundPreferenceKey(), expandedBackgroundMode())
        val translation = overlayPrefs.getString(LyricsOverlayService.PREF_TRANSLATION_MODE, LyricsOverlayService.TRANSLATION_BILINGUAL)
            ?: LyricsOverlayService.TRANSLATION_BILINGUAL
        overlayPreview.setAppearance(percent, color, translation, contextLines(true), contextLines(false), fontWeight())
        overlayPreview.background = OverlayAppearance.background(uiContext, mode)
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
            startService(Intent(uiContext, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_BACKGROUND
                putExtra(LyricsOverlayService.EXTRA_BACKGROUND_MODE, normalized)
                putExtra(LyricsOverlayService.EXTRA_TARGET_COMPACT, true)
            })
        }
    }

    private fun updateBackgroundModeUi() {
        if (!::backgroundModeTransparent.isInitialized) return
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
            startService(Intent(uiContext, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_FONT_SCALE
                putExtra(LyricsOverlayService.EXTRA_FONT_SCALE_PERCENT, normalized)
                putExtra(LyricsOverlayService.EXTRA_TARGET_COMPACT, true)
            })
        }
    }

    private fun updateFontSizeUi() {
        if (!::fontSizeValue.isInitialized) return
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
            startService(Intent(uiContext, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_FONT_WEIGHT
                putExtra(LyricsOverlayService.EXTRA_FONT_WEIGHT, weight)
            })
        }
    }

    private fun updateFontWeightUi() {
        if (!::fontWeightValue.isInitialized) return
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
            startService(Intent(uiContext, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_LYRIC_OFFSET
                putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MS, normalized)
                putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MEMORY_KEY,
                    LyricsOverlayService.lyricOffsetPreferenceKey(identity, source))
            })
        }
    }

    private fun updateLyricOffsetUi() {
        if (!::lyricOffsetValue.isInitialized) return
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
            startService(Intent(uiContext, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_LYRIC_COLOR
                putExtra(LyricsOverlayService.EXTRA_LYRIC_COLOR, color)
                putExtra(LyricsOverlayService.EXTRA_TARGET_COMPACT, true)
            })
        }
    }

    private fun updateLyricColorUi() {
        if (!::lyricColorWhite.isInitialized) return
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
            option.strokeWidth = NativeUi.dp(uiContext, if (selectedColor) 2 else 1)
            option.strokeColor = ColorStateList.valueOf(if (selectedColor) AccentPrefs.accent(uiContext) else resolveColor(R.color.app_line))
        }
        val isCustom = options.none { (_, color) -> color.equals(selected, ignoreCase = true) }
        lyricColorCustom.isChecked = isCustom
        lyricColorCustom.strokeWidth = NativeUi.dp(uiContext, if (isCustom) 2 else 1)
        lyricColorCustom.strokeColor = ColorStateList.valueOf(if (isCustom) AccentPrefs.accent(uiContext) else resolveColor(R.color.app_line))
        val customColor = runCatching { Color.parseColor(selected) }.getOrDefault(Color.WHITE)
        if (isCustom) {
            // 选中自定义颜色时直接显示该颜色，而不是一直显示「＋」。
            lyricColorCustom.text = "✓"
            lyricColorCustom.setTextColor(contrastColor(customColor))
            lyricColorCustom.icon = null
            lyricColorCustom.backgroundTintList = ColorStateList.valueOf(customColor)
            lyricColorCustom.contentDescription = "自定义颜色 $selected，已选中"
        } else {
            lyricColorCustom.text = ""
            lyricColorCustom.setIconResource(R.drawable.ic_native_add)
            lyricColorCustom.iconTint = ColorStateList.valueOf(resolveColor(R.color.text_secondary))
            lyricColorCustom.backgroundTintList =
                ContextCompat.getColorStateList(uiContext, R.color.native_button_background)
                    ?: ColorStateList.valueOf(resolveColor(R.color.app_surface_container))
            lyricColorCustom.contentDescription = "自定义歌词颜色"
        }
    }

    private fun contrastColor(color: Int): Int =
        if (ColorUtils.calculateContrast(Color.WHITE, color) >= 4.5) Color.WHITE else Color.BLACK

    private fun resolveColor(res: Int): Int = ContextCompat.getColor(uiContext, res)

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
            startService(Intent(uiContext, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_SET_TRANSLATION_MODE
                putExtra(LyricsOverlayService.EXTRA_TRANSLATION_MODE, normalized)
            })
        }
    }

    private fun updateTranslationModeUi() {
        if (!::translationOriginal.isInitialized) return
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
        colorPicker = LyricColorPickerDialog(uiContext,
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
                    MaterialAlertDialogBuilder(uiContext)
                        .setTitle("发现新版本 ${release.version}")
                        .setMessage(release.summary)
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("前往下载") { _, _ -> openUrl(release.pageUrl) }
                        .show()
                } else {
                    Toast.makeText(uiContext, "当前已是最新版", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                val message = if (error is UpdateChecker.NoReleaseException) "还没有发布版本" else "暂时无法检查更新，请稍后重试"
                Toast.makeText(uiContext, message, Toast.LENGTH_SHORT).show()
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
