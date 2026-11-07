package com.luoh.music.lrc

import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.Process
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.system.exitProcess

/** One bounded report, replayed after restart. Never reads Throwable.message or writes a full trace. */
internal object CrashDiagnostics {
    private val lock = Any()
    private var file: AtomicFile? = null
    private var identity: String? = null
    private var version = ""
    private const val MAX_BYTES = 32_768
    private const val MAX_AGE_MS = 7 * 24 * 60 * 60_000L

    fun initialize(context: Context, installId: String?, appVersion: String) = synchronized(lock) {
        if (file != null) return@synchronized
        file = AtomicFile(File(context.noBackupFilesDir, "usage-crash.json"))
        version = appVersion
        identity = installId
        if (installId == null) file?.delete()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                // Failures in diagnostics must never prevent Android's original crash handling.
                runCatching { capture(thread, error) }
            } finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
        }
    }

    fun setIdentity(installId: String?) = synchronized(lock) {
        if (identity != installId || installId == null) file?.delete()
        identity = installId
    }

    private fun symbol(value: String) = value.take(240).replace(Regex("[^A-Za-z0-9_.$<>:-]"), "_")

    private fun capture(thread: Thread, error: Throwable) = synchronized(lock) {
        val id = identity ?: return@synchronized
        val target = file ?: return@synchronized
        val causes = mutableListOf<Throwable>()
        var next: Throwable? = error
        while (next != null && causes.size < 4 && causes.none { it === next }) {
            val current = next ?: break
            causes += current
            next = current.cause
        }
        val frames = causes.flatMap { it.stackTrace.take(24).toList() }
        val selected = (frames.take(4) + frames.filter { it.className.startsWith("com.luoh.music.lrc.") }
            + frames).distinct().take(10)
        val type = symbol(error.javaClass.name)
        val positions = selected.map { "${symbol(it.className)}.${symbol(it.methodName)}:${it.lineNumber}".take(450) }
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest((type + positions.joinToString("|")).toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val reportId = UUID.randomUUID().toString()
        val data = JSONObject().put("report_id", reportId).put("kind", "jvm_uncaught")
            .put("crash_time", java.time.Instant.now().toString())
            .put("exception_type", type).put("cause_type", symbol(causes.last().javaClass.name))
            .put("fingerprint", fingerprint).put("main_thread", thread === Looper.getMainLooper().thread)
            .put("app_version", version).put("android_api", Build.VERSION.SDK_INT)
            .put("android_version", Build.VERSION.RELEASE.take(64)).put("device_brand", Build.BRAND.take(80))
            .put("device_model", Build.MODEL.take(120))
        positions.forEachIndexed { i, value -> data.put("frame_${i + 1}", value) }
        val report = JSONObject().put("id", id).put("report_id", reportId)
            .put("at", System.currentTimeMillis()).put("data", data).toString().toByteArray(Charsets.UTF_8)
        if (report.size > MAX_BYTES) return@synchronized
        val output = target.startWrite()
        try {
            output.write(report)
            target.finishWrite(output)
        } catch (failure: Throwable) {
            target.failWrite(output)
            throw failure
        }
    }

    private fun read(): JSONObject? = runCatching {
        file?.openRead()?.use { input ->
            val bytes = ByteArray(MAX_BYTES)
            var count = 0
            while (count < bytes.size) {
                val n = input.read(bytes, count, bytes.size - count)
                if (n < 0) break
                count += n
            }
            if (input.read() != -1) null else JSONObject(String(bytes, 0, count, Charsets.UTF_8))
        }
    }.getOrNull()

    fun pendingReport(): JSONObject? = synchronized(lock) {
        val report = read() ?: return@synchronized null
        val age = System.currentTimeMillis() - report.optLong("at")
        if (identity == null || report.optString("id") != identity || age !in 0..MAX_AGE_MS ||
            report.optJSONObject("data") == null || report.optString("report_id").isBlank()) {
            file?.delete()
            return@synchronized null
        }
        report
    }

    fun markDelivered(reportId: String) = synchronized(lock) {
        // A new crash may have replaced the file while an older report was uploading.
        if (read()?.optString("report_id") == reportId) file?.delete()
    }
}
