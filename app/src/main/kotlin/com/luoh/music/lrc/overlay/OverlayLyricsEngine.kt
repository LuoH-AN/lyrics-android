package com.luoh.music.lrc.overlay

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.luoh.music.lrc.CustomLyricsEditActivity
import com.luoh.music.lrc.R
import com.luoh.music.lrc.TranslationSettingsActivity
import com.luoh.music.lrc.data.lyrics.CustomLyricsStore
import com.luoh.music.lrc.data.lyrics.DirectLyricsRepository
import com.luoh.music.lrc.data.lyrics.LyricMatchMemory
import com.luoh.music.lrc.data.lyrics.LyricOffsetMemory
import com.luoh.music.lrc.data.lyrics.SharedLyricsCache
import com.luoh.music.lrc.data.lyrics.SongSearchCorrections
import com.luoh.music.lrc.data.lyrics.TemporaryCache
import com.luoh.music.lrc.data.translation.translationPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Owns the detached lyric engine, its browser protocol and the requests it starts. */
internal class OverlayLyricsEngine(
    private val context: Context,
    prefs: SharedPreferences,
    private val mainHandler: Handler,
    private val window: OverlayWindow,
    private val offsetMemory: LyricOffsetMemory,
    private val mediaController: () -> MediaController?,
    private val scheduleSnapshot: () -> Unit,
    private val onReady: () -> Unit,
    private val onActiveLyric: (LyricOffsetMemory.Song, Int) -> Unit,
) {
    private val matchMemory = LyricMatchMemory(prefs)
    private val sharedLyricsCache = SharedLyricsCache(context)
    private val lyricsRepository = DirectLyricsRepository()
    private val lyricsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val supplement = OverlaySupplement(context, mainHandler, lyricsScope) { requestId, data ->
        if (webReady) webView?.evaluateJavascript("window.LobstaOverlay.receiveSupplement($requestId,$data);", null)
    }

    private var webView: WebView? = null
    private var webReady = false
    private var pendingSnapshot: JSONObject? = null
    private var nativeTrack = ""
    private var nativeArtist = ""
    private var nativeDocumentRequestId = -1
    @Volatile private var latestLyricsRequestId = 0

    private val engineTickRunnable = object : Runnable {
        override fun run() {
            if (!webReady || !window.isOpen) return
            webView?.evaluateJavascript("window.LobstaOverlay && window.LobstaOverlay.engineTick();", null)
            mainHandler.postDelayed(this, 250L)
        }
    }

    fun pruneTemporaryCache() {
        lyricsScope.launch { TemporaryCache.prune(context.applicationContext) }
    }

    @Suppress("SetJavaScriptEnabled")
    fun start() {
        if (webView != null) return
        val uiContext = ContextThemeWrapper(context, R.style.Theme_DesktopLyrics)
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
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != ASSET_URL || view !== webView || !window.isOpen) return
                    webReady = true
                    onReady()
                    pendingSnapshot?.let(::dispatchPlayback) ?: scheduleSnapshot()
                    mainHandler.removeCallbacks(engineTickRunnable)
                    mainHandler.post(engineTickRunnable)
                }
            }
        }
        // The matching engine is never attached to the visible window.
        webView = player
        player.loadUrl(ASSET_URL)
    }

    fun close() {
        webReady = false
        mainHandler.removeCallbacks(engineTickRunnable)
        lyricsScope.cancel()
        lyricsRepository.close()
        val player = webView ?: return
        webView = null
        (player.parent as? ViewGroup)?.removeView(player)
        player.stopLoading()
        player.loadUrl("about:blank")
        player.destroy()
    }

    fun refreshSupplementTranslation() {
        mainHandler.post {
            webView?.evaluateJavascript("window.LobstaOverlay.refreshSupplement();", null)
        }
    }

    fun refreshMatches() {
        webView?.evaluateJavascript("window.LobstaOverlay?.refreshMatchManagement();", null)
    }

    fun reloadCustomLyrics() {
        mainHandler.post {
            webView?.evaluateJavascript("window.LobstaOverlay && window.LobstaOverlay.reloadCustomLyrics();", null)
        }
    }

    fun setOffset(offsetMs: Int) {
        webView?.evaluateJavascript(
            "window.LobstaOverlay && window.LobstaOverlay.setLyricOffset($offsetMs);",
            null
        )
    }

    fun setTranslationMode(mode: String) {
        val encoded = JSONObject.quote(mode)
        webView?.evaluateJavascript(
            "window.LobstaOverlay && window.LobstaOverlay.setTranslationMode($encoded);",
            null
        )
    }

    fun resetHorizontalLayout() {
        webView?.evaluateJavascript(
            "window.LobstaOverlay && window.LobstaOverlay.setHorizontalLayout(false);",
            null
        )
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
                window.setDocument(result)
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
                context.translationPrefs().edit()
                    .putString("last_status", message).putString("last_track", nativeTrack).apply()
                if (status == "error") Toast.makeText(
                    context, message, Toast.LENGTH_LONG
                ).show()
            }
        }

        @JavascriptInterface
        fun setCompactContentState(hasLyrics: Boolean, hasTranslation: Boolean) {
            mainHandler.post { window.setContentState(hasLyrics, hasTranslation) }
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
                onActiveLyric(song, remembered)
            }
            return remembered
        }

        @JavascriptInterface
        fun supplementSettings() {
            mainHandler.post { context.startActivity(Intent(context, TranslationSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        @JavascriptInterface
        fun supplementStrategy(): String = supplement.strategy()

        @JavascriptInterface
        fun cancelSupplement() = supplement.cancel()

        @JavascriptInterface
        fun translateMissing(requestId: Int, payload: String) = supplement.translate(requestId, payload)

        @JavascriptInterface
        fun mediaCommand(command: String) {
            mainHandler.post {
                val controller = mediaController() ?: return@post
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
                val controller = mediaController() ?: return@post
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
                    val array = JSONArray(excludedJson)
                    val excluded = (0 until array.length()).map { array.optString(it) }.toSet()
                    val search = SongSearchCorrections.resolve(context, track, artist)
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
            val entry = CustomLyricsStore.find(context, track, artist) ?: return ""
            val duration = durationMs.takeIf { it.isFinite() && it > 0 }?.toLong() ?: 0L
            return CustomLyricsStore.toResultJson(entry, duration).toString()
        }

        @JavascriptInterface
        fun lyricCacheEpoch(): Long = sharedLyricsCache.epoch

        @JavascriptInterface
        fun cachedLyrics(track: String, artist: String, album: String, durationMs: Double): String {
            if (track.isBlank()) return ""
            val duration = durationMs.takeIf { it.isFinite() && it > 0 }?.toLong() ?: 0L
            val search = SongSearchCorrections.resolve(context, track, artist)
            return sharedLyricsCache.read(search.title, search.artist, album, duration)?.toJson()?.toString().orEmpty()
        }

        /** 从悬浮窗菜单进入自定义歌词编辑页（当前这首歌）。 */
        @JavascriptInterface
        fun editCustomLyrics(track: String, artist: String) {
            mainHandler.post {
                context.startActivity(
                    CustomLyricsEditActivity.intent(context, track, artist)
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
            val search = SongSearchCorrections.resolve(context, track, artist)
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

    private fun updateNativePlayback(snapshot: JSONObject) {
        val track = snapshot.optString("track")
        val artist = snapshot.optString("artist")
        if (snapshot.optBoolean("permissionRequired") || !snapshot.optBoolean("hasSession") || track.isBlank()) {
            nativeTrack = ""
            nativeArtist = ""
            window.setKeepScreenOn(false)
            window.clearLyrics(if (snapshot.optBoolean("permissionRequired")) "请授予通知使用权" else "暂未播放")
            return
        }
        if (track != nativeTrack || artist != nativeArtist) {
            nativeTrack = track
            nativeArtist = artist
            window.clearLyrics("正在获取歌词")
        }
        val playing = snapshot.optString("state") == "playing"
        window.setPlayback(
            "$track\u0000$artist",
            snapshot.optLong("positionMs"), snapshot.optLong("durationMs"),
            playing, snapshot.optDouble("speed", 1.0).toFloat()
        )
    }

    fun dispatchPlayback(snapshot: JSONObject) {
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

    private companion object {
        const val ASSET_URL = "file:///android_asset/lyrics_overlay.html"
        const val LOG_TAG = "DesktopLyrics"
    }
}
