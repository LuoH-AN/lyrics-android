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
import org.json.JSONArray
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
    enum class Permission(val value: String) { NOTIFICATION("notification"), OVERLAY("overlay") }
    enum class Feature(val value: String) {
        OVERLAY_START("overlay_start"), OVERLAY_STOP("overlay_stop"), SEEK("seek"),
        PLAY_PAUSE("play_pause"), PREVIOUS("previous"), NEXT("next"),
        RETRY_LYRICS("retry_lyrics"), EXPORT_LRC("export_lrc"), ADD_TILE("add_tile"), TILE_TOGGLE("tile_toggle")
    }
    enum class Result(val value: String) { SUCCESS("success"), NOT_FOUND("not_found"), ERROR("error"), CANCELLED("cancelled"), SKIPPED("skipped") }
    enum class Failure(val value: String) {
        NONE("none"), TIMEOUT("timeout"), NETWORK("network"), CONFIGURATION("configuration"),
        LANGUAGE_UNKNOWN("language_unknown"), MODEL_MISSING("model_missing"),
        AUTHENTICATION("authentication"), RATE_LIMIT("rate_limit"), SERVER("server"),
        RESPONSE_FORMAT("response_format"), INCOMPLETE_RESPONSE("incomplete_response"), OTHER("other")
    }

    private data class Pending(val payload: JSONObject, val generation: Int, val at: Long,
        val crashId: String? = null, var attempts: Int = 0)
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
    private val milestones = mutableSetOf<String>()
    private val permissionStates = mutableMapOf<Permission, Boolean>()
    private var firstSeenAt = 0L
    private var cohort = "new_install"
    private var lastConfiguration = ""

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
        firstSeenAt = saved?.optLong("first_seen_at", 0L)?.takeIf { it > 0 } ?: System.currentTimeMillis()
        cohort = saved?.optString("cohort")?.takeIf { it in setOf("new_install", "existing_install", "reenabled") } ?: run {
            val upgraded = runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                info.lastUpdateTime > info.firstInstallTime
            }.getOrDefault(false)
            if (saved != null || upgraded) "existing_install" else "new_install"
        }
        if (enabled && validId) {
            saved?.optJSONArray("milestones")?.let { list ->
                for (i in 0 until list.length()) milestones += list.optString(i)
            }
            playerDay = saved?.optString("player_day").orEmpty()
            saved?.optJSONArray("players")?.let { list ->
                for (i in 0 until list.length()) seenPlayers += list.optString(i)
            }
        }
        if (savePreferences(enabled, nextId, noticeSeen)) {
            installId = nextId
            mutablePreferences.value = Preferences(noticeSeen, enabled)
        }
        if (production) {
            CrashDiagnostics.initialize(context, installId.takeIf { collecting() }, version)
            CrashDiagnostics.pendingReport()?.let { report ->
                val data = report.getJSONObject("data")
                record("app_crash", "diagnostics", data.keys().asSequence().associateWith { data.get(it) },
                    crashId = report.getString("report_id"))
            }
        }
    }

    @Synchronized
    fun markNoticeSeen() {
        val current = mutablePreferences.value
        if (!current.noticeSeen && savePreferences(current.enabled, installId, true)) {
            mutablePreferences.value = current.copy(noticeSeen = true)
        }
    }

    private fun savePreferences(enabled: Boolean, id: String, noticeSeen: Boolean,
        firstSeen: Long = firstSeenAt, audience: String = cohort): Boolean {
        val file = settings ?: return false
        return runCatching {
            val output = file.startWrite()
            try {
                output.write(JSONObject().put("notice_seen", noticeSeen).put("enabled", enabled).put("id", id)
                    .put("first_seen_at", firstSeen).put("cohort", audience)
                    .put("milestones", JSONArray(if (enabled) milestones.toList() else emptyList<String>()))
                    .put("player_day", if (enabled) playerDay else "")
                    .put("players", JSONArray(if (enabled) seenPlayers.toList() else emptyList<String>()))
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
        if (enabled && mutablePreferences.value.enabled) return true
        val id = if (enabled) installId.ifBlank { UUID.randomUUID().toString() } else ""
        val nextFirstSeen = if (enabled) System.currentTimeMillis() else firstSeenAt
        val nextCohort = if (enabled) "reenabled" else cohort
        val saved = savePreferences(enabled, id, true, nextFirstSeen, nextCohort)
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
        milestones.clear()
        permissionStates.clear()
        lastConfiguration = ""
        firstSeenAt = nextFirstSeen
        cohort = nextCohort
        CrashDiagnostics.setIdentity(id.takeIf { enabled && production })
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
        milestone(if (surface == Surface.APP) "first_open" else "first_overlay")
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
        milestone("first_playback")
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
        if (seenPlayers.add(player)) {
            savePreferences(true, installId, mutablePreferences.value.noticeSeen)
            record("player_used", "player", mapOf("player" to player))
        }
    }

    private fun milestone(step: String) {
        if (!collecting() || !milestones.add(step)) return
        savePreferences(true, installId, mutablePreferences.value.noticeSeen)
        record("onboarding_$step", "onboarding", mapOf("step" to step, "cohort" to cohort,
            "elapsed_seconds" to ((System.currentTimeMillis() - firstSeenAt) / 1000).coerceAtLeast(0)))
    }

    @Synchronized
    fun permissions(notification: Boolean, overlay: Boolean) {
        if (!collecting()) return
        mapOf(Permission.NOTIFICATION to notification, Permission.OVERLAY to overlay).forEach { (permission, allowed) ->
            val previous = permissionStates.put(permission, allowed)
            if (previous != allowed) record("permission_state", "onboarding", mapOf(
                "permission" to permission.value, "granted" to allowed, "observation" to if (previous == null) "initial" else "changed"))
            if (allowed) milestone("${permission.value}_granted")
        }
    }

    @Synchronized
    fun permissionRequested(permission: Permission) {
        record("permission_open", "onboarding", mapOf("permission" to permission.value))
    }

    @Synchronized
    fun lyricsDisplayed(surface: Surface) {
        milestone(if (surface == Surface.APP) "first_home_lyrics" else "first_overlay_lyrics")
    }

    @Synchronized
    fun feature(feature: Feature) {
        record("feature_use", "features", mapOf("feature" to feature.value))
    }

    @Synchronized
    fun configuration(context: Context) {
        if (!collecting()) return
        val mode = context.getSharedPreferences("supplement_translation", Context.MODE_PRIVATE)
            .getString("mode", "off").takeIf { it in setOf("off", "offline", "api") } ?: "off"
        val display = context.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LyricsOverlayService.PREF_TRANSLATION_MODE, LyricsOverlayService.TRANSLATION_BILINGUAL)
            .takeIf { it in setOf("original", "translated", "bilingual") } ?: "bilingual"
        val key = "$mode/$display"
        if (lastConfiguration == key) return
        lastConfiguration = key
        record("translation_setting", "settings", mapOf("mode" to mode, "display" to display))
    }

    @Synchronized
    fun exportResult(success: Boolean) {
        record("lrc_export_result", "features", mapOf("result" to if (success) "success" else "error"))
    }

    @Synchronized
    fun screen(kind: String, category: String = "") {
        val screen = if (kind == "category") {
            if (category !in setOf("access", "overlay", "sync", "lyrics", "appearance", "home", "motion", "about")) return
            "settings_$category"
        } else {
            if (kind !in setOf("settings", "translation", "profiles", "offsets", "sources", "privacy", "custom", "cached", "editor", "cached-editor")) return
            kind
        }
        record("screen_view", "settings", mapOf("screen" to screen))
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
        if (result in setOf(Result.SUCCESS, Result.NOT_FOUND, Result.ERROR)) {
            record("lyrics_lookup_${result.value}", "lyrics", mapOf("source" to source(provider), "failure" to failure.value))
        }
    }

    @Synchronized
    fun translationResult(mode: String, result: Result, durationMs: Long, lines: Int, failure: Failure = Failure.NONE) {
        if (mode != "offline" && mode != "api") return
        record("translation_result", "translation", mapOf("mode" to mode, "result" to result.value,
            "duration_ms" to durationMs.coerceIn(0, 3_600_000), "translated_lines" to lines.coerceIn(0, 1000),
            "failure" to failure.value))
        if (result == Result.SUCCESS || result == Result.ERROR) {
            record("translation_${result.value}", "translation", mapOf("mode" to mode, "failure" to failure.value))
        }
    }

    /** Called under this object's monitor; only the closed vocabulary above reaches this method. */
    private fun record(name: String?, path: String, values: Map<String, Any> = emptyMap(), crashId: String? = null) {
        if (!collecting()) return
        val data = JSONObject().put("app_version", version).put("android_api", Build.VERSION.SDK_INT)
            .put("android_version", Build.VERSION.RELEASE.take(64)).put("device_brand", Build.BRAND.take(80))
            .put("device_model", Build.MODEL.take(120)).put("analytics_schema", 2)
        values.forEach { (key, value) -> data.put(key, value) }
        val payload = JSONObject().put("website", WEBSITE_ID).put("hostname", HOSTNAME)
            .put("url", "/$path").put("title", "桌面歌词 Android").put("language", Locale.getDefault().toLanguageTag())
            .put("id", installId).put("tag", "android").put("data", data)
        if (name != null) payload.put("name", name)
        if (pending.size >= MAX_PENDING) pending.removeFirst()
        pending.addLast(Pending(payload, generation, SystemClock.elapsedRealtime(), crashId))
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
                        else {
                            pending.removeFirst()
                            if (delivery == Delivery.SENT) event.crashId?.let(CrashDiagnostics::markDelivered)
                            false
                        }
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
