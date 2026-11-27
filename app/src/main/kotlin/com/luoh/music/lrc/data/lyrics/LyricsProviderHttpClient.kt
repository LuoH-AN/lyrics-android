package com.luoh.music.lrc.data.lyrics

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"

internal fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

internal class LyricsProviderHttpClient(
    private val fetchBytes: ((String, Map<String, String>) -> ByteArray)?
) {
    private val providerCooldown = ConcurrentHashMap<String, Long>()

    private fun providerKey(url: String): String = when {
        URL(url).host.endsWith("music.163.com") -> "netease"
        URL(url).host.endsWith("qq.com") -> "qq"
        URL(url).host == "lrclib.net" -> "lrclib"
        URL(url).host in setOf("krcs.kugou.com", "lyrics2.kugou.com") -> "kugou-krc"
        URL(url).host.endsWith(".kugou.com") -> "kugou"
        else -> URL(url).host
    }

    private fun checkProvider(url: String) {
        if ((providerCooldown[providerKey(url)] ?: 0L) > System.nanoTime()) {
            throw IllegalStateException("Provider cooling down after request failure")
        }
    }

    private fun checkStatus(url: String, status: Int) {
        if (status == 405 || status == 429 || status == 503) {
            val seconds = if (status == 503) 15L else 60L
            providerCooldown[providerKey(url)] = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            throw IllegalStateException("Provider status=$status; cooldown=${seconds}s")
        }
    }

    fun getText(url: String, headers: Map<String, String>): String {
        val text = getBytes(url, headers).toString(Charsets.UTF_8)
        if (text.trimStart().startsWith("{")) {
            runCatching { JSONObject(text) }.getOrNull()?.let { checkStatus(url, it.optInt("code", 0)) }
        }
        return text
    }

    fun postFormText(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>
    ): String {
        checkProvider(url)
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.toByteArray(Charsets.UTF_8)
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setFixedLengthStreamingMode(body.size)
            headers.forEach(connection::setRequestProperty)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            checkStatus(url, status)
            if (status !in 200..299) throw IllegalStateException("HTTP $status")
            return connection.readBody().toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    fun getBytes(url: String, headers: Map<String, String>): ByteArray {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        checkProvider(url)
        fetchBytes?.let { fetch ->
            return fetch(url, headers).also { require(it.size <= MAX_RESPONSE_BYTES) { "Response too large" } }
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = true
            headers.forEach(connection::setRequestProperty)
            val status = connection.responseCode
            checkStatus(url, status)
            if (status !in 200..299) throw IllegalStateException("HTTP $status")
            return connection.readBody()
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.readBody(): ByteArray = inputStream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_RESPONSE_BYTES) throw IllegalStateException("Response too large")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000
        const val READ_TIMEOUT_MS = 6_000
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}
