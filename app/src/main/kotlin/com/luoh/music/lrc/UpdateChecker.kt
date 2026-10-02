package com.luoh.music.lrc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    const val RELEASES_LATEST_URL = "https://github.com/LuoH-AN/lyrics-android/releases/latest"
    private const val LATEST_API_URL =
        "https://api.github.com/repos/LuoH-AN/lyrics-android/releases/latest"

    /** GitHub 返回 404 表示仓库还没有发布任何 Release。 */
    class NoReleaseException : Exception("尚未发布任何版本")

    data class Release(
        val tag: String,
        val version: String,
        val pageUrl: String,
        val summary: String
    )

    suspend fun fetchLatest(currentVersion: String): Result<Release> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Desktop-Lyrics-Android/$currentVersion")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            try {
                // 404 = 仓库还没有任何 Release，单独区分出来给用户友好提示
                if (connection.responseCode == 404) throw NoReleaseException()
                if (connection.responseCode !in 200..299) {
                    error("GitHub returned HTTP ${connection.responseCode}")
                }
                val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(payload)
                val tag = json.optString("tag_name").trim()
                val version = normalizedVersion(tag)
                if (version.isBlank()) error("Release version is missing")
                val apiUrl = json.optString("html_url")
                val pageUrl = apiUrl.takeIf {
                    it.startsWith("https://github.com/LuoH-AN/lyrics-android/releases/")
                } ?: RELEASES_LATEST_URL
                Release(tag.ifBlank { "v$version" }, version, pageUrl, summarize(json.optString("body")))
            } finally {
                connection.disconnect()
            }
        }
    }

    fun isNewer(remote: String, current: String): Boolean {
        val left = versionParts(remote)
        val right = versionParts(current)
        val length = maxOf(left.size, right.size)
        repeat(length) { index ->
            val comparison = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    private fun versionParts(value: String): List<Int> = normalizedVersion(value)
        .split('.')
        .mapNotNull { it.toIntOrNull() }
        .ifEmpty { listOf(0) }

    private fun normalizedVersion(value: String): String = value
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .takeWhile { it.isDigit() || it == '.' }
        .trim('.')

    private fun summarize(markdown: String): String {
        val text = markdown.lineSequence()
            .map { it.trim().replace(Regex("^[#>*+\\-\\s]+"), "") }
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(" · ")
            .replace(Regex("[`*_\\[\\]]"), "")
            .take(180)
        return text.ifBlank { "新版本已经发布，可前往 GitHub 查看更新内容。" }
    }
}
