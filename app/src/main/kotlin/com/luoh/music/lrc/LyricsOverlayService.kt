package com.luoh.music.lrc

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.luoh.music.lrc.data.lyrics.CustomLyricsStore
import com.luoh.music.lrc.data.lyrics.DirectLyricsRepository
import com.luoh.music.lrc.data.lyrics.LyricMatchMemory
import com.luoh.music.lrc.data.lyrics.LyricOffsetMemory
import com.luoh.music.lrc.data.lyrics.SharedLyricsCache
import com.luoh.music.lrc.data.lyrics.SongSearchCorrections
import com.luoh.music.lrc.data.lyrics.TemporaryCache
import com.luoh.music.lrc.data.translation.SupplementOutcome
import com.luoh.music.lrc.data.translation.SupplementTranslation
import com.luoh.music.lrc.data.translation.TranslationApiProfiles
import com.luoh.music.lrc.data.translation.translationPrefs
import com.luoh.music.lrc.diagnostics.UsageAnalytics
import com.luoh.music.lrc.overlay.CompactLyricsView
import com.luoh.music.lrc.overlay.OverlayAppearance
import com.luoh.music.lrc.overlay.OverlayControls
import com.luoh.music.lrc.overlay.OverlayNativeDocument
import com.luoh.music.lrc.overlay.OverlayNotification
import com.luoh.music.lrc.overlay.OverlayPlaybackSnapshot
import com.luoh.music.lrc.overlay.OverlayWindowGeometry
import com.luoh.music.lrc.overlay.applyOverlayTouchMode
import com.luoh.music.lrc.overlay.overlayTrackTitle
import com.luoh.music.lrc.playback.mediaSessionScore
import com.luoh.music.lrc.ui.theme.AccentPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A user-started overlay that consumes MediaSession callbacks directly on-device.
 * Network requests are only used to resolve lyrics/cover art; playback synchronization
 * never waits for the website status polling path.
 */
@SuppressLint("ForegroundServiceType")
class LyricsOverlayService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val matchMemory by lazy { LyricMatchMemory(prefs) }
    private val offsetMemory by lazy { LyricOffsetMemory(prefs) }
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val windowGeometry by lazy { OverlayWindowGeometry(this, windowManager) }
    private val notification by lazy { OverlayNotification(this) }
    private val sessionManager by lazy { getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager }
    private val playbackSnapshot = OverlayPlaybackSnapshot(this)
    private val listenerComponent by lazy { ComponentName(this, MediaListenerService::class.java) }
    private val lyricsRepository = DirectLyricsRepository()
    private val lyricsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uiContext by lazy { ContextThemeWrapper(this, R.style.Theme_DesktopLyrics) }

    private var overlayRoot: FrameLayout? = null
    private var overlayContent: FrameLayout? = null
    private var chromeBar: LinearLayout? = null
    private var dragTouchArea: View? = null
    private var lockButton: MaterialButton? = null
    private var closeButton: MaterialButton? = null
    private var nativeLyrics: CompactLyricsView? = null
    private var chromeHideTask: Runnable? = null
    private var keepScreenOn = false
    private var nativeTrack = ""
    private var nativeArtist = ""
    private var nativeDocumentRequestId = -1
    private var nativeContentHeight = 0
    // Retained offscreen only for matching/cache compatibility; never added to the window.
    private var webView: WebView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var webReady = false
    private val compact = true
    private val overlayRotated = false
    private var positionLocked = false
    private var compactHasLyrics = false
    private var compactHasTranslation = false
    private var backgroundMode = BACKGROUND_DEFAULT
    private var fontScalePercent = FONT_SCALE_DEFAULT_PERCENT
    private var fontWeight = OverlayAppearance.DEFAULT_FONT_WEIGHT
    private var lyricColor = LYRIC_COLOR_DEFAULT
    private var expandedBackgroundMode = BACKGROUND_DEFAULT
    private var compactBackgroundMode = BACKGROUND_DEFAULT
    private var expandedFontScalePercent = FONT_SCALE_DEFAULT_PERCENT
    private var compactFontScalePercent = FONT_SCALE_DEFAULT_PERCENT
    private var expandedLyricColor = LYRIC_COLOR_DEFAULT
    private var compactLyricColor = LYRIC_COLOR_DEFAULT
    private var lyricOffsetMs = 0
    private var activeLyric: LyricOffsetMemory.Song? = null
    private var translationMode = TRANSLATION_BILINGUAL
    private var monitorStarted = false
    private var lastDisplayWidth = 0
    private var lastDisplayHeight = 0
    private var currentController: MediaController? = null
    private var pendingSnapshot: JSONObject? = null
    private val sharedLyricsCache by lazy { SharedLyricsCache(this) }
    private var snapshotScheduled = false
    @Volatile private var latestLyricsRequestId = 0
    private var supplementJob: kotlinx.coroutines.Job? = null
    private var supplementGeneration = 0
    private val supplements by lazy { SupplementTranslation(this) }

    private val dispatchRunnable = Runnable {
        snapshotScheduled = false
        dispatchSnapshot()
    }
    private val sessionRefreshRunnable = object : Runnable {
        override fun run() {
            if (!monitorStarted) return
            refreshActiveSessions()
            mainHandler.postDelayed(this, 2_000L)
        }
    }

    private val engineTickRunnable = object : Runnable {
        override fun run() {
            if (!webReady || overlayRoot == null) return
            webView?.evaluateJavascript("window.LobstaOverlay && window.LobstaOverlay.engineTick();", null)
            mainHandler.postDelayed(this, 250L)
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleSnapshot()
        override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleSnapshot()
        override fun onSessionDestroyed() = refreshActiveSessions()
    }

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            selectController(controllers.orEmpty())
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.post { adaptOverlayToDisplay() }
        mainHandler.postDelayed({ adaptOverlayToDisplay() }, 220L)
    }

    override fun onCreate() {
        super.onCreate()
        UsageAnalytics.configuration(this)
        lyricsScope.launch { TemporaryCache.prune(applicationContext) }
        instance = this
        isRunning = true
        announceOverlayState()
        expandedBackgroundMode = normalizedBackgroundMode(
            prefs.getString(PREF_BACKGROUND_MODE, BACKGROUND_DEFAULT)
        )
        compactBackgroundMode = normalizedBackgroundMode(
            prefs.getString(PREF_BACKGROUND_MODE_COMPACT, expandedBackgroundMode)
        )
        expandedFontScalePercent = normalizedFontScale(
            prefs.getInt(PREF_FONT_SCALE_PERCENT, FONT_SCALE_DEFAULT_PERCENT)
        )
        compactFontScalePercent = normalizedFontScale(
            prefs.getInt(PREF_FONT_SCALE_COMPACT_PERCENT, expandedFontScalePercent)
        )
        expandedLyricColor = normalizedLyricColor(prefs.getString(PREF_LYRIC_COLOR, LYRIC_COLOR_DEFAULT))
        compactLyricColor = normalizedLyricColor(
            prefs.getString(PREF_LYRIC_COLOR_COMPACT, expandedLyricColor)
        )
        fontWeight = OverlayAppearance.normalizeWeight(prefs.getInt(PREF_FONT_WEIGHT, OverlayAppearance.DEFAULT_FONT_WEIGHT))
        syncActiveVisualPreferences(true)
        lyricOffsetMs = offsetMemory.displayedOffset()
        translationMode = normalizedTranslationMode(
            prefs.getString(PREF_TRANSLATION_MODE, TRANSLATION_BILINGUAL)
        )
        positionLocked = prefs.getBoolean(PREF_POSITION_LOCKED, false)
        prefs.edit().putBoolean("compact", true).putBoolean(PREF_OVERLAY_ROTATED, false).apply()
        notification.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH_NOTIFICATION && overlayRoot != null) {
            notification.refresh(positionLocked)
            return START_STICKY
        }
        if (intent?.action == ACTION_REFRESH_ACCENT) {
            updateChromeTint()
            return START_STICKY
        }
        if (intent?.action == ACTION_REFRESH_MATCHES) {
            webView?.evaluateJavascript("window.LobstaOverlay?.refreshMatchManagement();", null)
            return START_STICKY
        }
        if (intent?.action == ACTION_RELOAD_CUSTOM_LYRICS) {
            reloadCustomLyrics()
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_LOCK || intent?.action == ACTION_UNLOCK) {
            setPositionLocked(intent.action == ACTION_LOCK)
            if (overlayRoot != null) return START_STICKY
        }

        if (intent?.action == ACTION_SET_CONTEXT) {
            applyCompactLayout()
            if (overlayRoot != null) return START_STICKY
        }

        if (intent?.action == ACTION_REFRESH_APPEARANCE) {
            applyNativeAppearance()
            applyBackgroundMode()
            adaptOverlayToDisplay()
            if (overlayRoot != null) return START_STICKY
        }

        if (intent?.action == ACTION_SET_BACKGROUND) {
            val targetCompact = intent.getBooleanExtra(EXTRA_TARGET_COMPACT, false)
            val value = normalizedBackgroundMode(
                intent.getStringExtra(EXTRA_BACKGROUND_MODE)
            )
            if (targetCompact) compactBackgroundMode = value else expandedBackgroundMode = value
            prefs.edit().putString(
                if (targetCompact) PREF_BACKGROUND_MODE_COMPACT else PREF_BACKGROUND_MODE,
                value
            ).apply()
            if (displayedVisualTargetIsCompact() == targetCompact) {
                backgroundMode = value
                applyBackgroundMode()
            }
            if (overlayRoot != null) return START_STICKY
        }

        if (intent?.action == ACTION_SET_FONT_WEIGHT) {
            fontWeight = OverlayAppearance.normalizeWeight(intent.getIntExtra(EXTRA_FONT_WEIGHT, OverlayAppearance.DEFAULT_FONT_WEIGHT))
            prefs.edit().putInt(PREF_FONT_WEIGHT, fontWeight).apply()
            applyFontScale()
            if (overlayRoot != null) return START_STICKY
        }

        if (intent?.action == ACTION_SET_FONT_SCALE) {
            val targetCompact = intent.getBooleanExtra(EXTRA_TARGET_COMPACT, false)
            val value = normalizedFontScale(
                intent.getIntExtra(EXTRA_FONT_SCALE_PERCENT, FONT_SCALE_DEFAULT_PERCENT)
            )
            if (targetCompact) compactFontScalePercent = value else expandedFontScalePercent = value
            prefs.edit().putInt(
                if (targetCompact) PREF_FONT_SCALE_COMPACT_PERCENT else PREF_FONT_SCALE_PERCENT,
                value
            ).apply()
            if (displayedVisualTargetIsCompact() == targetCompact) {
                fontScalePercent = value
                applyFontScale()
            }
            if (overlayRoot != null) return START_STICKY
        }

        if (intent?.action == ACTION_SET_LYRIC_COLOR) {
            val targetCompact = intent.getBooleanExtra(EXTRA_TARGET_COMPACT, false)
            val value = normalizedLyricColor(intent.getStringExtra(EXTRA_LYRIC_COLOR))
            if (targetCompact) compactLyricColor = value else expandedLyricColor = value
            prefs.edit().putString(
                if (targetCompact) PREF_LYRIC_COLOR_COMPACT else PREF_LYRIC_COLOR,
                value
            ).apply()
            if (displayedVisualTargetIsCompact() == targetCompact) {
                lyricColor = value
                applyLyricColor()
            }
            if (overlayRoot != null) return START_STICKY
        }

        if (intent?.action == ACTION_SET_LYRIC_OFFSET) {
            val expectedKey = intent.getStringExtra(EXTRA_LYRIC_OFFSET_MEMORY_KEY)
            if (expectedKey != null && expectedKey != activeLyric?.key) {
                // 设置页已保存目标歌曲的记忆；切歌后不要把这条消息套到另一首歌上。
                if (overlayRoot == null) stopSelf()
                return if (overlayRoot == null) START_NOT_STICKY else START_STICKY
            }
            lyricOffsetMs = intent.getIntExtra(EXTRA_LYRIC_OFFSET_MS, 0)
                .coerceIn(LyricOffsetMemory.MIN_MS, LyricOffsetMemory.MAX_MS)
            offsetMemory.save(activeLyric, lyricOffsetMs, updateDisplayedOffset = true)
            applyLyricOffset()
            if (overlayRoot != null) return START_STICKY
        }

        if (intent?.action == ACTION_DELETE_LYRIC_OFFSET_MEMORY) {
            val key = intent.getStringExtra(EXTRA_LYRIC_OFFSET_MEMORY_KEY).orEmpty()
            val isActiveLyric = activeLyric?.key == key
            if (offsetMemory.delete(key, resetDisplayedOffset = isActiveLyric)) {
                if (isActiveLyric) {
                    lyricOffsetMs = 0
                    applyLyricOffset()
                }
                announceOverlayState()
            }
            if (overlayRoot != null) return START_STICKY
        }

        if (intent?.action == ACTION_CLEAR_LYRIC_OFFSET_MEMORIES) {
            lyricOffsetMs = 0
            offsetMemory.clear()
            applyLyricOffset()
            announceOverlayState()
            if (overlayRoot != null) return START_STICKY
        }

        if (intent?.action == ACTION_SET_TRANSLATION_MODE) {
            translationMode = normalizedTranslationMode(
                intent.getStringExtra(EXTRA_TRANSLATION_MODE)
            )
            prefs.edit().putString(PREF_TRANSLATION_MODE, translationMode).apply()
            applyTranslationMode()
            if (overlayRoot != null) return START_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        notification.start(positionLocked)
        if (overlayRoot == null) createOverlay()
        startMediaMonitor()
        return START_STICKY
    }

    override fun onDestroy() {
        UsageAnalytics.setActive(UsageAnalytics.Surface.OVERLAY, false)
        instance = null
        mainHandler.removeCallbacksAndMessages(null)
        lyricsScope.cancel()
        lyricsRepository.close()
        stopMediaMonitor()
        val player = webView
        (player?.parent as? ViewGroup)?.removeView(player)
        overlayRoot?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        player?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        webReady = false
        webView = null
        nativeLyrics = null
        overlayRoot = null
        overlayContent = null
        chromeBar = null
        dragTouchArea = null
        lockButton = null
        closeButton = null
        chromeHideTask = null
        keepScreenOn = false
        isRunning = false
        announceOverlayState()
        super.onDestroy()
    }

    private fun announceOverlayState() {
        LyricsTileService.requestUpdate(this)
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_RUNNING, isRunning)
        )
    }

    fun refreshSupplementTranslation() {
        mainHandler.post {
            webView?.evaluateJavascript("window.LobstaOverlay.refreshSupplement();", null)
        }
    }

    private inner class LyricsJavascriptBridge {
        @JavascriptInterface
        fun renderNativeLyrics(payload: String) {
            if (payload.length > 2_000_000) return
            val result = OverlayNativeDocument.parse(payload) ?: return
            mainHandler.post {
                if (nativeTrack.isBlank() || result.track != nativeTrack || result.artist != nativeArtist ||
                    result.requestId < nativeDocumentRequestId) return@post
                nativeDocumentRequestId = result.requestId
                latestLyricsRequestId = maxOf(latestLyricsRequestId, result.requestId)
                nativeLyrics?.setDocument(result.document, result.message, result.durationMs)
                if (nativeLyrics != null && result.document.lines.isNotEmpty()) {
                    UsageAnalytics.lyricsDisplayed(UsageAnalytics.Surface.OVERLAY)
                }
            }
        }

        @JavascriptInterface
        fun reportSupplementStatus(payload: String) {
            if (payload.length > 4000) return
            val data = runCatching { JSONObject(payload) }.getOrNull() ?: return
            mainHandler.post {
                if (nativeTrack.isBlank() || data.optString("track") != nativeTrack ||
                    data.optString("artist") != nativeArtist ||
                    data.optInt("requestId", -1) != nativeDocumentRequestId) return@post
                val status = data.optString("status")
                val message = when (status) {
                    "working" -> "正在补充译文"
                    "done", "skipped", "off" -> ""
                    "error" -> data.optString("message").take(180).ifBlank { "补充翻译失败，请检查配置" }
                    else -> return@post
                }
                translationPrefs().edit()
                    .putString("last_status", message).putString("last_track", nativeTrack).apply()
                if (status == "error") android.widget.Toast.makeText(
                    this@LyricsOverlayService, message, android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

        @JavascriptInterface
        fun setCompactContentState(hasLyrics: Boolean, hasTranslation: Boolean) {
            mainHandler.post {
                if (compactHasLyrics == hasLyrics && compactHasTranslation == hasTranslation) return@post
                compactHasLyrics = hasLyrics
                compactHasTranslation = hasTranslation
                resizeCompactWindow()
            }
        }

        @JavascriptInterface
        fun lyricSourceOffset(identity: String, source: String, title: String, artist: String): Int {
            val safeIdentity = identity.trim().take(600)
            val safeSource = source.trim().take(120)
            val safeTitle = title.trim().take(300)
            val safeArtist = artist.trim().take(300)
            if (safeIdentity.isBlank() || safeSource.isBlank()) return 0
            val song = LyricOffsetMemory.Song(safeIdentity, safeSource, safeTitle, safeArtist)
            val remembered = offsetMemory.read(song)
            mainHandler.post {
                if (safeTitle != nativeTrack || safeArtist != nativeArtist) return@post
                activeLyric = song
                lyricOffsetMs = remembered
                nativeLyrics?.setOffset(remembered)
                offsetMemory.rememberActiveSong(song, remembered)
                announceOverlayState()
            }
            return remembered
        }

        @JavascriptInterface
        fun supplementSettings() {
            mainHandler.post { startActivity(Intent(this@LyricsOverlayService, TranslationSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        @JavascriptInterface
        fun supplementStrategy(): String {
            val translationPrefs = translationPrefs()
            val profile = TranslationApiProfiles.find(
                this@LyricsOverlayService,
                translationPrefs.getString("active_api_profile", null)
            )
            val endpoint = translationPrefs.getString(
                TranslationApiProfiles.endpointKey(profile.id), profile.defaultEndpoint
            ).orEmpty().lowercase()
            val model = translationPrefs.getString(
                TranslationApiProfiles.modelKey(profile.id), profile.defaultModel
            ).orEmpty().lowercase()
            val provider = when {
                profile.id == "gemini" || "generativelanguage.googleapis.com" in endpoint || "gemini" in model -> "gemini"
                profile.id == "glm" || "bigmodel.cn" in endpoint || model.startsWith("glm-") -> "glm"
                profile.id == "deepseek" || "deepseek.com" in endpoint || model.startsWith("deepseek-") -> "deepseek"
                else -> "default"
            }
            return when (provider) {
                "gemini" -> JSONObject().put("behind", 2).put("ahead", 34).put("prefetch", 10).toString()
                "glm" -> JSONObject().put("behind", 2).put("ahead", 22).put("prefetch", 7).toString()
                else -> JSONObject().put("behind", 2).put("ahead", 12).put("prefetch", 4).toString()
            }
        }

        @JavascriptInterface
        fun cancelSupplement() {
            mainHandler.post { supplementGeneration++; supplementJob?.cancel() }
        }

        @JavascriptInterface
        fun translateMissing(requestId: Int, payload: String) {
            if (payload.length > 250000) return
            mainHandler.post {
                supplementJob?.cancel()
                val generation = ++supplementGeneration
                fun report(data: JSONObject) {
                    mainHandler.post {
                        if (generation == supplementGeneration && webReady) webView?.evaluateJavascript(
                            "window.LobstaOverlay.receiveSupplement($requestId,$data);", null)
                    }
                }
                supplementJob = lyricsScope.launch {
                    try {
                        val mode = translationPrefs().getString("mode", "off")
                        if (mode == "off") { report(JSONObject().put("status", "off")); return@launch }
                        val outcome = supplements.translate(payload,
                            onTranslationRequired = { report(JSONObject().put("status", "working")) }) { report(it) }
                        val status = when (outcome) {
                            SupplementOutcome.COMPLETE -> "done"
                            SupplementOutcome.NOT_NEEDED -> "skipped"
                            SupplementOutcome.DISABLED -> "off"
                        }
                        report(JSONObject().put("status", status))
                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        val message = when (error) {
                            is IllegalArgumentException -> error.message.orEmpty().take(180)
                            is java.net.SocketTimeoutException -> "翻译连接超时，请检查网络或 API 服务"
                            is java.io.IOException -> "翻译服务网络连接失败：${error.message.orEmpty().take(120)}"
                            else -> "补充翻译暂不可用：${error.message.orEmpty().take(120)}"
                        }
                        report(JSONObject().put("status", "error").put("message", message))
                    }
                }
            }
        }
        @JavascriptInterface
        fun mediaCommand(command: String) {
            mainHandler.post {
                val controller = currentController ?: return@post
                runCatching {
                    when (command) {
                        "play" -> controller.transportControls.play()
                        "pause" -> controller.transportControls.pause()
                        "previous" -> controller.transportControls.skipToPrevious()
                        "next" -> controller.transportControls.skipToNext()
                        else -> return@runCatching
                    }
                }.onFailure {
                    Log.w(LOG_TAG, "Media command failed: $command", it)
                }
                mainHandler.postDelayed({ scheduleSnapshot() }, 180L)
            }
        }

        @JavascriptInterface
        fun mediaSeek(positionMs: Double) {
            if (!positionMs.isFinite()) return
            mainHandler.post {
                val controller = currentController ?: return@post
                val duration = controller.metadata
                    ?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                    ?.coerceAtLeast(0L)
                    ?: 0L
                val requested = positionMs.toLong().coerceAtLeast(0L)
                val target = if (duration > 0L) requested.coerceAtMost(duration) else requested
                runCatching {
                    controller.transportControls.seekTo(target)
                }.onFailure {
                    Log.w(LOG_TAG, "Media seek failed: $target", it)
                }
                mainHandler.postDelayed({ scheduleSnapshot() }, 180L)
            }
        }

        @JavascriptInterface
        fun rematchLyrics(track: String, artist: String, album: String, durationMs: Double,
                          source: String, excludedJson: String, requestId: Int, operationId: Int) {
            if (requestId != latestLyricsRequestId || track.isBlank()) return
            lyricsScope.launch {
                val payload = try {
                    val array = org.json.JSONArray(excludedJson)
                    val excluded = (0 until array.length()).map { array.optString(it) }.toSet()
                    val search = SongSearchCorrections.resolve(this@LyricsOverlayService, track, artist)
                    val result = lyricsRepository.rematch(source, search.title, search.artist, album,
                        durationMs.takeIf { it.isFinite() && it > 0 }?.toLong() ?: 0L, excluded)
                    result?.toJson() ?: JSONObject().put("error", "没有找到更合适的版本，已保留当前歌词")
                } catch (error: Exception) {
                    val cooling = error.message.orEmpty().contains("cool", ignoreCase = true)
                    JSONObject().put("error", if (cooling) "此歌词源暂时限流或不可用，请稍后重试" else "重新匹配失败，已保留当前歌词")
                }
                mainHandler.post {
                    if (requestId == latestLyricsRequestId && webReady) webView?.evaluateJavascript(
                        "window.LobstaOverlay && window.LobstaOverlay.receiveRematch($requestId,$operationId,$payload);", null)
                }
            }
        }

        @JavascriptInterface
        fun readMatchMemory(): String = matchMemory.readRaw()

        /** 悬浮窗解析歌曲时先同步问一次：这首歌有没有用户手动指定的自定义 LRC 歌词。 */
        @JavascriptInterface
        fun customLyrics(track: String, artist: String, durationMs: Double): String {
            if (track.isBlank()) return ""
            val entry = CustomLyricsStore.find(this@LyricsOverlayService, track, artist) ?: return ""
            val duration = durationMs.takeIf { it.isFinite() && it > 0 }?.toLong() ?: 0L
            return CustomLyricsStore.toResultJson(entry, duration).toString()
        }

        @JavascriptInterface
        fun lyricCacheEpoch(): Long = sharedLyricsCache.epoch

        @JavascriptInterface
        fun cachedLyrics(track: String, artist: String, album: String, durationMs: Double): String {
            if (track.isBlank()) return ""
            val duration = durationMs.takeIf { it.isFinite() && it > 0 }?.toLong() ?: 0L
            val search = SongSearchCorrections.resolve(this@LyricsOverlayService, track, artist)
            return sharedLyricsCache.read(search.title, search.artist, album, duration)?.toJson()?.toString().orEmpty()
        }

        /** 从悬浮窗菜单进入自定义歌词编辑页（当前这首歌）。 */
        @JavascriptInterface
        fun editCustomLyrics(track: String, artist: String) {
            mainHandler.post {
                startActivity(
                    CustomLyricsEditActivity.intent(this@LyricsOverlayService, track, artist)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        @JavascriptInterface
        fun writeMatchMemory(value: String) = matchMemory.writeFromOverlay(value)

        @JavascriptInterface
        fun requestLyrics(
            track: String,
            artist: String,
            album: String,
            durationMs: Double,
            requestId: Int,
            needsRemoteCover: Boolean
        ) {
            if (track.isBlank() || requestId <= 0) return
            latestLyricsRequestId = requestId
            val search = SongSearchCorrections.resolve(this@LyricsOverlayService, track, artist)
            lyricsScope.launch {
                val startedAt = SystemClock.elapsedRealtime()
                val coverLookup = if (needsRemoteCover) {
                    async { lyricsRepository.resolveCover(search.title, search.artist) }
                } else null
                val duration = durationMs.takeIf { it.isFinite() && it > 0 }?.toLong() ?: 0L
                val result = sharedLyricsCache.resolve(search.title, search.artist, album, duration) {
                    lyricsRepository.resolveLyrics(search.title, search.artist, album, duration, onPartial = { partial ->
                        if (requestId == latestLyricsRequestId) deliverLyricsResult(requestId, partial)
                    })
                }
                if (requestId != latestLyricsRequestId) {
                    coverLookup?.cancel()
                    return@launch
                }
                Log.i(
                    LOG_TAG,
                    "Direct lyrics source=${result.source.ifBlank { "none" }} " +
                        "found=${result.lyrics.isNotBlank()} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
                )
                deliverLyricsResult(requestId, result)

                if (needsRemoteCover && result.cover.isBlank()) {
                    val cover = runCatching { coverLookup?.await().orEmpty() }.getOrDefault("")
                    if (cover.isNotBlank() && requestId == latestLyricsRequestId) {
                        deliverRemoteCover(requestId, cover)
                    }
                } else {
                    coverLookup?.cancel()
                }
            }
        }
    }

    private fun deliverLyricsResult(requestId: Int, result: DirectLyricsRepository.Result) {
        val payload = result.toJson().toString()
        mainHandler.post {
            if (requestId != latestLyricsRequestId || !webReady) return@post
            webView?.evaluateJavascript(
                "window.LobstaOverlay && window.LobstaOverlay.receiveLyrics($requestId,$payload);",
                null
            )
        }
    }

    private fun deliverRemoteCover(requestId: Int, cover: String) {
        val encodedCover = JSONObject.quote(cover)
        mainHandler.post {
            if (requestId != latestLyricsRequestId || !webReady) return@post
            webView?.evaluateJavascript(
                "window.LobstaOverlay && window.LobstaOverlay.receiveRemoteCover($requestId,$encodedCover);",
                null
            )
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun createOverlay() {
        val (screenWidth, screenHeight) = windowGeometry.currentDisplaySize()
        val safeBounds = windowGeometry.currentSafeDisplayBounds(overlayRoot)
        lastDisplayWidth = screenWidth
        lastDisplayHeight = screenHeight
        val storedExpandedWidth = prefs.getInt(
            "width",
            min(dp(360), max(1, safeBounds.width() - dp(24)))
        )
        val storedCompactWidth = preferredCompactWidth(safeBounds, prefs.getInt(PREF_COMPACT_WIDTH, storedExpandedWidth))
        syncActiveVisualPreferences(true)
        val activeWindowSize = windowGeometry.fittedLogicalOverlaySize(
            storedCompactWidth, desiredCompactHeight(),
            rotated = false, safeBounds = safeBounds
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
                positionXPreferenceKey(compact),
                prefs.getInt(
                    "x",
                    max(safeBounds.left, safeBounds.right - activeWindowSize.first - dp(12))
                )
            ).coerceIn(
                safeBounds.left,
                max(safeBounds.left, safeBounds.right - activeWindowSize.first)
            )
            y = prefs.getInt(
                positionYPreferenceKey(compact),
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
                run {
                    val safe = windowGeometry.currentSafeDisplayBounds(overlayRoot)
                    windowParams?.let { lp ->
                        val x = lp.x.coerceIn(safe.left, max(safe.left, safe.right - lp.width))
                        val y = lp.y.coerceIn(safe.top, max(safe.top, safe.bottom - lp.height))
                        if (x != lp.x || y != lp.y) {
                            lp.x = x; lp.y = y
                            overlayRoot?.let { windowManager.updateViewLayout(it, lp) }
                        }
                    }
                }
            }
            insets
        }

        val content = FrameLayout(uiContext).apply {
            clipToOutline = true
            elevation = 0f
            background = overlayBackground(compact)
        }
        overlayContent = content
        root.addView(
            content,
            windowGeometry.rotatedContentLayoutParams(params.width, params.height, overlayRotated)
        )
        content.rotation = if (overlayRotated) 90f else 0f

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

        closeButton = chromeButton(R.drawable.ic_overlay_close, "关闭桌面歌词") { stopSelf() }
        lockButton = chromeButton(R.drawable.ic_overlay_unlocked, "锁定") {
            setPositionLocked(true)
        }
        updateLockControl()

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
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
                        downRawX = event.rawX; downRawY = event.rawY
                        downX = lp.x; downY = lp.y
                        moving = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!moving && max(kotlin.math.abs(dx), kotlin.math.abs(dy)) <= touchSlop) return true
                        if (!moving) hideChrome(immediate = true)
                        moving = true
                        val safe = windowGeometry.currentSafeDisplayBounds(overlayRoot)
                        lp.x = (downX + dx).roundToInt().coerceIn(safe.left, max(safe.left, safe.right - lp.width))
                        lp.y = (downY + dy).roundToInt().coerceIn(safe.top, max(safe.top, safe.bottom - lp.height))
                        overlayRoot?.let { windowManager.updateViewLayout(it, lp) }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (moving) saveOverlayGeometry(true, lp, saveSize = false)
                        moving = false
                        revealChrome()
                        return true
                    }
                }
                return false
            }
        })

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

        val player = WebView(uiContext).apply {
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(LyricsJavascriptBridge(), "LobstaNativeLyrics")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = true

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != "file:///android_asset/lyrics_overlay.html" || overlayRoot == null) return
                    webReady = true
                    applyFontScale()
                    applyLyricColor()
                    applyLyricOffset()
                    applyTranslationMode()
                    applyCompactLayout()
                    applyBackgroundMode()
                    pendingSnapshot?.let { deliverToWeb(it) } ?: scheduleSnapshot()
                    mainHandler.removeCallbacks(engineTickRunnable)
                    mainHandler.post(engineTickRunnable)
                }
            }
            loadUrl("file:///android_asset/lyrics_overlay.html")
        }
        webView = player
        // The engine deliberately has no parent: only native views enter the overlay window.
        OverlayControls.attachFloating(root, dragArea, chrome)
        updateControlLayout()

        applyTouchMode()
        windowManager.addView(root, params)
        UsageAnalytics.setActive(UsageAnalytics.Surface.OVERLAY, true)
        revealChrome()
    }

    private fun applyOverlayRotationLayout() {
        val content = overlayContent ?: return
        val lp = windowParams ?: return
        content.layoutParams = windowGeometry.rotatedContentLayoutParams(lp.width, lp.height, overlayRotated)
        content.rotation = if (overlayRotated) 90f else 0f
        content.requestLayout()
    }

    private fun preferredCompactWidth(safe: Rect, fallback: Int): Int =
        if (prefs.contains(OverlayAppearance.PREF_WIDTH_PERCENT))
            (safe.width() * prefs.getInt(OverlayAppearance.PREF_WIDTH_PERCENT, 90).coerceIn(40, 100) / 100f).roundToInt()
        else fallback

    private fun adaptOverlayToDisplay() {
        val root = overlayRoot ?: return
        val lp = windowParams ?: return
        val (screenWidth, screenHeight) = windowGeometry.currentDisplaySize()
        val safe = windowGeometry.currentSafeDisplayBounds(overlayRoot)
        val previousWidth = max(1, lastDisplayWidth)
        val previousHeight = max(1, lastDisplayHeight)
        lp.preferredRefreshRate = windowGeometry.highestOverlayRefreshRate()
        val centerX = (lp.x + lp.width / 2f) / previousWidth
        val centerY = (lp.y + lp.height / 2f) / previousHeight
        val currentLogicalSize = windowGeometry.windowToLogicalSize(lp.width, lp.height, overlayRotated)

        val desiredSize = preferredCompactWidth(safe, prefs.getInt(PREF_COMPACT_WIDTH, currentLogicalSize.first)) to desiredCompactHeight()
        val fittedLogicalSize = windowGeometry.fittedLogicalOverlaySize(
            desiredSize.first,
            desiredSize.second,
            rotated = overlayRotated,
            safeBounds = safe
        )
        val fittedWindowSize = windowGeometry.logicalToWindowSize(fittedLogicalSize, overlayRotated)
        lp.width = fittedWindowSize.first
        lp.height = fittedWindowSize.second
        lp.x = (centerX * screenWidth - lp.width / 2f).roundToInt()
            .coerceIn(safe.left, max(safe.left, safe.right - lp.width))
        lp.y = (centerY * screenHeight - lp.height / 2f).roundToInt()
            .coerceIn(safe.top, max(safe.top, safe.bottom - lp.height))
        lastDisplayWidth = screenWidth
        lastDisplayHeight = screenHeight
        saveOverlayGeometry(compact, lp, saveSize = true)
        applyOverlayRotationLayout()
        applyHorizontalWebLayout()
        windowManager.updateViewLayout(root, lp)
    }

    private fun applyHorizontalWebLayout() {
        webView?.evaluateJavascript(
            "window.LobstaOverlay && window.LobstaOverlay.setHorizontalLayout($overlayRotated);",
            null
        )
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

    private fun updateChromeTint() {
        val lockedColor = AccentPrefs.overlayAccent(this)
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

    private fun overlayBackground(isCompact: Boolean): GradientDrawable = OverlayAppearance.background(
        this, backgroundMode, OverlayAppearance.parseColor(lyricColor),
        prefs.getInt(OverlayAppearance.PREF_BACKGROUND_OPACITY, OverlayAppearance.DEFAULT_BACKGROUND_OPACITY),
        prefs.getInt(OverlayAppearance.PREF_CORNER_RADIUS, OverlayAppearance.DEFAULT_CORNER_RADIUS),
    ).apply {
        if (!isCompact) {
            setStroke(dp(1), Color.argb(45, 255, 255, 255))
        }
    }

    private fun positionXPreferenceKey(targetCompact: Boolean): String =
        if (targetCompact) PREF_COMPACT_X else PREF_EXPANDED_X

    private fun positionYPreferenceKey(targetCompact: Boolean): String =
        if (targetCompact) PREF_COMPACT_Y else PREF_EXPANDED_Y

    private fun saveOverlayGeometry(
        targetCompact: Boolean,
        lp: WindowManager.LayoutParams,
        saveSize: Boolean
    ) {
        val editor = prefs.edit()
            .putInt("x", lp.x)
            .putInt("y", lp.y)
            .putInt(positionXPreferenceKey(targetCompact), lp.x)
            .putInt(positionYPreferenceKey(targetCompact), lp.y)
            .putBoolean("compact", targetCompact)
        if (saveSize) {
            val logicalSize = windowGeometry.windowToLogicalSize(lp.width, lp.height, overlayRotated)
            if (targetCompact) {
                editor.putInt(PREF_COMPACT_WIDTH, logicalSize.first)
                    .putInt("compact_height_v3", logicalSize.second)
            } else {
                editor.putInt("width", logicalSize.first)
                    .putInt("height", logicalSize.second)
            }
        }
        editor.apply()
    }

    private fun syncActiveVisualPreferences(targetCompact: Boolean) {
        backgroundMode = if (targetCompact) compactBackgroundMode else expandedBackgroundMode
        fontScalePercent = if (targetCompact) compactFontScalePercent else expandedFontScalePercent
        lyricColor = if (targetCompact) compactLyricColor else expandedLyricColor
    }

    private fun displayedVisualTargetIsCompact(): Boolean = true

    private fun applyBackgroundMode() {
        overlayContent?.background = overlayBackground(true)
    }

    private fun applyNativeAppearance() {
        val color = Color.parseColor(lyricColor)
        nativeLyrics?.setAppearance(fontScalePercent, color, translationMode, contextLines(true), contextLines(false), fontWeight,
            OverlayAppearance.textStyle(prefs))
        updateChromeTint()
    }

    private fun applyFontScale() {
        applyNativeAppearance()
        resizeCompactWindow()
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

    private fun applyCompactLayout() {
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
        applyOverlayRotationLayout()
        if (root.isAttachedToWindow) windowManager.updateViewLayout(root, lp)
        saveOverlayGeometry(true, lp, saveSize = true)
    }

    private fun applyLyricColor() {
        applyNativeAppearance()
        applyBackgroundMode()
    }

    private fun applyLyricOffset() {
        nativeLyrics?.setOffset(lyricOffsetMs)
        webView?.evaluateJavascript(
            "window.LobstaOverlay && window.LobstaOverlay.setLyricOffset($lyricOffsetMs);",
            null
        )
    }

    /** 自定义歌词被新增/编辑/删除后，让悬浮窗重新解析当前这首歌。 */
    fun reloadCustomLyrics() {
        mainHandler.post {
            webView?.evaluateJavascript(
                "window.LobstaOverlay && window.LobstaOverlay.reloadCustomLyrics();",
                null
            )
        }
    }

    /** Change the search revision without touching the original playback identity. */
    fun refreshSongSearch() {
        mainHandler.post {
            dispatchSnapshot()
        }
    }

    fun currentLyricOffsetMs(): Int = lyricOffsetMs

    fun isPositionLocked(): Boolean = positionLocked

    private fun setPositionLocked(locked: Boolean) {
        positionLocked = locked
        prefs.edit().putBoolean(PREF_POSITION_LOCKED, locked).apply()
        applyTouchMode()
        updateLockControl()
        if (overlayRoot != null) {
            notification.refresh(positionLocked)
        }
        announceOverlayState()
    }

    private fun applyTouchMode() {
        val lp = windowParams ?: return
        // Android 12+ blocks touches through opaque application overlays even when
        // NOT_TOUCHABLE is set. Window alpha, rather than background alpha, must
        // respect the system's maximum obscuring opacity.
        val maximumOpacity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
        } else null
        applyOverlayTouchMode(lp, positionLocked, maximumOpacity)
        overlayRoot?.takeIf { it.isAttachedToWindow }?.let {
            windowManager.updateViewLayout(it, lp)
        }
    }

    private fun applyTranslationMode() {
        applyNativeAppearance()
        val encoded = JSONObject.quote(translationMode)
        webView?.evaluateJavascript(
            "window.LobstaOverlay && window.LobstaOverlay.setTranslationMode($encoded);",
            null
        )
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

    private fun normalizedTranslationMode(value: String?): String = when (value) {
        TRANSLATION_ORIGINAL -> TRANSLATION_ORIGINAL
        TRANSLATION_TRANSLATED -> TRANSLATION_TRANSLATED
        else -> TRANSLATION_BILINGUAL
    }

    private fun normalizedFontScale(value: Int): Int =
        value.coerceIn(FONT_SCALE_MIN_PERCENT, FONT_SCALE_MAX_PERCENT)

    private fun updateLockControl() {
        lockButton?.let { button ->
            button.contentDescription = if (positionLocked) "解锁" else "锁定"
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

    private fun startMediaMonitor() {
        if (monitorStarted) {
            refreshActiveSessions()
            return
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent)
            monitorStarted = true
            refreshActiveSessions()
            mainHandler.removeCallbacks(sessionRefreshRunnable)
            mainHandler.postDelayed(sessionRefreshRunnable, 750L)
        } catch (_: SecurityException) {
            pendingSnapshot = JSONObject()
                .put("hasSession", false)
                .put("permissionRequired", true)
            pendingSnapshot?.let { deliverToWeb(it) }
            mainHandler.postDelayed({
                if (!monitorStarted) startMediaMonitor()
            }, 1_500L)
        }
    }

    private fun stopMediaMonitor() {
        mainHandler.removeCallbacks(sessionRefreshRunnable)
        if (monitorStarted) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener)
            } catch (_: Exception) {
            }
        }
        monitorStarted = false
        currentController?.unregisterCallback(controllerCallback)
        currentController = null
    }

    private fun refreshActiveSessions() {
        try {
            val controllers = sessionManager.getActiveSessions(listenerComponent)
            selectController(controllers)
        } catch (error: SecurityException) {
            pendingSnapshot = JSONObject()
                .put("hasSession", false)
                .put("permissionRequired", true)
            pendingSnapshot?.let { deliverToWeb(it) }
        }
    }

    private fun selectController(controllers: List<MediaController>) {
        val best = controllers
            .asSequence()
            .filter { it.packageName != packageName }
            .filter { isSupportedMusicPackage(it.packageName) }
            .maxByOrNull { mediaSessionScore(it, overlayTrackTitle(it.metadata).isNotBlank()) }

        if (best?.sessionToken == currentController?.sessionToken) {
            scheduleSnapshot()
            return
        }

        currentController?.unregisterCallback(controllerCallback)
        currentController = best
        playbackSnapshot.clearArtwork()
        best?.registerCallback(controllerCallback, mainHandler)
        scheduleSnapshot()
    }

    private fun isSupportedMusicPackage(packageName: String): Boolean {
        val p = packageName.lowercase(Locale.ROOT)
        val exactOrPrefix = listOf(
            "com.apple.android.music",
            "com.spotify.music",
            "com.netease.cloudmusic",
            "com.tencent.qqmusic",
            "com.kugou.android",
            "cn.kuwo.player",
            "com.kuwo.player",
            "com.google.android.apps.youtube.music",
            "com.amazon.mp3",
            "com.soundcloud.android",
            "deezer.android.app",
            "com.aspiro.tidal",
            "com.miui.player",
            "com.sec.android.app.music",
            "com.maxmpz.audioplayer",
            "in.krosbits.musicolet",
            "com.aimp.player",
            "com.fiio.music",
            "com.plexamp.android",
            "org.videolan.vlc"
        )
        return exactOrPrefix.any { p == it || p.startsWith("$it.") } ||
            (p.contains("music") && !p.contains("bilibili"))
    }

    private fun scheduleSnapshot() {
        if (snapshotScheduled) return
        snapshotScheduled = true
        mainHandler.postDelayed(dispatchRunnable, 35)
    }

    private fun dispatchSnapshot() {
        val controller = currentController
        val snapshot = if (controller == null) {
            JSONObject().put("hasSession", false).put("permissionRequired", false)
        } else {
            playbackSnapshot.build(controller)
        }
        pendingSnapshot = snapshot
        deliverToWeb(snapshot)
    }

    private fun updateNativePlayback(snapshot: JSONObject) {
        val track = snapshot.optString("track")
        val artist = snapshot.optString("artist")
        if (snapshot.optBoolean("permissionRequired") || !snapshot.optBoolean("hasSession") || track.isBlank()) {
            nativeTrack = ""
            nativeArtist = ""
            updateKeepScreenOn(false)
            nativeLyrics?.clear(if (snapshot.optBoolean("permissionRequired")) "请授予通知使用权" else "暂未播放")
            return
        }
        if (track != nativeTrack || artist != nativeArtist) {
            nativeTrack = track
            nativeArtist = artist
            nativeLyrics?.clear("正在获取歌词")
        }
        val playing = snapshot.optString("state") == "playing"
        updateKeepScreenOn(playing)
        nativeLyrics?.setPlayback(
            "$track\u0000$artist",
            snapshot.optLong("positionMs"), snapshot.optLong("durationMs"),
            playing, snapshot.optDouble("speed", 1.0).toFloat()
        )
    }

    /** 只在音乐播放时保持屏幕常亮；暂停或没有会话时释放。 */
    private fun updateKeepScreenOn(playing: Boolean) {
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

    private fun deliverToWeb(snapshot: JSONObject) {
        pendingSnapshot = snapshot
        updateNativePlayback(snapshot)
        if (!webReady) return
        // View.post waits for attachment; the engine deliberately never gets attached.
        mainHandler.post {
            if (!webReady) return@post
            webView?.evaluateJavascript(
                "window.LobstaOverlay && window.LobstaOverlay.updatePlayback($snapshot);",
                null
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        var instance: LyricsOverlayService? = null
            private set
        const val ACTION_START = "com.luoh.music.lrc.action.START_LYRICS_OVERLAY"
        const val ACTION_STOP = "com.luoh.music.lrc.action.STOP_LYRICS_OVERLAY"
        const val ACTION_LOCK = "com.luoh.music.lrc.action.LOCK_LYRICS_OVERLAY"
        const val ACTION_UNLOCK = "com.luoh.music.lrc.action.UNLOCK_LYRICS_OVERLAY"
        const val ACTION_REFRESH_NOTIFICATION = "com.luoh.music.lrc.action.REFRESH_LYRICS_NOTIFICATION"
        const val ACTION_STATE_CHANGED = "com.luoh.music.lrc.action.LYRICS_OVERLAY_STATE_CHANGED"
        const val ACTION_SET_BACKGROUND = "com.luoh.music.lrc.action.SET_LYRICS_BACKGROUND"
        const val ACTION_SET_FONT_SCALE = "com.luoh.music.lrc.action.SET_LYRICS_FONT_SCALE"
        const val ACTION_SET_FONT_WEIGHT = "com.luoh.music.lrc.action.SET_LYRICS_FONT_WEIGHT"
        const val ACTION_SET_LYRIC_COLOR = "com.luoh.music.lrc.action.SET_LYRIC_COLOR"
        const val ACTION_SET_LYRIC_OFFSET = "com.luoh.music.lrc.action.SET_LYRIC_OFFSET"
        const val ACTION_CLEAR_LYRIC_OFFSET_MEMORIES = "com.luoh.music.lrc.action.CLEAR_LYRIC_OFFSET_MEMORIES"
        const val ACTION_DELETE_LYRIC_OFFSET_MEMORY = "com.luoh.music.lrc.action.DELETE_LYRIC_OFFSET_MEMORY"
        const val ACTION_SET_CONTEXT = "com.luoh.music.lrc.action.SET_CONTEXT"
        const val ACTION_REFRESH_APPEARANCE = "com.luoh.music.lrc.action.REFRESH_OVERLAY_APPEARANCE"
        const val PREF_CONTEXT_BEFORE = "context_before"
        const val PREF_CONTEXT_AFTER = "context_after"
        const val PREF_POSITION_LOCKED = "position_locked"
        const val ACTION_SET_TRANSLATION_MODE = "com.luoh.music.lrc.action.SET_TRANSLATION_MODE"
        const val ACTION_REFRESH_MATCHES = "com.luoh.music.lrc.action.REFRESH_MATCHES"
        const val ACTION_REFRESH_ACCENT = "com.luoh.music.lrc.action.REFRESH_ACCENT"
        const val ACTION_RELOAD_CUSTOM_LYRICS = "com.luoh.music.lrc.action.RELOAD_CUSTOM_LYRICS"
        const val EXTRA_BACKGROUND_MODE = "background_mode"
        const val EXTRA_FONT_SCALE_PERCENT = "font_scale_percent"
        const val EXTRA_FONT_WEIGHT = "font_weight"
        const val EXTRA_LYRIC_COLOR = "lyric_color"
        const val EXTRA_TARGET_COMPACT = "target_compact"
        const val EXTRA_LYRIC_OFFSET_MS = "lyric_offset_ms"
        const val EXTRA_LYRIC_OFFSET_MEMORY_KEY = "lyric_offset_memory_key"
        const val EXTRA_TRANSLATION_MODE = "translation_mode"
        const val EXTRA_RUNNING = "running"
        const val PREFS_NAME = "lyrics_overlay_prefs"
        const val PREF_BACKGROUND_MODE = "background_mode"
        const val PREF_BACKGROUND_MODE_COMPACT = "background_mode_compact_v1"
        const val PREF_FONT_SCALE_PERCENT = "font_scale_percent"
        const val PREF_FONT_SCALE_COMPACT_PERCENT = "font_scale_compact_percent_v1"
        const val PREF_FONT_WEIGHT = "font_weight_compact_v1"
        const val PREF_LYRIC_COLOR = "lyric_color_v1"
        const val PREF_LYRIC_COLOR_COMPACT = "lyric_color_compact_v1"
        const val PREF_TRANSLATION_MODE = "translation_mode_v1"
        private const val PREF_COMPACT_WIDTH = "compact_width_v1"
        private const val PREF_EXPANDED_X = "expanded_x_v1"
        private const val PREF_EXPANDED_Y = "expanded_y_v1"
        private const val PREF_COMPACT_X = "compact_x_v1"
        private const val PREF_COMPACT_Y = "compact_y_v1"
        private const val PREF_OVERLAY_ROTATED = "overlay_rotated_v1"
        const val BACKGROUND_TRANSPARENT = "transparent"
        const val BACKGROUND_LOW = "low"
        const val BACKGROUND_MEDIUM = "medium"
        const val BACKGROUND_HIGH = "high"
        // A soft translucent surface keeps the default text readable over busy wallpapers.
        const val BACKGROUND_DEFAULT = BACKGROUND_LOW
        const val FONT_SCALE_MIN_PERCENT = 35
        const val FONT_SCALE_MAX_PERCENT = 150
        const val FONT_SCALE_DEFAULT_PERCENT = 100
        const val LYRIC_COLOR_DEFAULT = "#F4F6FA"
        const val TRANSLATION_ORIGINAL = "original"
        const val TRANSLATION_BILINGUAL = "bilingual"
        const val TRANSLATION_TRANSLATED = "translated"

        fun compactMinimumHeightDp(percent: Int): Int {
            val scale = percent.coerceIn(FONT_SCALE_MIN_PERCENT, FONT_SCALE_MAX_PERCENT) / 100f
            return (9.5f + 34.5f * scale).roundToInt().coerceIn(32, 64)
        }

        private const val LOG_TAG = "DesktopLyrics"
        internal const val CHANNEL_ID = "lobsta_lyrics_overlay"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
