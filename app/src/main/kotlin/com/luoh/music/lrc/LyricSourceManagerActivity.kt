package com.luoh.music.lrc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.google.android.material.textfield.TextInputLayout
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Uses the same store as the overlay; no second lyric cache. */
class LyricSourceManagerActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE) }
    private lateinit var content: RecyclerView
    private lateinit var searchField: TextInputLayout
    private var renderJob: Job? = null
    private data class Row(val kind: Int, val title: String, val artist: String = "", val detail: String = "",
        val entry: JSONObject? = null, val candidate: JSONObject? = null, val current: Boolean = false)

    private var busy = false
    private var selected: String? = null
    private var message = ""
    private var songQuery = ""
    private fun col(res: Int) = ContextCompat.getColor(this, res)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        searchField = NativeUi.field(this, "搜索已记录的歌名或歌手")
        val header = NativeUi.column(this, 24).apply { addView(searchField) }
        content = NativeUi.listScreen(this, "歌词源管理", header)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selected != null) { selected = null; render() }
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
        searchField.editText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                songQuery = s.toString()
                render(debounce = true)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        render()
    }
    private fun entries(): List<JSONObject> {
        val array = runCatching { JSONArray(prefs.getString("match_memory_v2", "[]").orEmpty().ifBlank { "[]" }) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }
    private fun save(values: List<JSONObject>, notify: Boolean = true) {
        prefs.edit().putString("match_memory_v2", JSONArray(values).toString()).apply()
        if (notify && LyricsOverlayService.isRunning) startService(Intent(this, LyricsOverlayService::class.java).apply {
            action = LyricsOverlayService.ACTION_REFRESH_MATCHES
        })
    }
    private fun update(key: String, change: (JSONObject) -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val values = entries()
                values.find { it.optString("key") == key }?.let(change)
                save(values)
            }
            render()
        }
    }

    private fun render(debounce: Boolean = false) {
        renderJob?.cancel()
        val selectedKey = selected
        val query = songQuery.trim()
        searchField.visibility = if (selectedKey == null) View.VISIBLE else View.GONE
        renderJob = lifecycleScope.launch {
            if (debounce) delay(120)
            val rows = withContext(Dispatchers.IO) {
                val values = entries()
                val entry = values.find { it.optString("key") == selectedKey }
                buildList {
                    if (message.isNotBlank()) add(Row(0, message))
                    if (entry == null) {
                        val filtered = values.asReversed().filter {
                            (it.optString("title") + " " + it.optString("artist")).contains(query, ignoreCase = true)
                        }
                        if (filtered.isEmpty()) add(Row(0, if (values.isEmpty()) "暂无重匹配记录" else "没有符合搜索条件的歌曲"))
                        filtered.forEach { item -> add(Row(1, item.optString("title").ifBlank { "未知歌曲" },
                            item.optString("artist"), item.optString("source"), item)) }
                        add(Row(2, "匹配记录与缓存", "已记录 ${values.size} 个来源"))
                    } else {
                        add(Row(3, entry.optString("title"), entry.optString("artist"), entry = entry))
                        val history = entry.optJSONArray("history") ?: JSONArray().put(entry.optJSONObject("candidate"))
                        for (i in 0 until history.length()) {
                            val candidate = history.optJSONObject(i) ?: continue
                            val current = !entry.optBoolean("needsReview") && candidate.optString("recordId") == entry.optJSONObject("candidate")?.optString("recordId")
                            val seconds = candidate.optLong("duration") / 1000
                            val preview = candidate.optString("lyrics").lineSequence()
                                .map { it.replace(Regex("^(\\[[^]]+])+"), "").trim() }
                                .filter { it.isNotBlank() }.take(16).joinToString("\n")
                            val detail = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} · " +
                                (if (candidate.optString("wordLyrics").isNotBlank()) "含逐字数据" else "普通歌词") +
                                (if (candidate.optString("translatedLyrics").isNotBlank()) " · 含译文" else "") + "\n\n" + preview
                            add(Row(4, (if (current) "✓ 当前选择 · " else "") + candidate.optString("title").ifBlank { entry.optString("title") },
                                candidate.optString("artist").ifBlank { entry.optString("artist") } + " · " + candidate.optString("source"),
                                detail, entry, candidate, current))
                        }
                        add(Row(5, "来源记忆", "删除后恢复自动匹配", entry = entry))
                    }
                }
            }
            if (selectedKey != selected) return@launch
            NativeUi.items(content, rows, { LibraryRow(it) }) { view, row ->
                val cell = view as LibraryRow
                val entry = row.entry
                when (row.kind) {
                    1 -> cell.bind(row.title, row.artist, row.detail, "查看版本与预览", {
                        selected = requireNotNull(entry).optString("key"); message = ""; render()
                    })
                    2 -> cell.bind(row.title, row.artist, action = "清除匹配记录与缓存…", onAction = ::confirmClearAll, enabled = !busy)
                    3 -> cell.bind(row.title, row.artist, action = if (busy) "正在搜索…" else "搜索下一个匹配结果（仅预览）",
                        onAction = { search(requireNotNull(entry)) }, secondary = if (entry?.optJSONObject("original") != null) "恢复最初的歌词" else "",
                        onSecondary = {
                            entry?.optJSONObject("original")?.let { original ->
                                update(entry.optString("key")) { it.put("candidate", original).put("needsReview", false) }
                            }
                        }, enabled = !busy, secondaryEnabled = !busy)
                    4 -> cell.bind(row.title, row.artist, row.detail, if (row.current) "正在使用" else "选用这个版本", {
                        update(requireNotNull(entry).optString("key")) {
                            it.remove("auto")
                            it.put("candidate", row.candidate).put("needsReview", false).put("at", System.currentTimeMillis())
                        }
                    }, enabled = !row.current && !busy)
                    5 -> cell.bind(row.title, row.artist, action = "删除记忆 / 恢复自动匹配", onAction = { deleteEntry(requireNotNull(entry)) }, enabled = !busy)
                    else -> cell.bind(row.title, row.artist, row.detail)
                }
            }
        }
    }

    private fun deleteEntry(entry: JSONObject) {
        NativeUi.confirm(this, "删除这首歌的来源记忆？",
            "将删除《${entry.optString("title")}》在 ${entry.optString("source")} 的选择及历史版本，下次搜索恢复自动匹配。", "删除记忆") {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { save(entries().filter { it.optString("key") != entry.optString("key") }) }
                selected = null; message = "已删除，下次搜索使用自动匹配"; render()
            }
        }
    }

    private fun confirmClearAll() {
        if (busy) return
        lifecycleScope.launch {
        val count = withContext(Dispatchers.IO) { entries() }.map { it.optString("title") + "\u0000" + it.optString("artist") }.distinct().size
        MaterialAlertDialogBuilder(this)
            .setTitle("清除匹配记录与缓存？")
            .setMessage("将清除 $count 首歌曲的匹配记录（包括手动选定的版本与历史版本），以及主页缓存的歌词。此操作无法撤销。\n\n自定义歌词、翻译语言包和同步设置都会保留。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认清除") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        save(emptyList())
                        getSharedPreferences("home_lyric_cache_v1", Context.MODE_PRIVATE).edit().clear().apply()
                    }
                    selected = null
                    message = "已清除匹配记录与缓存，下次搜索恢复自动匹配"
                    render()
                }
            }.show()
        }
    }

    private fun search(entry: JSONObject) {
        if (busy) return
        if (entry.optString("title").isBlank()) { message = "旧版记录缺少歌曲信息，请先播放这首歌，获取歌词后再打开此页。"; render(); return }
        busy = true; message = ""; render()
        val key = entry.optString("key")
        val history = entry.optJSONArray("history") ?: JSONArray().put(entry.optJSONObject("candidate"))
        val excluded = (0 until history.length()).mapNotNull { history.optJSONObject(it)?.optString("recordId") }.filter { it.isNotBlank() }.toSet()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val repository = DirectLyricsRepository()
                try { runCatching {
                    repository.rematch(entry.optString("source"), entry.optString("title"), entry.optString("artist"),
                        entry.optString("album"), entry.optLong("duration"), excluded)?.toJson()
                } } finally { repository.close() }
            }
            busy = false
            val candidate = result.getOrNull()
            val values = withContext(Dispatchers.IO) { entries() }
            val latest = values.find { it.optString("key") == key }
            if (candidate != null && latest != null) {
                candidate.remove("alternatives")
                val recent = latest.optJSONArray("history") ?: history
                if ((0 until recent.length()).none { recent.optJSONObject(it)?.optString("recordId") == candidate.optString("recordId") }) recent.put(candidate)
                latest.put("history", recent).put("at", System.currentTimeMillis())
                save(values, false)
                message = "已找到新候选，预览后点击选用。当前歌词保持不变。"
            } else message = if (result.isFailure) "请求失败或来源暂时不可用，请稍后重试。" else "没有更多可靠的匹配结果，已保留现有版本。"
            render()
        }
    }
}
