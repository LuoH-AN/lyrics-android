package com.luoh.music.lrc.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    const val RELEASES_LATEST_URL = "https://github.com/LuoH-AN/lyrics-android/releases/latest"
    const val NIGHTLY_BUILDS_URL = "https://github.com/LuoH-AN/lyrics-android/actions/workflows/build.yml"
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
                val version = stableVersion(tag) ?: error("Release version is invalid")
                val apiUrl = json.optString("html_url")
                val pageUrl = apiUrl.takeIf {
                    it.startsWith("https://github.com/LuoH-AN/lyrics-android/releases/")
                } ?: RELEASES_LATEST_URL
                Release(tag.ifBlank { "v$version" }, version, pageUrl, summarize(json.optString("body")))
            } finally {
                connection.disconnect()
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    fun isNewer(remote: String, current: String): Boolean {
        val left = comparisonParts(remote) ?: return false
        val right = comparisonParts(current) ?: return false
        val length = maxOf(left.size, right.size)
        repeat(length) { index ->
            val comparison = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    fun stableVersion(value: String): String? = versionParts(value)?.joinToString(".")

    private fun comparisonParts(value: String): List<Int>? {
        val parts = versionParts(value) ?: return null
        // 1.4.31 是 1.4.3 的补丁版，发布顺序介于 1.4.3 和 1.4.4 之间。
        return if (parts == listOf(1, 4, 31)) listOf(1, 4, 3, 1) else parts
    }

    private fun versionParts(value: String): List<Int>? {
        val match = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:-[0-9A-Za-z.-]+)?$")
            .matchEntire(value.trim()) ?: return null
        return (1..3).map { match.groupValues[it].toIntOrNull() ?: return null }
    }

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
