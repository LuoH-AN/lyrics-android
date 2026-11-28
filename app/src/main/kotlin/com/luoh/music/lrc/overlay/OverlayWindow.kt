package com.luoh.music.lrc.overlay

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.luoh.music.lrc.LyricsOverlayService.Companion.BACKGROUND_DEFAULT
import com.luoh.music.lrc.LyricsOverlayService.Companion.BACKGROUND_HIGH
import com.luoh.music.lrc.LyricsOverlayService.Companion.BACKGROUND_LOW
import com.luoh.music.lrc.LyricsOverlayService.Companion.BACKGROUND_MEDIUM
import com.luoh.music.lrc.LyricsOverlayService.Companion.BACKGROUND_TRANSPARENT
import com.luoh.music.lrc.LyricsOverlayService.Companion.FONT_SCALE_DEFAULT_PERCENT
import com.luoh.music.lrc.LyricsOverlayService.Companion.FONT_SCALE_MAX_PERCENT
import com.luoh.music.lrc.LyricsOverlayService.Companion.FONT_SCALE_MIN_PERCENT
import com.luoh.music.lrc.LyricsOverlayService.Companion.LYRIC_COLOR_DEFAULT
import com.luoh.music.lrc.LyricsOverlayService.Companion.PREF_BACKGROUND_MODE
import com.luoh.music.lrc.LyricsOverlayService.Companion.PREF_BACKGROUND_MODE_COMPACT
import com.luoh.music.lrc.LyricsOverlayService.Companion.PREF_CONTEXT_AFTER
import com.luoh.music.lrc.LyricsOverlayService.Companion.PREF_CONTEXT_BEFORE
import com.luoh.music.lrc.LyricsOverlayService.Companion.PREF_FONT_SCALE_COMPACT_PERCENT
import com.luoh.music.lrc.LyricsOverlayService.Companion.PREF_FONT_SCALE_PERCENT
import com.luoh.music.lrc.LyricsOverlayService.Companion.PREF_FONT_WEIGHT
import com.luoh.music.lrc.LyricsOverlayService.Companion.PREF_LYRIC_COLOR
import com.luoh.music.lrc.LyricsOverlayService.Companion.PREF_LYRIC_COLOR_COMPACT
import com.luoh.music.lrc.LyricsOverlayService.Companion.PREF_POSITION_LOCKED
import com.luoh.music.lrc.LyricsOverlayService.Companion.TRANSLATION_BILINGUAL
import com.luoh.music.lrc.R
import com.luoh.music.lrc.diagnostics.UsageAnalytics
import com.luoh.music.lrc.ui.theme.AccentPrefs
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class OverlayWindow(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val mainHandler: Handler,
    private val onLock: () -> Unit,
    private val onClose: () -> Unit,
) {
    private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val windowGeometry by lazy { OverlayWindowGeometry(context, windowManager) }
    private val uiContext by lazy { ContextThemeWrapper(context, R.style.Theme_DesktopLyrics) }

    private var overlayRoot: FrameLayout? = null
    private var overlayContent: FrameLayout? = null
    private var chromeBar: LinearLayout? = null
    private var dragTouchArea: View? = null
    private var lockButton: MaterialButton? = null
    private var closeButton: MaterialButton? = null
    private var nativeLyrics: CompactLyricsView? = null
    private var chromeHideTask: Runnable? = null
    private var keepScreenOn = false
    private var nativeContentHeight = 0
    private var windowParams: WindowManager.LayoutParams? = null
    var positionLocked: Boolean = false
        private set
    private var compactHasLyrics = false
    private var compactHasTranslation = false
    private var backgroundMode = BACKGROUND_DEFAULT
    private var fontScalePercent = FONT_SCALE_DEFAULT_PERCENT
    private var fontWeight = OverlayAppearance.DEFAULT_FONT_WEIGHT
    private var lyricColor = LYRIC_COLOR_DEFAULT
    private var translationMode = TRANSLATION_BILINGUAL
    private var lastDisplayWidth = 0
    private var lastDisplayHeight = 0

    val isOpen: Boolean get() = overlayRoot != null

    fun restorePreferences() {
        val expandedBackground = normalizedBackgroundMode(prefs.getString(PREF_BACKGROUND_MODE, BACKGROUND_DEFAULT))
        backgroundMode = normalizedBackgroundMode(prefs.getString(PREF_BACKGROUND_MODE_COMPACT, expandedBackground))
        val expandedScale = normalizedFontScale(prefs.getInt(PREF_FONT_SCALE_PERCENT, FONT_SCALE_DEFAULT_PERCENT))
        fontScalePercent = normalizedFontScale(prefs.getInt(PREF_FONT_SCALE_COMPACT_PERCENT, expandedScale))
        val expandedColor = normalizedLyricColor(prefs.getString(PREF_LYRIC_COLOR, LYRIC_COLOR_DEFAULT))
        lyricColor = normalizedLyricColor(prefs.getString(PREF_LYRIC_COLOR_COMPACT, expandedColor))
        fontWeight = OverlayAppearance.normalizeWeight(prefs.getInt(PREF_FONT_WEIGHT, OverlayAppearance.DEFAULT_FONT_WEIGHT))
        positionLocked = prefs.getBoolean(PREF_POSITION_LOCKED, false)
        prefs.edit().putBoolean("compact", true).putBoolean(PREF_OVERLAY_ROTATED, false).apply()
    }

    fun setBackground(targetCompact: Boolean, rawValue: String?) {
        val value = normalizedBackgroundMode(rawValue)
        prefs.edit().putString(if (targetCompact) PREF_BACKGROUND_MODE_COMPACT else PREF_BACKGROUND_MODE, value).apply()
        if (targetCompact) {
            backgroundMode = value
            applyBackgroundMode()
        }
    }

    fun setFontScale(targetCompact: Boolean, rawValue: Int) {
        val value = normalizedFontScale(rawValue)
        prefs.edit().putInt(if (targetCompact) PREF_FONT_SCALE_COMPACT_PERCENT else PREF_FONT_SCALE_PERCENT, value).apply()
        if (targetCompact) {
            fontScalePercent = value
            refreshContentLayout()
        }
    }

    fun setFontWeight(value: Int) {
        fontWeight = OverlayAppearance.normalizeWeight(value)
        prefs.edit().putInt(PREF_FONT_WEIGHT, fontWeight).apply()
        refreshContentLayout()
    }

    fun setLyricColor(targetCompact: Boolean, rawValue: String?) {
        val value = normalizedLyricColor(rawValue)
        prefs.edit().putString(if (targetCompact) PREF_LYRIC_COLOR_COMPACT else PREF_LYRIC_COLOR, value).apply()
        if (targetCompact) {
            lyricColor = value
            refreshAppearance()
        }
    }

    fun setTranslationMode(mode: String) {
        translationMode = mode
        applyNativeAppearance()
    }

    fun setOffset(offsetMs: Int) {
        nativeLyrics?.setOffset(offsetMs)
    }

    fun setContentState(hasLyrics: Boolean, hasTranslation: Boolean) {
        if (compactHasLyrics == hasLyrics && compactHasTranslation == hasTranslation) return
        compactHasLyrics = hasLyrics
        compactHasTranslation = hasTranslation
        resizeCompactWindow()
    }

    private var emptyStatus = R.string.nothing_playing

    fun setDocument(result: OverlayNativeDocument) {
        val lyrics = nativeLyrics ?: return
        emptyStatus = if (result.message == "loading") R.string.finding_lyrics else R.string.no_lyrics_found
        lyrics.setDocument(result.document, context.getString(emptyStatus), result.durationMs)
        if (result.document.lines.isNotEmpty()) {
            UsageAnalytics.lyricsDisplayed(UsageAnalytics.Surface.OVERLAY)
        }
    }

    fun clearLyrics(@StringRes status: Int) {
        emptyStatus = status
        nativeLyrics?.clear(context.getString(status))
    }

    fun refreshLocalizedText() {
        closeButton?.contentDescription = context.getString(R.string.close_lyrics_overlay)
        lockButton?.contentDescription = context.getString(if (positionLocked) R.string.unlock else R.string.lock)
        nativeLyrics?.refreshEmptyMessage(context.getString(emptyStatus))
    }

    fun setPlayback(identity: String, positionMs: Long, durationMs: Long, playing: Boolean, speed: Float) {
        setKeepScreenOn(playing)
        nativeLyrics?.setPlayback(identity, positionMs, durationMs, playing, speed)
    }

    fun refreshAppearance() {
        applyNativeAppearance()
        applyBackgroundMode()
    }

    fun close() {
        chromeHideTask?.let(mainHandler::removeCallbacks)
        chromeBar?.animate()?.cancel()
        overlayRoot?.let { runCatching { windowManager.removeView(it) } }
        overlayRoot = null
        windowParams = null
        overlayContent = null
        chromeBar = null
        dragTouchArea = null
        lockButton = null
        closeButton = null
        nativeLyrics = null
        chromeHideTask = null
        keepScreenOn = false
    }

    fun show() {
        if (isOpen) return
        val (screenWidth, screenHeight) = windowGeometry.currentDisplaySize()
        val safeBounds = windowGeometry.currentSafeDisplayBounds(overlayRoot)
        lastDisplayWidth = screenWidth
        lastDisplayHeight = screenHeight
        val storedExpandedWidth = prefs.getInt(
            "width",
            min(dp(360), max(1, safeBounds.width() - dp(24)))
        )
        val storedCompactWidth = preferredCompactWidth(safeBounds, prefs.getInt(PREF_COMPACT_WIDTH, storedExpandedWidth))
        val activeWindowSize = windowGeometry.fittedOverlaySize(
            storedCompactWidth, desiredCompactHeight(), safeBounds,
        )

        val params = WindowManager.LayoutParams(
            activeWindowSize.first,
            activeWindowSize.second,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            preferredRefreshRate = windowGeometry.highestOverlayRefreshRate()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            x = prefs.getInt(
                PREF_COMPACT_X,
                prefs.getInt(
                    "x",
                    max(safeBounds.left, safeBounds.right - activeWindowSize.first - dp(12))
                )
            ).coerceIn(
                safeBounds.left,
                max(safeBounds.left, safeBounds.right - activeWindowSize.first)
            )
            y = prefs.getInt(
                PREF_COMPACT_Y,
                prefs.getInt("y", dp(96))
            ).coerceIn(
                safeBounds.top,
                max(
                    safeBounds.top,
                    safeBounds.bottom - activeWindowSize.second
                )
            )
        }
        windowParams = params

        val root = FrameLayout(uiContext).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        overlayRoot = root
        root.setOnApplyWindowInsetsListener { _, insets ->
            mainHandler.post {
                val lp = windowParams ?: return@post
                val safe = windowGeometry.currentSafeDisplayBounds(overlayRoot)
                val x = lp.x.coerceIn(safe.left, max(safe.left, safe.right - lp.width))
                val y = lp.y.coerceIn(safe.top, max(safe.top, safe.bottom - lp.height))
                if (x != lp.x || y != lp.y) {
                    lp.x = x
                    lp.y = y
                    overlayRoot?.let { windowManager.updateViewLayout(it, lp) }
                }
            }
            insets
        }

        val content = FrameLayout(uiContext).apply {
            clipToOutline = true
            elevation = 0f
            background = overlayBackground()
        }
        overlayContent = content
        root.addView(
            content,
            windowGeometry.contentLayoutParams(params.width, params.height)
        )

        val chrome = LinearLayout(uiContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(150, 12, 12, 14))
            }
        }
        chromeBar = chrome

        val dragArea = View(uiContext)
        dragTouchArea = dragArea

        closeButton = chromeButton(R.drawable.ic_overlay_close, context.getString(R.string.close_lyrics_overlay), onClose)
        lockButton = chromeButton(R.drawable.ic_overlay_unlocked, context.getString(R.string.lock), onLock)
        updateLockControl()

        attachDragHandler(dragArea)

        val lyrics = CompactLyricsView(uiContext).apply {
            onContentSizeChanged = { hasLyrics, hasTranslation, height ->
                compactHasLyrics = hasLyrics
                compactHasTranslation = hasTranslation
                nativeContentHeight = height
                resizeCompactWindow()
            }
        }
        nativeLyrics = lyrics
        content.addView(lyrics, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        applyNativeAppearance()

        OverlayControls.attachFloating(root, dragArea, chrome)
        updateControlLayout()

        applyTouchMode()
        windowManager.addView(root, params)
        revealChrome()
    }

    private fun attachDragHandler(dragArea: View) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        dragArea.setOnTouchListener(object : View.OnTouchListener {
            var downRawX = 0f
            var downRawY = 0f
            var downX = 0
            var downY = 0
            var moving = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val lp = windowParams ?: return false
                if (event.actionMasked == MotionEvent.ACTION_DOWN) revealChrome()
                if (positionLocked) return true
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        downX = lp.x
                        downY = lp.y
                        moving = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!moving && max(abs(dx), abs(dy)) <= touchSlop) return true
                        if (!moving) hideChrome(immediate = true)
                        moving = true
                        val safe = windowGeometry.currentSafeDisplayBounds(overlayRoot)
                        lp.x = (downX + dx).roundToInt().coerceIn(safe.left, max(safe.left, safe.right - lp.width))
                        lp.y = (downY + dy).roundToInt().coerceIn(safe.top, max(safe.top, safe.bottom - lp.height))
                        overlayRoot?.let { windowManager.updateViewLayout(it, lp) }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (moving) saveOverlayGeometry(lp, saveSize = false)
                        moving = false
                        revealChrome()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun updateContentLayout() {
        val content = overlayContent ?: return
        val lp = windowParams ?: return
        content.layoutParams = windowGeometry.contentLayoutParams(lp.width, lp.height)
        content.requestLayout()
    }

    private fun preferredCompactWidth(safe: Rect, fallback: Int): Int =
        if (prefs.contains(OverlayAppearance.PREF_WIDTH_PERCENT))
            (safe.width() * prefs.getInt(OverlayAppearance.PREF_WIDTH_PERCENT, 90).coerceIn(40, 100) / 100f).roundToInt()
        else fallback

    fun adaptToDisplay() {
        val root = overlayRoot ?: return
        val lp = windowParams ?: return
        val (screenWidth, screenHeight) = windowGeometry.currentDisplaySize()
        val safe = windowGeometry.currentSafeDisplayBounds(overlayRoot)
        val previousWidth = max(1, lastDisplayWidth)
        val previousHeight = max(1, lastDisplayHeight)
        lp.preferredRefreshRate = windowGeometry.highestOverlayRefreshRate()
        val centerX = (lp.x + lp.width / 2f) / previousWidth
        val centerY = (lp.y + lp.height / 2f) / previousHeight

        val desiredSize = preferredCompactWidth(safe, prefs.getInt(PREF_COMPACT_WIDTH, lp.width)) to desiredCompactHeight()
        val fittedSize = windowGeometry.fittedOverlaySize(
            desiredSize.first,
            desiredSize.second,
            safe,
        )
        lp.width = fittedSize.first
        lp.height = fittedSize.second
        lp.x = (centerX * screenWidth - lp.width / 2f).roundToInt()
            .coerceIn(safe.left, max(safe.left, safe.right - lp.width))
        lp.y = (centerY * screenHeight - lp.height / 2f).roundToInt()
            .coerceIn(safe.top, max(safe.top, safe.bottom - lp.height))
        lastDisplayWidth = screenWidth
        lastDisplayHeight = screenHeight
        saveOverlayGeometry(lp, saveSize = true)
        updateContentLayout()
        windowManager.updateViewLayout(root, lp)
    }

    private fun chromeButton(icon: Int, description: String, action: () -> Unit): MaterialButton =
        OverlayControls.iconButton(uiContext, icon, description, action).apply {
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            iconTint = ColorStateList.valueOf(Color.WHITE)
            rippleColor = ColorStateList.valueOf(Color.argb(40, 255, 255, 255))
            strokeWidth = 0
        }

    private fun setChromeIcon(button: MaterialButton, drawableRes: Int) {
        button.setIconResource(drawableRes)
        updateChromeTint()
    }

    fun updateChromeTint() {
        val lockedColor = AccentPrefs.overlayAccent(context)
        lockButton?.iconTint = ColorStateList.valueOf(if (positionLocked) lockedColor else Color.WHITE)
        closeButton?.iconTint = ColorStateList.valueOf(Color.WHITE)
    }

    /** 控制条默认淡出，避免长期占用歌词宽度；触摸小窗时再出现。 */
    private fun revealChrome(retainMs: Long = 3500L) {
        if (positionLocked) {
            hideChrome(immediate = true)
            return
        }
        val chrome = chromeBar ?: return
        chromeHideTask?.let(mainHandler::removeCallbacks)
        chrome.animate().cancel()
        chrome.visibility = View.VISIBLE
        chrome.alpha = 1f
        val task = Runnable { hideChrome() }
        chromeHideTask = task
        mainHandler.postDelayed(task, retainMs)
    }

    private fun hideChrome(immediate: Boolean = false) {
        val chrome = chromeBar ?: return
        chromeHideTask?.let(mainHandler::removeCallbacks)
        chromeHideTask = null
        chrome.animate().cancel()
        if (immediate) {
            chrome.alpha = 0f
            chrome.visibility = View.INVISIBLE
            return
        }
        chrome.animate().alpha(0f).setDuration(220).withEndAction {
            if (chromeHideTask == null) chrome.visibility = View.INVISIBLE
        }.start()
    }

    private fun overlayBackground(): GradientDrawable = OverlayAppearance.background(
        context, backgroundMode, OverlayAppearance.parseColor(lyricColor),
        prefs.getInt(OverlayAppearance.PREF_BACKGROUND_OPACITY, OverlayAppearance.DEFAULT_BACKGROUND_OPACITY),
        prefs.getInt(OverlayAppearance.PREF_CORNER_RADIUS, OverlayAppearance.DEFAULT_CORNER_RADIUS),
    )

    private fun saveOverlayGeometry(lp: WindowManager.LayoutParams, saveSize: Boolean) {
        val editor = prefs.edit()
            .putInt("x", lp.x)
            .putInt("y", lp.y)
            .putInt(PREF_COMPACT_X, lp.x)
            .putInt(PREF_COMPACT_Y, lp.y)
            .putBoolean("compact", true)
        if (saveSize) {
            editor.putInt(PREF_COMPACT_WIDTH, lp.width).putInt("compact_height_v3", lp.height)
        }
        editor.apply()
    }

    private fun applyBackgroundMode() {
        overlayContent?.background = overlayBackground()
    }

    private fun applyNativeAppearance() {
        val color = Color.parseColor(lyricColor)
        nativeLyrics?.setAppearance(fontScalePercent, color, translationMode, contextLines(true), contextLines(false), fontWeight,
            OverlayAppearance.textStyle(prefs))
        updateChromeTint()
    }

    private fun contextLines(before: Boolean): Int = prefs.getInt(
        if (before) PREF_CONTEXT_BEFORE else PREF_CONTEXT_AFTER, if (before) 0 else 1
    ).coerceIn(0, 2)

    private fun desiredCompactHeight(): Int {
        if (nativeContentHeight > 0) return nativeContentHeight
        val secondaryLines = if (compactHasLyrics) contextLines(true) + contextLines(false) +
            (if (compactHasTranslation) 1 else 0) else 0
        val scale = fontScalePercent / 100f
        return dp(kotlin.math.ceil(12f + (34.5f + 26.45f * secondaryLines) * scale).toInt().coerceAtLeast(48))
    }

    fun refreshContentLayout() {
        applyNativeAppearance()
        resizeCompactWindow()
    }

    private fun resizeCompactWindow() {
        val root = overlayRoot ?: return
        val lp = windowParams ?: return
        val safe = windowGeometry.currentSafeDisplayBounds(overlayRoot)
        val height = desiredCompactHeight().coerceAtMost(windowGeometry.availableHeight(safe.height()))
        if (lp.height == height) return
        lp.height = height
        lp.y = lp.y.coerceIn(safe.top, max(safe.top, safe.bottom - lp.height))
        updateContentLayout()
        if (root.isAttachedToWindow) windowManager.updateViewLayout(root, lp)
        saveOverlayGeometry(lp, saveSize = true)
    }

    fun setPositionLocked(locked: Boolean) {
        positionLocked = locked
        prefs.edit().putBoolean(PREF_POSITION_LOCKED, locked).apply()
        applyTouchMode()
        updateLockControl()
    }

    private fun applyTouchMode() {
        val lp = windowParams ?: return
        // Android 12+ blocks touches through opaque application overlays even when
        // NOT_TOUCHABLE is set. Window alpha, rather than background alpha, must
        // respect the system's maximum obscuring opacity.
        val maximumOpacity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
        } else null
        applyOverlayTouchMode(lp, positionLocked, maximumOpacity)
        overlayRoot?.takeIf { it.isAttachedToWindow }?.let {
            windowManager.updateViewLayout(it, lp)
        }
    }

    private fun normalizedBackgroundMode(value: String?): String = when (value) {
        BACKGROUND_TRANSPARENT -> BACKGROUND_TRANSPARENT
        // medium 归并到半透明(low)；只保留 透明/半透明/不透明 三档
        BACKGROUND_LOW, BACKGROUND_MEDIUM -> BACKGROUND_LOW
        BACKGROUND_HIGH -> BACKGROUND_HIGH
        else -> BACKGROUND_DEFAULT
    }

    private fun normalizedLyricColor(value: String?): String {
        val normalized = value.orEmpty().uppercase(Locale.ROOT)
        return if (Regex("^#[0-9A-F]{6}$").matches(normalized)) normalized else LYRIC_COLOR_DEFAULT
    }

    private fun normalizedFontScale(value: Int): Int =
        value.coerceIn(FONT_SCALE_MIN_PERCENT, FONT_SCALE_MAX_PERCENT)

    private fun updateLockControl() {
        lockButton?.let { button ->
            button.contentDescription = if (positionLocked) context.getString(R.string.unlock) else context.getString(R.string.lock)
            button.isChecked = positionLocked
            button.isSelected = positionLocked
            setChromeIcon(button, if (positionLocked) R.drawable.ic_overlay_locked else R.drawable.ic_overlay_unlocked)
        }
        revealChrome()
    }

    private fun updateControlLayout() {
        val chrome = chromeBar ?: return
        chrome.orientation = LinearLayout.HORIZONTAL
        chrome.gravity = Gravity.CENTER
        chrome.removeAllViews()
        listOfNotNull(lockButton, closeButton).forEach {
            chrome.addView(it, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        dragTouchArea?.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    /** 只在音乐播放时保持屏幕常亮；暂停或没有会话时释放。 */
    fun setKeepScreenOn(playing: Boolean) {
        val lp = windowParams ?: return
        if (keepScreenOn == playing) return
        keepScreenOn = playing
        lp.flags = if (playing) {
            lp.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
        val root = overlayRoot ?: return
        if (root.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(root, lp) }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val PREF_COMPACT_WIDTH = "compact_width_v1"
        const val PREF_COMPACT_X = "compact_x_v1"
        const val PREF_COMPACT_Y = "compact_y_v1"
        const val PREF_OVERLAY_ROTATED = "overlay_rotated_v1"
    }
}
