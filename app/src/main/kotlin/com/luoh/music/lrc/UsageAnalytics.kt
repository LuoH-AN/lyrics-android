package com.luoh.music.lrc

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.PowerManager
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

/** Optional, bounded usage events. Call sites never pass lyric text, titles, URLs or error messages. */
internal object UsageAnalytics {
    private const val ENDPOINT = "https://umi.luoh.org/api/send"
    private const val WEBSITE_ID = "c8a3563b-bf82-40c0-8352-de5438f4c653"
    private const val HOSTNAME = "lyrics-android.local"
    private const val MAX_PENDING = 100
    private const val MAX_AGE_MS = 15 * 60_000L

    data class Preferences(val noticeSeen: Boolean = false, val enabled: Boolean = false)
    enum class Surface(val value: String) { APP("app"), OVERLAY("overlay") }
    enum class Result(val value: String) { SUCCESS("success"), NOT_FOUND("not_found"), ERROR("error"), CANCELLED("cancelled"), SKIPPED("skipped") }
    enum class Failure(val value: String) {
        NONE("none"), TIMEOUT("timeout"), NETWORK("network"), CONFIGURATION("configuration"),
        LANGUAGE_UNKNOWN("language_unknown"), MODEL_MISSING("model_missing"),
        AUTHENTICATION("authentication"), RATE_LIMIT("rate_limit"), SERVER("server"),
        RESPONSE_FORMAT("response_format"), INCOMPLETE_RESPONSE("incomplete_response"), OTHER("other")
    }

    private data class Pending(val payload: JSONObject, val generation: Int, val at: Long, var attempts: Int = 0)
    private enum class Delivery { SENT, RETRY, DROP }
    private val mutablePreferences = MutableStateFlow(Preferences())
    val preferences = mutablePreferences.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var settings: AtomicFile? = null
    private var production = false
    private var interactive = true
    private var version = ""
    private var installId = ""
    private var generation = 0
    private var cache: String? = null
    private var connection: HttpURLConnection? = null
    private val pending = ArrayDeque<Pending>()
    private var uploadJob: Job? = null
    private var durationJob: Job? = null
    private val activeSurfaces = mutableSetOf<Surface>()
    private val accountedAt = mutableMapOf<Surface, Long>()
    private val seenPlayers = mutableSetOf<String>()
    private var playerDay = ""

    @Synchronized
    fun initialize(context: Context) {
        if (settings != null) return
        production = context.packageName == "com.luoh.music.lrc"
        interactive = context.getSystemService(PowerManager::class.java)?.isInteractive != false
        version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
            .getOrDefault("unknown")
        // Preferences and installation identity are never restored onto another device by Android backup.
        val file = AtomicFile(File(context.noBackupFilesDir, "usage-analytics.json"))
        settings = file
        val saved = runCatching { JSONObject(String(file.readFully(), Charsets.UTF_8)) }.getOrNull()
        val id = saved?.optString("id").orEmpty()
        val validId = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
        // A missing preference defaults on; an explicit opt-out must survive upgrades and restarts.
        val enabled = saved?.optBoolean("enabled", true) ?: true
        val noticeSeen = saved?.let { it.optBoolean("notice_seen", it.optBoolean("decided")) } == true
        val nextId = if (enabled) { if (validId) id else UUID.randomUUID().toString() } else ""
        if (savePreferences(enabled, nextId, noticeSeen)) {
            installId = nextId
            mutablePreferences.value = Preferences(noticeSeen, enabled)
        }
    }

    @Synchronized
    fun markNoticeSeen() {
        val current = mutablePreferences.value
        if (!current.noticeSeen && savePreferences(current.enabled, installId, true)) {
            mutablePreferences.value = current.copy(noticeSeen = true)
        }
    }

    private fun savePreferences(enabled: Boolean, id: String, noticeSeen: Boolean): Boolean {
        val file = settings ?: return false
        return runCatching {
            val output = file.startWrite()
            try {
                output.write(JSONObject().put("notice_seen", noticeSeen).put("enabled", enabled).put("id", id)
                    .toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        }.isSuccess
    }

    @Synchronized
    fun setEnabled(enabled: Boolean): Boolean {
        if (settings == null) return false
        val id = if (enabled) installId.ifBlank { UUID.randomUUID().toString() } else ""
        val saved = savePreferences(enabled, id, true)
        if (!saved && enabled) return false
        // An opt-out takes effect in this process even if storage fails; the UI reports the failed save.
        generation++
        uploadJob?.cancel()
        uploadJob = null
        connection?.disconnect()
        connection = null
        durationJob?.cancel()
        durationJob = null
        pending.clear()
        cache = null
        accountedAt.clear()
        seenPlayers.clear()
        installId = id
        mutablePreferences.value = Preferences(noticeSeen = true, enabled = enabled)
        if (collecting()) {
            activeSurfaces.forEach { surface -> startSurface(surface) }
            startDurationClock()
        }
        return saved
    }

    private fun collecting() = production && mutablePreferences.value.enabled && installId.isNotBlank()

    @Synchronized
    fun setInteractive(value: Boolean) {
        if (interactive == value) return
        activeSurfaces.forEach(::recordDuration)
        accountedAt.clear()
        interactive = value
        durationJob?.cancel()
        durationJob = null
        if (interactive && collecting()) {
            activeSurfaces.forEach { accountedAt[it] = SystemClock.elapsedRealtime() }
            startDurationClock()
        }
    }

    @Synchronized
    fun setActive(surface: Surface, active: Boolean) {
        if (active == (surface in activeSurfaces)) return
        if (active) {
            activeSurfaces += surface
            if (collecting()) startSurface(surface)
        } else {
            recordDuration(surface)
            activeSurfaces -= surface
            accountedAt.remove(surface)
            record("${surface.value}_stop", surface.value)
        }
        if (activeSurfaces.isEmpty()) {
            durationJob?.cancel()
            durationJob = null
        } else startDurationClock()
    }

    private fun startSurface(surface: Surface) {
        if (interactive) accountedAt[surface] = SystemClock.elapsedRealtime()
        // Pageviews keep Umami's built-in visit charts useful alongside custom event charts.
        record(null, surface.value)
        record("${surface.value}_start", surface.value)
    }

    private fun startDurationClock() {
        if (!collecting() || !interactive || activeSurfaces.isEmpty() || durationJob != null) return
        durationJob = scope.launch {
            while (true) {
                delay(60_000L)
                synchronized(UsageAnalytics) { activeSurfaces.forEach(::recordDuration) }
            }
        }
    }

    private fun recordDuration(surface: Surface) {
        val start = accountedAt[surface] ?: return
        val now = SystemClock.elapsedRealtime()
        val seconds = ((now - start) / 1000).coerceAtLeast(0)
        if (seconds > 0) {
            accountedAt[surface] = start + seconds * 1000
            record("usage_time", surface.value, mapOf("surface" to surface.value, "seconds" to seconds))
        }
    }

    @Synchronized
    fun playerUsed(packageName: String) {
        if (!collecting() || activeSurfaces.isEmpty()) return
        val player = when (packageName) {
            "com.tencent.qqmusic" -> "qq_music"
            "com.netease.cloudmusic" -> "netease"
            "com.apple.android.music" -> "apple_music"
            "com.spotify.music" -> "spotify"
            "com.kugou.android" -> "kugou"
            "cn.kuwo.player", "com.kuwo.player" -> "kuwo"
            "com.google.android.apps.youtube.music" -> "youtube_music"
            "com.amazon.mp3" -> "amazon_music"
            "com.soundcloud.android" -> "soundcloud"
            "deezer.android.app" -> "deezer"
            "com.aspiro.tidal" -> "tidal"
            "com.miui.player" -> "xiaomi_music"
            "com.sec.android.app.music" -> "samsung_music"
            "com.maxmpz.audioplayer" -> "poweramp"
            else -> "other"
        }
        val day = LocalDate.now(ZoneOffset.UTC).toString()
        if (day != playerDay) { playerDay = day; seenPlayers.clear() }
        if (seenPlayers.add(player)) record("player_used", "player", mapOf("player" to player))
    }

    fun failure(error: Throwable): Failure = when (error) {
        is SocketTimeoutException -> Failure.TIMEOUT
        is IOException -> Failure.NETWORK
        else -> Failure.OTHER
    }

    private fun source(value: String) = when (value) {
        "QQ", "QQ音乐" -> "qq_music"
        "NetEase", "网易云音乐" -> "netease"
        "LRCLIB" -> "lrclib"
        "自定义歌词" -> "custom"
        else -> "none"
    }

    @Synchronized
    fun lyricsResult(provider: String, result: Result, durationMs: Long, failure: Failure = Failure.NONE) {
        record("lyrics_provider_result", "lyrics", mapOf("source" to source(provider), "result" to result.value,
            "duration_ms" to durationMs.coerceIn(0, 300_000), "failure" to failure.value))
    }

    @Synchronized
    fun lyricsLookup(provider: String, result: Result, durationMs: Long, failure: Failure = Failure.NONE) {
        record("lyrics_lookup_result", "lyrics", mapOf("source" to source(provider), "result" to result.value,
            "duration_ms" to durationMs.coerceIn(0, 300_000), "failure" to failure.value))
    }

    @Synchronized
    fun translationResult(mode: String, result: Result, durationMs: Long, lines: Int, failure: Failure = Failure.NONE) {
        if (mode != "offline" && mode != "api") return
        record("translation_result", "translation", mapOf("mode" to mode, "result" to result.value,
            "duration_ms" to durationMs.coerceIn(0, 3_600_000), "translated_lines" to lines.coerceIn(0, 1000),
            "failure" to failure.value))
    }

    /** Called under this object's monitor; only the closed vocabulary above reaches this method. */
    private fun record(name: String?, path: String, values: Map<String, Any> = emptyMap()) {
        if (!collecting()) return
        val data = JSONObject().put("app_version", version).put("android_api", Build.VERSION.SDK_INT)
        values.forEach { (key, value) -> data.put(key, value) }
        val payload = JSONObject().put("website", WEBSITE_ID).put("hostname", HOSTNAME)
            .put("url", "/$path").put("title", "桌面歌词 Android").put("language", Locale.getDefault().toLanguageTag())
            .put("id", installId).put("tag", "android").put("data", data)
        if (name != null) payload.put("name", name)
        if (pending.size >= MAX_PENDING) pending.removeFirst()
        pending.addLast(Pending(payload, generation, SystemClock.elapsedRealtime()))
        scheduleUpload()
    }

    private fun scheduleUpload() {
        if (uploadJob != null || pending.isEmpty() || !collecting()) return
        val epoch = generation
        uploadJob = scope.launch {
            try {
                delay(5_000L)
                while (true) {
                    val event = synchronized(UsageAnalytics) {
                        if (epoch == generation && collecting()) pending.firstOrNull() else null
                    } ?: break
                    val delivery = if (SystemClock.elapsedRealtime() - event.at > MAX_AGE_MS) Delivery.DROP else send(event)
                    val retry = synchronized(UsageAnalytics) {
                        if (epoch != generation || pending.firstOrNull() !== event) false
                        else if (delivery == Delivery.RETRY && ++event.attempts < 3) true
                        else { pending.removeFirst(); false }
                    }
                    if (retry) delay(30_000L * event.attempts)
                }
            } finally {
                synchronized(UsageAnalytics) {
                    if (epoch == generation) { uploadJob = null; scheduleUpload() }
                }
            }
        }
    }

    private suspend fun send(event: Pending): Delivery {
        currentCoroutineContext().ensureActive()
        val request = try { URL(ENDPOINT).openConnection() as HttpURLConnection }
            catch (_: Exception) { return Delivery.RETRY }
        try {
            synchronized(this) {
                if (!collecting() || event.generation != generation) return Delivery.DROP
                connection = request
                request.connectTimeout = 5_000
                request.readTimeout = 5_000
                request.instanceFollowRedirects = false
                request.requestMethod = "POST"
                request.doOutput = true
                request.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                request.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36 LyricsAndroid/$version")
                request.setRequestProperty("x-umami-website-id", WEBSITE_ID)
                request.setRequestProperty("x-umami-hostname", HOSTNAME)
                cache?.let { request.setRequestProperty("x-umami-cache", it) }
            }
            currentCoroutineContext().ensureActive()
            request.outputStream.use { it.write(JSONObject().put("type", "event").put("payload", event.payload)
                .toString().toByteArray(Charsets.UTF_8)) }
            val status = request.responseCode
            if (status in 200..299) {
                val body = request.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(16_384)
                    var length = 0
                    while (length < buffer.size) {
                        val read = reader.read(buffer, length, buffer.size - length)
                        if (read < 0) break
                        length += read
                    }
                    String(buffer, 0, length)
                }
                val result = runCatching { JSONObject(body) }.getOrNull()
                synchronized(this) {
                    if (event.generation == generation) cache = result?.optString("cache")?.takeIf { it.isNotBlank() }
                }
                return Delivery.SENT
            }
            if (status == 401) synchronized(this) { if (event.generation == generation) cache = null }
            return if (status in setOf(401, 408, 429) || status >= 500) Delivery.RETRY else Delivery.DROP
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return Delivery.RETRY
        } finally {
            synchronized(this) { if (connection === request) connection = null }
            request.disconnect()
        }
    }
}
