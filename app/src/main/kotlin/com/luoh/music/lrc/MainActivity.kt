package com.luoh.music.lrc

import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
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
import android.view.View
import android.view.ViewGroup
import android.graphics.Bitmap
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Native Material home page. This activity owns MediaSession, lyric retrieval/cache and permissions;
 * HomeLyricsView owns the native player controls, timed lyric rendering and more menu.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var home: HomeLyricsView
    private lateinit var gate: View
    private lateinit var gateStatus: MaterialTextView
    private lateinit var themeTransition: ThemeTransition
    private var appliedNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED
    private val overlayStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateOverlayState()
    }

    private val overlayPrefs by lazy {
        getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val appPrefs by lazy {
        getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE)
    }

    private val sessionManager by lazy {
        getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }
    private val listenerComponent by lazy {
        ComponentName(this, MediaListenerService::class.java)
    }

    private val repository = DirectLyricsRepository()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private val translationScope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private val supplements by lazy { SupplementTranslation(this) }
    private var homeForeground = false
    private var baseLyricDocument = LyricDocument(emptyList(), true)
    private var currentLyricsCustom = false
    private val homeTranslation by lazy {
        HomeTranslationController(translationScope, { missing ->
            val payload = org.json.JSONArray().apply {
                missing.forEach { put(JSONObject().put("id", it.id).put("text", it.text)) }
            }
            val translated = mutableMapOf<Int, String>()
            supplements.translate(payload.toString()) { row ->
                translated[row.optInt("id", -1)] = row.optString("text")
            }
            translated
        }, { document -> home.setLyrics(document, currentLyricsCustom) }, { status ->
            home.setTranslationStatus(status)
            getSharedPreferences("supplement_translation", Context.MODE_PRIVATE).edit()
                .putString("last_status", status).putString("last_track", currentTrack).apply()
        })
    }

    private var controller: MediaController? = null
    private var lastTrackKey = ""
    @Volatile private var lyricRequestId = 0
    // 切歌时刻（elapsedRealtime 时基）。用于判断某份 PlaybackState 是新歌的还是旧歌的残留：
    // 只有 lastPositionUpdateTime >= 此值的状态才算“属于当前这首歌”，才允许墙钟外推位置。
    // 这样切歌瞬间播放器乐观上报 STATE_PLAYING（音频还没响）时不会把歌词冲到前面去。
    private var trackChangedAtElapsed = 0L
    // 刚开始监听（onResume / 切换会话）后的第一帧快照，此时的“换歌”其实是恢复现场，不该当作切歌卡住位置。
    private var firstSnapshotSinceMonitor = true
    // 当前这首歌用于 per-song 偏移的身份信息（歌词就绪后填），供设置页在悬浮窗未运行时对齐偏移记忆。
    private var currentLyricIdentity = ""
    private var currentLyricSource = ""
    private var currentLyricTitle = ""
    private var currentLyricArtist = ""
    // 当前生效的歌词负载签名，refreshLyricsSettings 用它判断本地来源（自定义/缓存）是否变化。
    private var currentPayloadSignature = ""
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
        // 主题先于 setContentView
        ThemePrefs.apply(appPrefs.getString(ThemePrefs.KEY, ThemePrefs.FOLLOW))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        home = findViewById(R.id.lyric_home)
        home.actions = HomeActions()
        home.onTranslationSettings = { startActivity(Intent(this, TranslationSettingsActivity::class.java)) }
        gate = findViewById(R.id.gate_overlay)
        gateStatus = findViewById(R.id.gate_status)
        appliedNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        themeTransition = ThemeTransition(findViewById(android.R.id.content))
        ThemePrefs.updateSystemBars(this)

        // 权限浮层按钮
        findViewById<MaterialButton>(R.id.gate_listener).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.gate_overlay_perm).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            }
        }
        findViewById<MaterialButton>(R.id.gate_start).setOnClickListener { tryStartOverlay() }
        findViewById<MaterialButton>(R.id.gate_settings_link).setOnClickListener { openSettings() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night == appliedNightMode || !::themeTransition.isInitialized) {
            super.onConfigurationChanged(newConfig)
            return
        }
        themeTransition.capture()
        appliedNightMode = night
        super.onConfigurationChanged(newConfig)
        theme.applyStyle(R.style.Theme_DesktopLyrics, true)
        home.applyTheme()
        applyGateTheme(gate)
        ThemePrefs.updateSystemBars(this)
        themeTransition.finish()
    }

    private fun applyGateTheme(view: View) {
        when (view) {
            is MaterialButton -> {
                val primary = view.id == R.id.gate_start
                view.setTextColor(ContextCompat.getColor(this, when {
                    primary -> R.color.text_on_accent
                    view.id == R.id.gate_settings_link -> R.color.text_secondary
                    else -> R.color.text_primary
                }))
                view.backgroundTintList = ColorStateList.valueOf(
                    if (primary) ContextCompat.getColor(this, R.color.accent) else android.graphics.Color.TRANSPARENT)
                view.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.app_line))
                view.rippleColor = ContextCompat.getColorStateList(this, R.color.native_ripple)
            }
            is MaterialTextView -> view.setTextColor(ContextCompat.getColor(this,
                if (view.id == R.id.gate_status) R.color.text_secondary else R.color.text_primary))
        }
        if (view === gate) view.setBackgroundColor(ContextCompat.getColor(this, R.color.app_bg))
        if (view is ViewGroup) for (index in 0 until view.childCount) applyGateTheme(view.getChildAt(index))
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
        homeForeground = true
        ThemePrefs.apply(appPrefs.getString(ThemePrefs.KEY, ThemePrefs.FOLLOW))
        home.applyTheme()
        home.setActive(true)
        updateGate()
        startSessionMonitor()
        mainHandler.post(progressTick)
        pushSnapshot()
        refreshLyricsSettings()
    }

    override fun onPause() {
        homeForeground = false
        homeTranslation.cancel()
        mainHandler.removeCallbacks(progressTick)
        stopSessionMonitor()
        home.setActive(false)
        super.onPause()
    }

    override fun onDestroy() {
        if (::themeTransition.isInitialized) themeTransition.dispose()
        homeTranslation.cancel()
        translationScope.coroutineContext[Job]?.cancel()
        ioScope.coroutineContext[Job]?.cancel()
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
        // 只要有通知使用权就能读歌显示歌词页；两者都齐才能开悬浮窗
        if (listenerOk) {
            gate.visibility = View.GONE
        } else {
            gate.visibility = View.VISIBLE
            gateStatus.text = when {
                !listenerOk && !overlayOk -> "通知使用权用于获取正在播放的歌曲，不读取聊天通知正文。\n开启桌面歌词还需悬浮窗权限；授权后点底部中控台的歌词图标即可开启。"
                !listenerOk -> "通知使用权用于获取正在播放的歌曲，不读取聊天通知正文。"
                else -> "准备就绪"
            }
        }
    }

    private fun tryStartOverlay() {
        if (!hasListener()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); return
        }
        if (!hasOverlay()) {
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
            .maxByOrNull { score(it) }

        if (best?.sessionToken == controller?.sessionToken) { pushSnapshot(); return }
        controller?.unregisterCallback(controllerCallback)
        controller = best
        best?.registerCallback(controllerCallback, mainHandler)
        pushSnapshot()
    }

    private fun score(c: MediaController): Int {
        val s = when (c.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> 1000
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> 800
            PlaybackState.STATE_PAUSED -> 600
            else -> 100
        }
        val hasTitle = !c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
        return s + if (hasTitle) 100 else 0
    }

    private fun positionOf(state: PlaybackState?, duration: Long): Long {
        if (state == null) return 0L
        var pos = state.position.coerceAtLeast(0L)
        val now = SystemClock.elapsedRealtime()
        val updateTime = state.lastPositionUpdateTime
        // 切歌后短暂窗口内，若这份状态的位置更新还停留在切歌之前（旧歌残留），先按 0 处理，
        // 等播放器真正开始新歌再显示，避免歌词先跑一段又倒回的抖动。
        if (updateTime < trackChangedAtElapsed && now - trackChangedAtElapsed < TRACK_CHANGE_SETTLE_MS) {
            return 0L
        }
        if (state.state == PlaybackState.STATE_PLAYING && state.playbackSpeed > 0f && updateTime > 0L) {
            val elapsed = (now - updateTime).coerceAtLeast(0L)
            pos += (elapsed * state.playbackSpeed).toLong()
        }
        return if (duration > 0) pos.coerceAtMost(duration) else pos
    }

    private fun firstString(md: MediaMetadata?, vararg keys: String): String {
        if (md == null) return ""
        for (k in keys) md.getString(k)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return ""
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
        currentLyricIdentity = ""
        currentLyricSource = ""
        if (!LyricsOverlayService.isRunning) {
            LyricsOverlayService.rememberActiveLyric(overlayPrefs, "", "", "", "")
        }
    }

    private fun pushSnapshot() {
        val c = controller
        if (c == null) {
            clearTrackState()
            home.setSnapshot(HomeLyricsView.Snapshot())
            return
        }
        val md = c.metadata
        val pb = c.playbackState
        val title = firstString(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = firstString(
            md,
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_AUTHOR,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
        )
        val album = firstString(md, MediaMetadata.METADATA_KEY_ALBUM)
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        // 先检测换歌（在算位置之前），以便正确设置 trackChangedAtElapsed
        val key = "$title\u0000$artist\u0000$album"
        val trackChanged = title.isNotBlank() && key != lastTrackKey
        if (trackChanged) {
            homeTranslation.cancel()
            baseLyricDocument = LyricDocument(emptyList(), true)
            home.setTranslationStatus("")
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
        val cover = coverBitmap(md)
        val actions = pb?.actions ?: 0L
        val canPrev = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L
        val canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L
        home.setSnapshot(
            HomeLyricsView.Snapshot(title, artist, cover, positionOf(pb, duration),
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

    // ---------- 歌词拉取（自定义 → 本地缓存 → 联网） ----------
    private val lyricCachePrefs by lazy {
        getSharedPreferences(LYRIC_CACHE_PREFS, Context.MODE_PRIVATE)
    }

    private fun cacheKeyOf(track: String, artist: String, album: String) =
        "$track\u0000$artist\u0000$album"

    /** 一份可上屏的歌词负载，signature 用于判断本地来源是否变化（自定义被增删/改）。 */
    private data class LyricPayload(
        val lyrics: String,
        val translated: String,
        val word: String,
        val source: String,
        val recordId: String
    ) {
        val signature: String get() = "$source\u0000$recordId\u0000${lyrics.length}"
        val isBlank: Boolean get() = lyrics.isBlank()
    }

    /** 只查本地：先自定义歌词，再 14 天内的缓存。查不到返回 null（需要联网）。 */
    private fun localPayload(track: String, artist: String, album: String): LyricPayload? {
        CustomLyricsStore.find(this, track, artist)?.let { entry ->
            val parsed = CustomLyricsStore.parse(entry.lyrics)
            if (parsed.lyrics.isNotBlank()) {
                return LyricPayload(
                    parsed.lyrics, parsed.translatedLyrics, "",
                    CustomLyricsStore.SOURCE, entry.recordId
                )
            }
        }
        val cached = runCatching {
            lyricCachePrefs.getString(cacheKeyOf(track, artist, album), null)?.let { JSONObject(it) }
        }.getOrNull() ?: return null
        val at = cached.optLong("at", 0L)
        if (at > 0 && System.currentTimeMillis() - at > CACHE_TTL_MS) return null
        val lrc = cached.optString("lyrics")
        if (lrc.isBlank()) return null
        return LyricPayload(
            lrc, cached.optString("translated"), cached.optString("word"),
            cached.optString("source"), cached.optString("recordId")
        )
    }

    private fun applyLyricPayload(payload: LyricPayload) {
        currentPayloadSignature = payload.signature
        currentLyricSource = payload.source
        // per-song 偏移身份：与悬浮窗一致 identity=(track\u0000artist).trim().lowercase
        currentLyricTitle = currentTrack
        currentLyricArtist = currentArtist
        currentLyricIdentity = "$currentTrack\u0000$currentArtist".trim().lowercase(java.util.Locale.ROOT)
        currentLyricsCustom = payload.source == CustomLyricsStore.SOURCE
        baseLyricDocument = if (payload.isBlank) LyricDocument(emptyList(), true)
            else LyricParser.parse(payload.lyrics, payload.translated, payload.word)
        home.setLyrics(baseLyricDocument, currentLyricsCustom)
        refreshHomeTranslation()
        applyLyricOffset()
    }

    /** 读取该歌该源的 per-song 偏移并喂给页面；同时记录当前身份，让设置页也能对齐。 */
    private fun applyLyricOffset() {
        if (currentLyricIdentity.isBlank() || currentLyricSource.isBlank()) return
        LyricsOverlayService.rememberActiveLyric(
            overlayPrefs, currentLyricIdentity, currentLyricSource, currentLyricTitle, currentLyricArtist
        )
        val key = LyricsOverlayService.lyricOffsetPreferenceKey(currentLyricIdentity, currentLyricSource)
        val offset = overlayPrefs.getInt(key, 0)
            .coerceIn(LyricsOverlayService.LYRIC_OFFSET_MIN_MS, LyricsOverlayService.LYRIC_OFFSET_MAX_MS)
        home.setLyricOffset(offset)
    }

    private fun fetchLyrics(track: String, artist: String, album: String, durationMs: Long) {
        val reqId = ++lyricRequestId
        val cacheKey = cacheKeyOf(track, artist, album)

        // 1) 本地命中（自定义 / 缓存）立即上屏，不打网络
        val local = localPayload(track, artist, album)
        if (local != null) {
            applyLyricPayload(local)
            // 自定义歌词不写入自动匹配记忆；缓存来源补一条歌词源管理记录
            if (local.source != CustomLyricsStore.SOURCE) {
                mirrorToMatchMemory(
                    track, artist, album, durationMs,
                    local.source, local.recordId, local.lyrics, local.translated, local.word
                )
            }
            return
        }

        // 2) 未命中才清空并联网
        home.showLoading()
        ioScope.launch {
            val result = runCatching {
                repository.resolveLyrics(track, artist, album, durationMs)
            }.getOrNull()
            if (reqId != lyricRequestId) return@launch
            val lrc = result?.lyrics.orEmpty()
            val trans = result?.translatedLyrics.orEmpty()
            val word = result?.wordLyrics.orEmpty()
            val source = result?.source.orEmpty()
            val recordId = result?.recordId.orEmpty()
            if (lrc.isNotBlank()) {
                // 写缓存（限量，避免无限膨胀）
                runCatching {
                    val obj = JSONObject()
                        .put("lyrics", lrc).put("translated", trans).put("word", word)
                        .put("source", source).put("recordId", recordId)
                        .put("at", System.currentTimeMillis())
                    val editor = lyricCachePrefs.edit().putString(cacheKey, obj.toString())
                    if (lyricCachePrefs.all.size > 60) {
                        // 简单清理：删掉最旧的一半
                        lyricCachePrefs.all.entries
                            .sortedBy { (runCatching { JSONObject(it.value as String).optLong("at") }.getOrDefault(0L)) }
                            .take(30).forEach { editor.remove(it.key) }
                    }
                    editor.apply()
                }
                // 镜像到歌词源管理记忆（与悬浮窗共用同一份 match_memory_v2）
                mirrorToMatchMemory(track, artist, album, durationMs, source, recordId, lrc, trans, word)
            }
            withContext(Dispatchers.Main) {
                if (reqId != lyricRequestId) return@withContext
                applyLyricPayload(LyricPayload(lrc, trans, word, source, recordId))
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
        // 翻译模式
        val mode = overlayPrefs.getString(
            LyricsOverlayService.PREF_TRANSLATION_MODE, LyricsOverlayService.TRANSLATION_BILINGUAL
        ) ?: LyricsOverlayService.TRANSLATION_BILINGUAL
        home.setTranslationMode(mode)

        if (currentTrack.isBlank()) return
        val local = localPayload(currentTrack, currentArtist, currentAlbum)
        when {
            // 本地来源变了（新增/改了自定义，或缓存过期），重新上屏
            local != null && local.signature != currentPayloadSignature -> applyLyricPayload(local)
            // 之前用的是自定义歌词，但现在被删了 → 重新联网拉
            local == null && currentLyricSource == CustomLyricsStore.SOURCE ->
                fetchLyrics(currentTrack, currentArtist, currentAlbum, currentDurationMs)
            // 其它情况只重新应用偏移
            else -> applyLyricOffset()
        }
        refreshHomeTranslation()
    }

    private fun refreshHomeTranslation() {
        if (!homeForeground) return
        val display = overlayPrefs.getString(LyricsOverlayService.PREF_TRANSLATION_MODE,
            LyricsOverlayService.TRANSLATION_BILINGUAL).orEmpty()
        val translationPrefs = getSharedPreferences("supplement_translation", Context.MODE_PRIVATE)
        homeTranslation.update(lastTrackKey, baseLyricDocument, display,
            translationPrefs.getString("mode", "off").orEmpty())
    }

    /**
     * 把主页拉到的歌词写进「歌词源管理」共用的 match_memory_v2（存于悬浮窗 prefs）。
     * 仅在该歌+来源尚无记录时新增，绝不覆盖用户在悬浮窗里手动选过的版本。
     * key 格式与悬浮窗 matchMemoryKey 完全一致：normalizedKey|durationSec|source。
     */
    private fun mirrorToMatchMemory(
        track: String, artist: String, album: String, durationMs: Long,
        source: String, recordId: String, lrc: String, trans: String, word: String
    ) {
        if (track.isBlank() || source.isBlank() || lrc.isBlank()) return
        runCatching {
            val normKey = ("$track\u0000$artist").trim().lowercase(java.util.Locale.ROOT)
            val durSec = Math.round(durationMs / 1000.0)
            val key = "$normKey|$durSec|$source"

            val arr = org.json.JSONArray(
                overlayPrefs.getString("match_memory_v2", "[]").orEmpty().ifBlank { "[]" }
            )
            // 已存在同 key（含悬浮窗手动选择）则不动
            for (i in 0 until arr.length()) {
                if (arr.optJSONObject(i)?.optString("key") == key) return
            }

            val candidate = JSONObject()
                .put("lyrics", lrc)
                .put("translatedLyrics", trans)
                .put("wordLyrics", word)
                .put("duration", durationMs)
                .put("cover", "")
                .put("source", source)
                .put("recordId", recordId)
                .put("title", track)
                .put("artist", artist)
                .put("matchScore", 0)
            val entry = JSONObject()
                .put("key", key)
                .put("at", System.currentTimeMillis())
                .put("title", track)
                .put("artist", artist)
                .put("album", album)
                .put("duration", durationMs)
                .put("source", source)
                .put("candidate", candidate)
                .put("original", candidate)
                .put("history", org.json.JSONArray().put(candidate))
                .put("needsReview", false)
                .put("auto", true)
            arr.put(entry)
            overlayPrefs.edit().putString("match_memory_v2", arr.toString()).apply()
        }
    }

    // ---------- Native player actions ----------
    private inner class HomeActions : HomeLyricsView.Actions {
        override fun toggleOverlay() {
            if (LyricsOverlayService.isRunning) {
                stopService(Intent(this@MainActivity, LyricsOverlayService::class.java))
            } else {
                tryStartOverlay()
            }
            updateOverlayState()
        }

        override fun openSettings() = this@MainActivity.openSettings()

        override fun seekTo(positionMs: Long) {
            val c = controller ?: return
            runCatching { c.transportControls.seekTo(positionMs.coerceAtLeast(0L)) }
            mainHandler.postDelayed({ pushSnapshot() }, 180)
        }

        override fun togglePlay() {
            val c = controller ?: return
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            runCatching {
                if (playing) c.transportControls.pause() else c.transportControls.play()
            }
            mainHandler.postDelayed({ pushSnapshot() }, 120)
        }

        override fun skipPrev() {
            val c = controller ?: return
            runCatching { c.transportControls.skipToPrevious() }
            mainHandler.postDelayed({ pushSnapshot() }, 180)
        }

        override fun skipNext() {
            val c = controller ?: return
            runCatching { c.transportControls.skipToNext() }
            mainHandler.postDelayed({ pushSnapshot() }, 180)
        }

        override fun editCustomLyrics() {
            if (currentTrack.isNotBlank()) {
                startActivity(CustomLyricsEditActivity.intent(this@MainActivity, currentTrack, currentArtist))
            } else {
                manageCustomLyrics()
            }
        }

        override fun manageCustomLyrics() {
            startActivity(Intent(this@MainActivity, CustomLyricsManagerActivity::class.java))
        }
    }

    companion object {
        // 切歌后的稳定窗口：这段时间内若拿到的还是旧歌残留状态，位置先按 0 处理，避免歌词抖动。
        private const val TRACK_CHANGE_SETTLE_MS = 1500L
        private const val LYRIC_CACHE_PREFS = "home_lyric_cache_v1"
        private const val CACHE_TTL_MS = 14L * 24 * 60 * 60 * 1000
    }
}
