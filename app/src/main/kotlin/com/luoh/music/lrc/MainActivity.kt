package com.luoh.music.lrc

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.luoh.music.lrc.data.lyrics.CustomLyricsStore
import com.luoh.music.lrc.data.lyrics.HomeLyricsLoader
import com.luoh.music.lrc.data.lyrics.LyricMatchMemory
import com.luoh.music.lrc.data.lyrics.LyricOffsetMemory
import com.luoh.music.lrc.data.lyrics.LyricPayload
import com.luoh.music.lrc.data.lyrics.SongSearchCorrections
import com.luoh.music.lrc.data.lyrics.TemporaryCache
import com.luoh.music.lrc.data.lyrics.parseLyricPayload
import com.luoh.music.lrc.data.translation.SupplementTranslation
import com.luoh.music.lrc.data.translation.translationPrefs
import com.luoh.music.lrc.diagnostics.UsageAnalytics
import com.luoh.music.lrc.lyrics.LyricDocument
import com.luoh.music.lrc.lyrics.LyricExporter
import com.luoh.music.lrc.overlay.OverlayNotificationAccess
import com.luoh.music.lrc.playback.NowPlaying
import com.luoh.music.lrc.playback.firstMetadataString
import com.luoh.music.lrc.playback.playbackPositionMs
import com.luoh.music.lrc.ui.agora.SettingsOverlayHost
import com.luoh.music.lrc.ui.home.ComposeHomeScreen
import com.luoh.music.lrc.ui.home.HomeLyricsPrefs
import com.luoh.music.lrc.ui.home.HomeMotionPrefs
import com.luoh.music.lrc.ui.home.HomePresentationState
import com.luoh.music.lrc.ui.home.HomeTranslationController
import com.luoh.music.lrc.ui.navigation.ComposeNavigator
import com.luoh.music.lrc.ui.theme.LyricsComposeTheme
import com.luoh.music.lrc.update.AppUpdateHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** MediaSession, lyric retrieval and permission owner; pages are rendered entirely in Compose. */
class MainActivity : ComponentActivity() {
    private lateinit var pageNavigator: ComposeNavigator
    private val home = HomePresentationState()
    private val overlayStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateOverlayState()
    }

    private val overlayPrefs by lazy {
        getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val offsetMemory by lazy { LyricOffsetMemory(overlayPrefs) }

    private val sessionManager by lazy {
        getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }
    private val listenerComponent by lazy {
        ComponentName(this, MediaListenerService::class.java)
    }

    private val lyricsLoader = HomeLyricsLoader(this)
    private val matchMemory by lazy { LyricMatchMemory(overlayPrefs) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private val translationScope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private val supplements by lazy { SupplementTranslation(this) }
    private var homeForeground = false
    private var baseLyricDocument = LyricDocument(emptyList(), true)
    private var currentLyricsCustom = false
    private val homeTranslation by lazy {
        HomeTranslationController(translationScope, { missing, onTranslationRequired ->
            val payload = org.json.JSONArray().apply {
                missing.forEach { put(JSONObject().put("id", it.id).put("text", it.text)) }
            }
            val translated = mutableMapOf<Int, String>()
            val outcome = supplements.translate(payload.toString(), onTranslationRequired = {
                withContext(Dispatchers.Main) { onTranslationRequired() }
            }) { row ->
                translated[row.optInt("id", -1)] = row.optString("text")
            }
            HomeTranslationController.Result(translated, outcome)
        }, { document -> home.setLyrics(document, currentLyricsCustom) }, { status ->
            home.setTranslationStatus(status)
            translationPrefs().edit()
                .putString("last_status", status).putString("last_track", currentTrack).apply()
        })
    }

    private var controller: MediaController? = null
    private var lastTrackKey = ""
    private var lyricFetchJob: Job? = null
    private var settingsRefreshJob: Job? = null
    @Volatile private var lyricRequestId = 0
    // 切歌时刻（elapsedRealtime 时基）。用于判断某份 PlaybackState 是新歌的还是旧歌的残留：
    // 只有 lastPositionUpdateTime >= 此值的状态才算“属于当前这首歌”，才允许墙钟外推位置。
    // 这样切歌瞬间播放器乐观上报 STATE_PLAYING（音频还没响）时不会把歌词冲到前面去。
    private var trackChangedAtElapsed = 0L
    // 刚开始监听（onResume / 切换会话）后的第一帧快照，此时的“换歌”其实是恢复现场，不该当作切歌卡住位置。
    private var firstSnapshotSinceMonitor = true
    private var activeLyric: LyricOffsetMemory.Song? = null
    // 当前生效的歌词负载签名，refreshLyricsSettings 用它判断本地来源（自定义/缓存）是否变化。
    private var currentPayloadSignature = ""
    // 当前生效的原始歌词文本（导出 .lrc 用）：applyLyricPayload 填充，切歌清空。
    private var currentLrcText = ""
    private var currentLrcTranslated = ""
    private var pendingLrcExport = ""
    private val overlayNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && LyricsOverlayService.isRunning) {
            startService(Intent(this, LyricsOverlayService::class.java).setAction(LyricsOverlayService.ACTION_REFRESH_NOTIFICATION))
        }
    }
    private val exportLrcLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val content = pendingLrcExport
        pendingLrcExport = ""
        if (uri != null) writeLrcExport(uri, content)
    }
    private var currentTrack = ""
    private var currentArtist = ""
    private var currentAlbum = ""
    private var currentDurationMs = 0L

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = pushSnapshot()
        override fun onPlaybackStateChanged(state: PlaybackState?) = pushSnapshot()
        override fun onSessionDestroyed() { controller = null; pushSnapshot() }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { refreshController() }

    // 高频进度推送
    private val progressTick = object : Runnable {
        override fun run() {
            pushProgressOnly()
            mainHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ioScope.launch { TemporaryCache.prune(applicationContext) }
        enableEdgeToEdge()
        home.actions = HomeActions()
        home.setAlignment(HomeLyricsPrefs.alignment(this))
        home.onTranslationSettings = { startActivity(Intent(this, TranslationSettingsActivity::class.java)) }
        pageNavigator = ComposeNavigator(savedInstanceState?.getBundle("navigation"))
        pageNavigator.onPresentationChanged = { open ->
            homeForeground = !open
            home.setActive(!open)
            if (!open) { updateGate(); pushSnapshot(); refreshLyricsSettings() }
        }
        setContent {
            LyricsComposeTheme {
                AppUpdateHost {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize().preferredFrameRate(FrameRateCategory.High)) {
                            ComposeHomeScreen(home,
                                onListenerPermission = {
                                    UsageAnalytics.permissionRequested(UsageAnalytics.Permission.NOTIFICATION)
                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                                onOverlayPermission = {
                                    UsageAnalytics.permissionRequested(UsageAnalytics.Permission.OVERLAY)
                                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                                },
                                onStartOverlay = ::tryStartOverlay)
                            SettingsOverlayHost(pageNavigator.isOpen, pageNavigator::dismiss,
                                onExitFinished = pageNavigator::onExitFinished) { pageNavigator.Content() }
                        }
                    }
                }
            }
        }
        if (!UsageAnalytics.preferences.value.noticeSeen) {
            if (UsageAnalytics.preferences.value.enabled) {
                Toast.makeText(this, "使用统计已开启，用于改进使用体验", Toast.LENGTH_SHORT).show()
            }
            UsageAnalytics.markNoticeSeen()
        }
    }

    override fun startActivity(intent: Intent) {
        if (!::pageNavigator.isInitialized || !pageNavigator.open(intent)) super.startActivity(intent)
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putBundle("navigation", pageNavigator.saveState())
        super.onSaveInstanceState(state)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, overlayStateReceiver, IntentFilter(LyricsOverlayService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(overlayStateReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        homeForeground = !pageNavigator.isOpen
        home.setActive(!pageNavigator.isOpen)
        updateGate()
        startSessionMonitor()
        mainHandler.post(progressTick)
        pushSnapshot()
        refreshLyricsSettings()
    }

    override fun onPause() {
        homeForeground = false
        homeTranslation.cancel()
        settingsRefreshJob?.cancel()
        mainHandler.removeCallbacks(progressTick)
        stopSessionMonitor()
        home.setActive(false)
        super.onPause()
    }

    override fun onDestroy() {
        homeTranslation.cancel()
        translationScope.coroutineContext[Job]?.cancel()
        ioScope.coroutineContext[Job]?.cancel()
        lyricsLoader.close()
        super.onDestroy()
    }

    // ---------- 权限浮层 ----------
    private fun hasListener(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun hasOverlay(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun updateOverlayState() {
        home.setOverlayState(LyricsOverlayService.isRunning)
    }

    private fun updateGate() {
        updateOverlayState()
        val listenerOk = hasListener()
        val overlayOk = hasOverlay()
        UsageAnalytics.permissions(listenerOk, overlayOk)
        home.gateVisible = !listenerOk
        home.gateStatus = if (!overlayOk) {
            "通知使用权用于获取歌曲信息，不读取通知正文。\n开启桌面歌词还需悬浮窗权限。"
        } else "通知使用权用于获取歌曲信息，不读取通知正文。"
    }

    private fun tryStartOverlay() {
        UsageAnalytics.feature(UsageAnalytics.Feature.OVERLAY_START)
        if (!hasListener()) {
            UsageAnalytics.permissionRequested(UsageAnalytics.Permission.NOTIFICATION)
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); return
        }
        if (!hasOverlay()) {
            UsageAnalytics.permissionRequested(UsageAnalytics.Permission.OVERLAY)
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
            ); return
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_START
            }
        )
        if (OverlayNotificationAccess.needsPermission(this)) {
            overlayNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        updateGate()
    }

    private fun openSettings() {
        home.setActive(false)
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // ---------- MediaSession ----------
    private var monitoring = false
    private fun startSessionMonitor() {
        if (monitoring) return
        if (!hasListener()) return
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, listenerComponent)
            monitoring = true
            firstSnapshotSinceMonitor = true
            refreshController()
        } catch (_: SecurityException) {
            // 无通知使用权
        }
    }

    private fun stopSessionMonitor() {
        if (!monitoring) return
        try { sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener) } catch (_: Exception) {}
        controller?.unregisterCallback(controllerCallback)
        controller = null
        monitoring = false
    }

    private fun refreshController() {
        val best = try {
            sessionManager.getActiveSessions(listenerComponent)
        } catch (_: SecurityException) { emptyList() }
            .asSequence()
            .filter { it.packageName != packageName }
            .maxByOrNull(NowPlaying::score)

        if (best?.sessionToken == controller?.sessionToken) { pushSnapshot(); return }
        controller?.unregisterCallback(controllerCallback)
        controller = best
        best?.registerCallback(controllerCallback, mainHandler)
        pushSnapshot()
    }

    private fun positionOf(state: PlaybackState?, duration: Long): Long {
        if (state == null) return 0L
        val now = SystemClock.elapsedRealtime()
        val updateTime = state.lastPositionUpdateTime
        // 切歌后短暂窗口内，若这份状态的位置更新还停留在切歌之前（旧歌残留），先按 0 处理，
        // 等播放器真正开始新歌再显示，避免歌词先跑一段又倒回的抖动。
        if (updateTime < trackChangedAtElapsed && now - trackChangedAtElapsed < TRACK_CHANGE_SETTLE_MS) {
            return 0L
        }
        return playbackPositionMs(state, duration, now)
    }

    private fun clearTrackState() {
        homeTranslation.cancel()
        baseLyricDocument = LyricDocument(emptyList(), true)
        home.setTranslationStatus("")
        if (lastTrackKey.isNotEmpty()) lyricRequestId++
        lastTrackKey = ""
        currentTrack = ""
        currentArtist = ""
        currentAlbum = ""
        currentDurationMs = 0L
        currentPayloadSignature = ""
        currentLrcText = ""
        currentLrcTranslated = ""
        activeLyric = null
        if (!LyricsOverlayService.isRunning) {
            offsetMemory.rememberActiveSong(null)
        }
    }

    private fun pushSnapshot() {
        val c = controller
        if (c == null) {
            clearTrackState()
            home.setSnapshot(HomePresentationState.Snapshot())
            return
        }
        val md = c.metadata
        val pb = c.playbackState
        val title = firstMetadataString(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = firstMetadataString(
            md,
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_AUTHOR,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
        )
        val album = firstMetadataString(md, MediaMetadata.METADATA_KEY_ALBUM)
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        // 先检测换歌（在算位置之前），以便正确设置 trackChangedAtElapsed
        val key = "$title\u0000$artist\u0000$album"
        val trackChanged = title.isNotBlank() && key != lastTrackKey
        if (trackChanged) {
            homeTranslation.cancel()
            baseLyricDocument = LyricDocument(emptyList(), true)
            home.setTranslationStatus("")
            currentLrcText = ""
            currentLrcTranslated = ""
            lastTrackKey = key
            currentTrack = title
            currentArtist = artist
            currentAlbum = album
            currentDurationMs = duration.coerceAtLeast(0L)
            // 第一帧（恢复现场）不算切歌，避免把正在播放的位置误判成旧歌残留而卡在 0
            trackChangedAtElapsed = if (firstSnapshotSinceMonitor) 0L else SystemClock.elapsedRealtime()
        } else if (title.isBlank()) {
            clearTrackState()
        }
        firstSnapshotSinceMonitor = false

        val playing = pb?.state == PlaybackState.STATE_PLAYING
        if (playing) UsageAnalytics.playerUsed(c.packageName)
        val cover = coverBitmap(md)
        val actions = pb?.actions ?: 0L
        val canPrev = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L
        val canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L
        home.setSnapshot(
            HomePresentationState.Snapshot(title, artist, cover, positionOf(pb, duration),
                duration.coerceAtLeast(0L), playing, pb?.playbackSpeed ?: 1f, canPrev, canNext),
            forcePosition = trackChanged
        )

        // 换歌 → 拉歌词
        if (trackChanged) {
            fetchLyrics(title, artist, album, duration)
        }
    }

    private fun pushProgressOnly() {
        val c = controller ?: return
        val pb = c.playbackState ?: return
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val playing = pb.state == PlaybackState.STATE_PLAYING
        home.setProgress(positionOf(pb, duration), playing, pb.playbackSpeed)
    }

    private fun coverBitmap(md: MediaMetadata?): Bitmap? =
        md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    private fun applyLyricPayload(payload: LyricPayload, document: LyricDocument) {
        currentPayloadSignature = payload.signature
        currentLrcText = payload.lyrics
        currentLrcTranslated = payload.translated
        activeLyric = payload.source.takeIf { it.isNotBlank() }?.let { source ->
            // Match the overlay's original playback identity, including the same lowercase normalization.
            val identity = "$currentTrack\u0000$currentArtist".trim().lowercase(java.util.Locale.ROOT)
            LyricOffsetMemory.Song(identity, source, currentTrack, currentArtist)
        }
        currentLyricsCustom = payload.source == CustomLyricsStore.SOURCE
        baseLyricDocument = document
        home.setLyrics(baseLyricDocument, currentLyricsCustom)
        if (homeForeground && document.lines.isNotEmpty()) UsageAnalytics.lyricsDisplayed(UsageAnalytics.Surface.APP)
        refreshHomeTranslation()
        applyLyricOffset()
    }

    private fun applyLyricOffset() {
        val song = activeLyric ?: return
        offsetMemory.rememberActiveSong(song)
        home.setLyricOffset(offsetMemory.read(song))
    }

    private fun fetchLyrics(track: String, artist: String, album: String, durationMs: Long, forceNetwork: Boolean = false, refreshOverlay: Boolean = false) {
        val reqId = ++lyricRequestId
        val search = SongSearchCorrections.resolve(this, track, artist)

        lyricFetchJob?.cancel()
        settingsRefreshJob?.cancel()
        lyricFetchJob = ioScope.launch {
            // Local preference decoding, LRC parsing and cache bookkeeping never block navigation.
            if (!forceNetwork) {
                val local = lyricsLoader.readLocal(track, artist, album, durationMs)
                if (local != null) {
                    val document = parseLyricPayload(local)
                    withContext(Dispatchers.Main) {
                        if (reqId == lyricRequestId) {
                            applyLyricPayload(local, document)
                            if (refreshOverlay) LyricsOverlayService.instance?.reloadCustomLyrics()
                        }
                    }
                    if (reqId == lyricRequestId && local.source != CustomLyricsStore.SOURCE) {
                        recordMatch(track, artist, album, durationMs, local)
                    }
                    return@launch
                }
            }
            withContext(Dispatchers.Main) { if (reqId == lyricRequestId) home.showLoading() }
            val payload = lyricsLoader.resolve(search, album, durationMs, forceNetwork)
            if (reqId != lyricRequestId) return@launch
            recordMatch(track, artist, album, durationMs, payload)
            val document = parseLyricPayload(payload)
            withContext(Dispatchers.Main) {
                if (reqId != lyricRequestId) return@withContext
                applyLyricPayload(payload, document)
                if (refreshOverlay) LyricsOverlayService.instance?.reloadCustomLyrics()
            }
        }
    }

    /**
     * 回到主页时重新对齐歌词设置：
     *   - 重新应用翻译模式
     *   - 若本地来源（自定义歌词）有增删导致负载签名变化，重新上屏
     *   - 否则只重新应用偏移（用户可能刚在偏移记忆页改过）
     */
    private fun refreshLyricsSettings() {
        UsageAnalytics.configuration(this)
        // 翻译模式
        val mode = overlayPrefs.getString(
            LyricsOverlayService.PREF_TRANSLATION_MODE, LyricsOverlayService.TRANSLATION_BILINGUAL
        ) ?: LyricsOverlayService.TRANSLATION_BILINGUAL
        home.setTranslationMode(mode)
        home.setMotionStyle(HomeMotionPrefs.style(this))
        home.setAlignment(HomeLyricsPrefs.alignment(this))

        if (currentTrack.isBlank()) return
        applyLyricOffset()
        refreshHomeTranslation()
        if (lyricFetchJob?.isActive == true) return
        val request = lyricRequestId
        val track = currentTrack
        val artist = currentArtist
        val album = currentAlbum
        val duration = currentDurationMs
        val loadedSignature = currentPayloadSignature
        val loadedDocument = baseLyricDocument
        settingsRefreshJob?.cancel()
        settingsRefreshJob = ioScope.launch {
            val local = lyricsLoader.readLocal(track, artist, album, duration)
            val document = local?.let {
                if (it.signature == loadedSignature) loadedDocument else parseLyricPayload(it)
            }
            withContext(Dispatchers.Main) {
                if (request != lyricRequestId || track != currentTrack || !homeForeground) return@withContext
                when {
                    local != null && local.signature != currentPayloadSignature ->
                        applyLyricPayload(local, requireNotNull(document))
                    local == null && activeLyric?.source == CustomLyricsStore.SOURCE ->
                        fetchLyrics(currentTrack, currentArtist, currentAlbum, currentDurationMs)
                    else -> applyLyricOffset()
                }
                refreshHomeTranslation()
            }
        }
    }

    private fun refreshHomeTranslation() {
        if (!homeForeground) return
        if (baseLyricDocument.lines.isNotEmpty()) UsageAnalytics.lyricsDisplayed(UsageAnalytics.Surface.APP)
        val display = overlayPrefs.getString(LyricsOverlayService.PREF_TRANSLATION_MODE,
            LyricsOverlayService.TRANSLATION_BILINGUAL).orEmpty()
        val translationPrefs = translationPrefs()
        homeTranslation.update(lastTrackKey, baseLyricDocument, display,
            translationPrefs.getString("mode", "off").orEmpty())
    }

    private fun recordMatch(
        track: String,
        artist: String,
        album: String,
        durationMs: Long,
        payload: LyricPayload,
    ) {
        if (track.isBlank() || payload.source.isBlank() || payload.lyrics.isBlank()) return
        runCatching {
            val revision = SongSearchCorrections.resolve(this, track, artist).revision
            matchMemory.recordAutomaticMatch(track, artist, album, durationMs, revision, payload)
        }
    }

    // ---------- Player actions ----------
    private inner class HomeActions : HomePresentationState.Actions {
        override fun toggleOverlay() {
            if (LyricsOverlayService.isRunning) {
                UsageAnalytics.feature(UsageAnalytics.Feature.OVERLAY_STOP)
                stopService(Intent(this@MainActivity, LyricsOverlayService::class.java))
            } else {
                tryStartOverlay()
            }
            updateOverlayState()
        }

        override fun openSettings() = this@MainActivity.openSettings()

        override fun seekTo(positionMs: Long) {
            val c = controller ?: return
            UsageAnalytics.feature(UsageAnalytics.Feature.SEEK)
            runCatching { c.transportControls.seekTo(positionMs.coerceAtLeast(0L)) }
            mainHandler.postDelayed({ pushSnapshot() }, 180)
        }

        override fun togglePlay() {
            val c = controller ?: return
            UsageAnalytics.feature(UsageAnalytics.Feature.PLAY_PAUSE)
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            runCatching {
                if (playing) c.transportControls.pause() else c.transportControls.play()
            }
            mainHandler.postDelayed({ pushSnapshot() }, 120)
        }

        override fun skipPrev() {
            val c = controller ?: return
            UsageAnalytics.feature(UsageAnalytics.Feature.PREVIOUS)
            runCatching { c.transportControls.skipToPrevious() }
            mainHandler.postDelayed({ pushSnapshot() }, 180)
        }

        override fun skipNext() {
            val c = controller ?: return
            UsageAnalytics.feature(UsageAnalytics.Feature.NEXT)
            runCatching { c.transportControls.skipToNext() }
            mainHandler.postDelayed({ pushSnapshot() }, 180)
        }

        override fun editCustomLyrics() {
            if (currentTrack.isNotBlank()) {
                val intent = if (!currentLyricsCustom && currentLrcText.isNotBlank()) {
                    CustomLyricsEditActivity.cachedIntent(this@MainActivity, currentTrack, currentArtist)
                } else CustomLyricsEditActivity.intent(this@MainActivity, currentTrack, currentArtist)
                startActivity(intent)
            } else {
                manageCustomLyrics()
            }
        }

        override fun retryLyrics() {
            if (currentTrack.isBlank()) return
            UsageAnalytics.feature(UsageAnalytics.Feature.RETRY_LYRICS)
            fetchLyrics(currentTrack, currentArtist, currentAlbum, currentDurationMs, forceNetwork = true)
        }

        override fun correctSongSearch(track: String, artist: String, title: String?, searchArtist: String) {
            if (track.isBlank()) return
            SongSearchCorrections.save(this@MainActivity, track, artist, title, searchArtist)
            if (track == currentTrack && artist == currentArtist) {
                val hasCustom = CustomLyricsStore.find(this@MainActivity, track, artist) != null
                fetchLyrics(currentTrack, currentArtist, currentAlbum, currentDurationMs, forceNetwork = !hasCustom, refreshOverlay = true)
            }
            LyricsOverlayService.instance?.refreshSongSearch()
            Toast.makeText(this@MainActivity, if (title == null) "已恢复原名" else "已保存", Toast.LENGTH_SHORT).show()
        }

        override fun manageCustomLyrics() {
            startActivity(Intent(this@MainActivity, CustomLyricsManagerActivity::class.java))
        }

        override fun manageLyricSources() {
            startActivity(Intent(this@MainActivity, LyricSourceManagerActivity::class.java))
        }

        override fun exportLrc() {
            val lrc = currentLrcText
            if (currentTrack.isBlank() || lrc.isBlank()) {
                Toast.makeText(this@MainActivity, "暂无可导出的歌词", Toast.LENGTH_SHORT).show()
                return
            }
            pendingLrcExport = LyricExporter.buildLrc(currentTrack, currentArtist, lrc, currentLrcTranslated)
            UsageAnalytics.feature(UsageAnalytics.Feature.EXPORT_LRC)
            exportLrcLauncher.launch(LyricExporter.fileName(currentTrack, currentArtist))
        }
    }

    private fun writeLrcExport(uri: android.net.Uri, content: String) {
        ioScope.launch {
            val saved = content.isNotBlank() && runCatching {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) } != null
            }.getOrDefault(false)
            UsageAnalytics.exportResult(saved)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, if (saved) "LRC 歌词已保存" else "保存失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        // 切歌后的稳定窗口：这段时间内若拿到的还是旧歌残留状态，位置先按 0 处理，避免歌词抖动。
        private const val TRACK_CHANGE_SETTLE_MS = 1500L
    }
}
