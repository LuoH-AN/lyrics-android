package com.luoh.music.lrc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Uses the same store as the overlay; no second lyric cache. */
class LyricSourceManagerActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private var busy = false
    private var selected: String? = null
    private var message = ""
    private var songQuery = ""
    private fun col(res: Int) = ContextCompat.getColor(this, res)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        content = NativeUi.screen(this, "歌词源管理") {
            if (selected != null) { selected = null; render() } else finish()
        }
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
        val values = entries()
        values.find { it.optString("key") == key }?.let(change)
        save(values)
        render()
    }
    private fun render() {
        content.removeAllViews()
        if (message.isNotBlank()) content.addView(label(message, 14f))
        val values = entries()
        val entry = values.find { it.optString("key") == selected }
        if (entry == null) {
            val searchField = NativeUi.field(this, "搜索已记录的歌名或歌手", songQuery)
            val input = searchField.editText!!
            content.addView(searchField)
            val list = NativeUi.cardColumn(this)
            content.addView(list)
            val canClear = !busy && (values.isNotEmpty() ||
                getSharedPreferences("home_lyric_cache_v1", Context.MODE_PRIVATE).all.isNotEmpty())
            val clearRow = NativeUi.row(
                this,
                title = "清除匹配记录与缓存…",
                iconRes = R.drawable.ic_native_delete,
                danger = true,
                chevron = canClear,
                onClick = if (canClear) ({ confirmClearAll() }) else null
            )
            if (!canClear) clearRow.alpha = 0.45f
            content.addView(NativeUi.cardWithRow(this, clearRow))
            fun fillSongs() {
                list.removeAllViews()
                val groups = values.asReversed().groupBy {
                    it.optString("title").trim().lowercase() + "\u0000" + it.optString("artist").trim().lowercase()
                }.values.filter { group -> group.any {
                    (it.optString("title") + " " + it.optString("artist")).contains(songQuery.trim(), ignoreCase = true)
                } }
                if (groups.isEmpty()) {
                    list.addView(NativeUi.cardWithRow(this, NativeUi.row(
                        this,
                        title = if (values.isEmpty()) "暂无重匹配记录" else "没有符合搜索条件的歌曲",
                        iconRes = R.drawable.ic_setting_lyrics,
                        subtitle = if (values.isEmpty()) "播放歌曲并获取歌词后会记录在这里" else "换个关键词试试"
                    )))
                }
                groups.forEach { group ->
                    val item = group.first()
                    val card = NativeUi.card(this)
                    val rows = NativeUi.cardColumn(this)
                    rows.addView(NativeUi.row(
                        this,
                        title = item.optString("title").ifBlank { "未知歌曲" },
                        iconRes = R.drawable.ic_setting_lyrics,
                        subtitle = item.optString("artist").ifBlank { "未知歌手" }
                    ))
                    group.forEach { source ->
                        rows.addView(NativeUi.divider(this, 52))
                        val name = source.optString("source").ifBlank {
                            source.optJSONObject("candidate")?.optString("source").orEmpty()
                        }
                        val seconds = source.optLong("duration") / 1000
                        val variant = if (group.count { it.optString("source") == name } > 1) {
                            " · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
                        } else ""
                        rows.addView(NativeUi.row(
                            this,
                            title = "$name$variant",
                            iconRes = R.drawable.ic_home_library,
                            subtitle = "查看版本与预览",
                            chevron = true
                        ) {
                            selected = source.optString("key")
                            message = ""
                            render()
                        })
                    }
                    card.addView(rows)
                    list.addView(card)
                }
            }
            fillSongs()
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { songQuery = s.toString(); fillSongs() }
                override fun afterTextChanged(s: Editable?) {}
            })
            return
        }
        val key = entry.optString("key")
        content.addView(NativeUi.sectionLabel(this, "来源记忆"))
        content.addView(NativeUi.cardWithRow(this, NativeUi.row(
            this,
            title = entry.optString("title").ifBlank { "未知歌曲" },
            iconRes = R.drawable.ic_setting_lyrics,
            subtitle = entry.optString("artist").ifBlank { "未知歌手" } +
                " · " + entry.optString("source").ifBlank { "歌词源" }
        )))
        val actions = NativeUi.card(this)
        val actionRows = NativeUi.cardColumn(this)
        val searchRow = NativeUi.row(
            this,
            title = if (busy) "正在搜索…" else "搜索下一个匹配结果（仅预览）",
            iconRes = R.drawable.ic_home_refresh,
            chevron = !busy,
            onClick = if (!busy) ({ search(entry) }) else null
        )
        if (busy) searchRow.alpha = 0.45f
        actionRows.addView(searchRow)
        entry.optJSONObject("original")?.let { original ->
            actionRows.addView(NativeUi.divider(this))
            actionRows.addView(NativeUi.row(
                this,
                title = "恢复最初的歌词",
                iconRes = R.drawable.ic_setting_history,
                chevron = true
            ) {
                update(key) { it.put("candidate", original).put("needsReview", false) }
                message = "已恢复最初的歌词"
                render()
            })
        }
        actionRows.addView(NativeUi.divider(this))
        val deleteRow = NativeUi.row(
            this,
            title = "删除记忆 / 恢复自动匹配",
            iconRes = R.drawable.ic_native_delete,
            danger = true,
            chevron = !busy,
            onClick = if (!busy) ({
                NativeUi.confirm(
                    this,
                    "删除这首歌的来源记忆？",
                    "将删除《${entry.optString("title")}》在 ${entry.optString("source")} 的选择及历史版本，下次搜索恢复自动匹配。",
                    "删除记忆"
                ) {
                    save(entries().filter { it.optString("key") != key })
                    selected = null
                    message = "已删除，下次搜索使用自动匹配"
                    render()
                }
            }) else null
        )
        if (busy) deleteRow.alpha = 0.45f
        actionRows.addView(deleteRow)
        actions.addView(actionRows)
        content.addView(actions)
        val history = entry.optJSONArray("history") ?: JSONArray().put(entry.optJSONObject("candidate"))
        if (history.length() > 0) content.addView(NativeUi.sectionLabel(this, "候选版本"))
        for (i in 0 until history.length()) {
            val candidate = history.optJSONObject(i) ?: continue
            val current = !entry.optBoolean("needsReview") && candidate.optString("recordId") == entry.optJSONObject("candidate")?.optString("recordId")
            val seconds = candidate.optLong("duration") / 1000
            val meta = buildString {
                append("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}")
                append(" · ")
                append(if (candidate.optString("wordLyrics").isNotBlank()) "含逐字数据" else "普通歌词")
                if (candidate.optString("translatedLyrics").isNotBlank()) append(" · 含译文")
            }
            val card = NativeUi.card(this)
            val rows = NativeUi.cardColumn(this)
            rows.addView(NativeUi.row(
                this,
                title = (if (current) "✓ 当前选择 · " else "") +
                    candidate.optString("title").ifBlank { entry.optString("title").ifBlank { "旧版候选" } },
                iconRes = R.drawable.ic_home_library,
                subtitle = listOf(
                    candidate.optString("artist").ifBlank { entry.optString("artist") },
                    candidate.optString("source"),
                    meta
                ).filter { it.isNotBlank() }.joinToString(" · "),
                value = if (current) "使用中" else null
            ))
            rows.addView(NativeUi.divider(this))
            rows.addView(NativeUi.row(
                this,
                title = if (current) "正在使用" else "选用这个版本",
                iconRes = if (current) R.drawable.ic_setting_shield else R.drawable.ic_home_refresh,
                chevron = !current && !busy,
                onClick = if (current || busy) null else ({
                    // 手动选用 → 固定为手动记忆（清掉 auto/needsReview），悬浮窗与主页都以它为准。
                    // 注意 JSONObject.remove() 返回被删值而非 this，不能接在 put 链上。
                    update(key) {
                        it.remove("auto")
                        it.put("candidate", candidate).put("needsReview", false).put("at", System.currentTimeMillis())
                    }
                })
            ))
            val preview = candidate.optString("lyrics").lineSequence().map { it.replace(Regex("^(\\[[^]]+])+"), "").trim() }
                .filter { it.isNotBlank() }.take(16).joinToString("\n")
            if (preview.isNotBlank()) {
                rows.addView(NativeUi.divider(this))
                rows.addView(NativeUi.text(this, preview, 14f).apply {
                    setPadding(NativeUi.dp(context, 16), NativeUi.dp(context, 10), NativeUi.dp(context, 16), NativeUi.dp(context, 14))
                })
            }
            card.addView(rows)
            content.addView(card)
        }
    }
    private fun confirmClearAll() {
        if (busy) return
        val count = entries().map { it.optString("title") + "\u0000" + it.optString("artist") }.distinct().size
        NativeUi.confirm(
            this,
            "清除匹配记录与缓存？",
            "将清除 $count 首歌曲的匹配记录（包括手动选定的版本与历史版本），以及主页缓存的歌词。此操作无法撤销。\n\n自定义歌词、翻译语言包和同步设置都会保留。",
            "确认清除"
        ) {
            save(emptyList())
            getSharedPreferences("home_lyric_cache_v1", Context.MODE_PRIVATE).edit().clear().apply()
            selected = null
            message = "已清除匹配记录与缓存，下次搜索恢复自动匹配"
            render()
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
            val values = entries()
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
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun label(value: String, size: Float) = NativeUi.text(this, value, size).apply {
        setTextColor(if (size >= 17) col(R.color.text_primary) else col(R.color.text_secondary))
        setPadding(0, dp(6), 0, dp(8))
    }
}
