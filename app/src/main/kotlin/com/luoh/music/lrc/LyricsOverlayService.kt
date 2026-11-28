package com.luoh.music.lrc

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.LocaleList
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.luoh.music.lrc.data.lyrics.LyricOffsetMemory
import com.luoh.music.lrc.diagnostics.UsageAnalytics
import com.luoh.music.lrc.overlay.OverlayAppearance
import com.luoh.music.lrc.overlay.OverlayLyricsEngine
import com.luoh.music.lrc.overlay.OverlayNotification
import com.luoh.music.lrc.overlay.OverlayPlaybackSnapshot
import com.luoh.music.lrc.overlay.OverlayWindow
import com.luoh.music.lrc.overlay.overlayTrackTitle
import com.luoh.music.lrc.playback.mediaSessionScore
import org.json.JSONObject
import java.util.Locale
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
    private val offsetMemory by lazy { LyricOffsetMemory(prefs) }
    private val notification by lazy { OverlayNotification(this) }
    private val sessionManager by lazy { getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager }
    private val playbackSnapshot = OverlayPlaybackSnapshot(this)
    private val listenerComponent by lazy { ComponentName(this, MediaListenerService::class.java) }
    private val overlayWindow by lazy {
        OverlayWindow(this, prefs, mainHandler, onLock = { setPositionLocked(true) }, onClose = { stopSelf() })
    }
    private val lyricsEngine by lazy {
        OverlayLyricsEngine(
            this, prefs, mainHandler, overlayWindow, offsetMemory,
            mediaController = { currentController },
            scheduleSnapshot = ::scheduleSnapshot,
            onReady = ::initializeLyricsEngine,
            onActiveLyric = ::rememberActiveLyric,
        )
    }

    private var lyricOffsetMs = 0
    private var activeLyric: LyricOffsetMemory.Song? = null
    private var translationMode = TRANSLATION_BILINGUAL
    private var monitorStarted = false
    private var currentController: MediaController? = null
    private var snapshotScheduled = false

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

    private var overlayLocales: LocaleList? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlayLocales != newConfig.locales) {
            overlayLocales = newConfig.locales
            overlayWindow.refreshLocalizedText()
            notification.createChannel()
            if (overlayWindow.isOpen) notification.refresh(overlayWindow.positionLocked)
            LyricsTileService.requestUpdate(this)
        }
        mainHandler.post { adaptOverlayToDisplay() }
        mainHandler.postDelayed({ adaptOverlayToDisplay() }, 220L)
    }

    override fun onCreate() {
        super.onCreate()
        overlayLocales = resources.configuration.locales
        UsageAnalytics.configuration(this)
        lyricsEngine.pruneTemporaryCache()
        instance = this
        isRunning = true
        announceOverlayState()
        overlayWindow.restorePreferences()
        lyricOffsetMs = offsetMemory.displayedOffset()
        translationMode = normalizedTranslationMode(prefs.getString(PREF_TRANSLATION_MODE, TRANSLATION_BILINGUAL))
        overlayWindow.setTranslationMode(translationMode)
        notification.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH_NOTIFICATION && overlayWindow.isOpen) {
            notification.refresh(overlayWindow.positionLocked)
            return START_STICKY
        }
        if (intent?.action == ACTION_REFRESH_ACCENT) {
            overlayWindow.updateChromeTint()
            return START_STICKY
        }
        if (intent?.action == ACTION_REFRESH_MATCHES) {
            lyricsEngine.refreshMatches()
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
            if (overlayWindow.isOpen) return START_STICKY
        }

        if (intent?.action == ACTION_SET_CONTEXT) {
            overlayWindow.refreshContentLayout()
            if (overlayWindow.isOpen) return START_STICKY
        }

        if (intent?.action == ACTION_REFRESH_APPEARANCE) {
            overlayWindow.refreshAppearance()
            adaptOverlayToDisplay()
            if (overlayWindow.isOpen) return START_STICKY
        }

        if (intent?.action == ACTION_SET_BACKGROUND) {
            overlayWindow.setBackground(
                intent.getBooleanExtra(EXTRA_TARGET_COMPACT, false),
                intent.getStringExtra(EXTRA_BACKGROUND_MODE),
            )
            if (overlayWindow.isOpen) return START_STICKY
        }

        if (intent?.action == ACTION_SET_FONT_WEIGHT) {
            overlayWindow.setFontWeight(intent.getIntExtra(EXTRA_FONT_WEIGHT, OverlayAppearance.DEFAULT_FONT_WEIGHT))
            if (overlayWindow.isOpen) return START_STICKY
        }

        if (intent?.action == ACTION_SET_FONT_SCALE) {
            overlayWindow.setFontScale(
                intent.getBooleanExtra(EXTRA_TARGET_COMPACT, false),
                intent.getIntExtra(EXTRA_FONT_SCALE_PERCENT, FONT_SCALE_DEFAULT_PERCENT),
            )
            if (overlayWindow.isOpen) return START_STICKY
        }

        if (intent?.action == ACTION_SET_LYRIC_COLOR) {
            overlayWindow.setLyricColor(
                intent.getBooleanExtra(EXTRA_TARGET_COMPACT, false),
                intent.getStringExtra(EXTRA_LYRIC_COLOR),
            )
            if (overlayWindow.isOpen) return START_STICKY
        }

        if (intent?.action == ACTION_SET_LYRIC_OFFSET) {
            val expectedKey = intent.getStringExtra(EXTRA_LYRIC_OFFSET_MEMORY_KEY)
            if (expectedKey != null && expectedKey != activeLyric?.key) {
                // 设置页已保存目标歌曲的记忆；切歌后不要把这条消息套到另一首歌上。
                if (!overlayWindow.isOpen) stopSelf()
                return if (!overlayWindow.isOpen) START_NOT_STICKY else START_STICKY
            }
            lyricOffsetMs = intent.getIntExtra(EXTRA_LYRIC_OFFSET_MS, 0)
                .coerceIn(LyricOffsetMemory.MIN_MS, LyricOffsetMemory.MAX_MS)
            offsetMemory.save(activeLyric, lyricOffsetMs, updateDisplayedOffset = true)
            applyLyricOffset()
            if (overlayWindow.isOpen) return START_STICKY
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
            if (overlayWindow.isOpen) return START_STICKY
        }

        if (intent?.action == ACTION_CLEAR_LYRIC_OFFSET_MEMORIES) {
            lyricOffsetMs = 0
            offsetMemory.clear()
            applyLyricOffset()
            announceOverlayState()
            if (overlayWindow.isOpen) return START_STICKY
        }

        if (intent?.action == ACTION_SET_TRANSLATION_MODE) {
            translationMode = normalizedTranslationMode(
                intent.getStringExtra(EXTRA_TRANSLATION_MODE)
            )
            prefs.edit().putString(PREF_TRANSLATION_MODE, translationMode).apply()
            applyTranslationMode()
            if (overlayWindow.isOpen) return START_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        notification.start(overlayWindow.positionLocked)
        if (!overlayWindow.isOpen) {
            overlayWindow.show()
            lyricsEngine.start()
            UsageAnalytics.setActive(UsageAnalytics.Surface.OVERLAY, true)
        }
        startMediaMonitor()
        return START_STICKY
    }

    override fun onDestroy() {
        UsageAnalytics.setActive(UsageAnalytics.Surface.OVERLAY, false)
        instance = null
        mainHandler.removeCallbacksAndMessages(null)
        stopMediaMonitor()
        lyricsEngine.close()
        overlayWindow.close()
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

    fun refreshSupplementTranslation() = lyricsEngine.refreshSupplementTranslation()

    fun reloadCustomLyrics() = lyricsEngine.reloadCustomLyrics()

    /** Change the search revision without touching the original playback identity. */
    fun refreshSongSearch() {
        mainHandler.post { dispatchSnapshot() }
    }

    fun currentLyricOffsetMs(): Int = lyricOffsetMs

    fun isPositionLocked(): Boolean = overlayWindow.positionLocked

    private fun setPositionLocked(locked: Boolean) {
        overlayWindow.setPositionLocked(locked)
        if (overlayWindow.isOpen) notification.refresh(locked)
        announceOverlayState()
    }

    private fun adaptOverlayToDisplay() {
        if (!overlayWindow.isOpen) return
        overlayWindow.adaptToDisplay()
        lyricsEngine.resetHorizontalLayout()
    }

    private fun initializeLyricsEngine() {
        overlayWindow.refreshContentLayout()
        applyLyricOffset()
        applyTranslationMode()
        overlayWindow.refreshAppearance()
    }

    private fun rememberActiveLyric(song: LyricOffsetMemory.Song, offsetMs: Int) {
        activeLyric = song
        lyricOffsetMs = offsetMs
        overlayWindow.setOffset(offsetMs)
        offsetMemory.rememberActiveSong(song, offsetMs)
        announceOverlayState()
    }

    private fun applyLyricOffset() {
        overlayWindow.setOffset(lyricOffsetMs)
        lyricsEngine.setOffset(lyricOffsetMs)
    }

    private fun applyTranslationMode() {
        overlayWindow.setTranslationMode(translationMode)
        lyricsEngine.setTranslationMode(translationMode)
    }

    private fun normalizedTranslationMode(value: String?): String = when (value) {
        TRANSLATION_ORIGINAL, TRANSLATION_TRANSLATED -> value
        else -> TRANSLATION_BILINGUAL
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
            lyricsEngine.dispatchPlayback(JSONObject()
                .put("hasSession", false)
                .put("permissionRequired", true))
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
            lyricsEngine.dispatchPlayback(JSONObject()
                .put("hasSession", false)
                .put("permissionRequired", true))
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
        lyricsEngine.dispatchPlayback(snapshot)
    }

    companion object {
        var instance: LyricsOverlayService? = null
            private set

        internal fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LyricsOverlayService::class.java).setAction(ACTION_START))
        }

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

        internal const val CHANNEL_ID = "lobsta_lyrics_overlay"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
