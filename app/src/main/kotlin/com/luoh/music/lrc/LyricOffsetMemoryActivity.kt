package com.luoh.music.lrc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class LyricOffsetMemoryActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE) }
    private lateinit var content: LinearLayout

    private data class Entry(val id: String, val title: String, val artist: String, val source: String, val offsetMs: Int, val updatedAt: Long)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        content = NativeUi.screen(this, "同步记忆")
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        val entries = readEntries()
        if (entries.isEmpty()) {
            content.addView(NativeUi.cardWithRow(this, NativeUi.row(
                this,
                title = "还没有保存的同步调整",
                iconRes = R.drawable.ic_setting_history,
                subtitle = "调整歌词同步后会自动记录在这里"
            )))
            return
        }
        content.addView(NativeUi.sectionLabel(this, "已保存 ${entries.size} 条同步调整"))
        val card = NativeUi.card(this)
        val rows = NativeUi.cardColumn(this)
        entries.forEachIndexed { index, entry ->
            if (index > 0) rows.addView(NativeUi.divider(this))
            val subtitle = listOf(entry.artist, entry.source)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "歌词源" }
            rows.addView(NativeUi.row(
                this,
                title = entry.title.ifBlank { "未知歌曲" },
                iconRes = R.drawable.ic_setting_history,
                subtitle = subtitle,
                value = LyricSyncText.format(entry.offsetMs),
                trailing = NativeUi.dangerButton(this, "删除") {
                    NativeUi.confirm(
                        this,
                        "删除这条同步记忆？",
                        "《${entry.title}》在 ${entry.source} 的歌词将恢复原始同步时间。",
                        "删除记忆"
                    ) { delete(entry) }
                }
            ))
        }
        card.addView(rows)
        content.addView(card)
        content.addView(NativeUi.cardWithRow(this, NativeUi.row(
            this,
            title = "重置全部同步记忆",
            iconRes = R.drawable.ic_native_delete,
            danger = true,
            chevron = true
        ) { showClearDialog() }))
    }

    private fun readEntries(): List<Entry> {
        val index = runCatching {
            JSONObject(prefs.getString(LyricsOverlayService.PREF_LYRIC_OFFSET_INDEX, "{}").orEmpty().ifBlank { "{}" })
        }.getOrDefault(JSONObject())
        return index.keys().asSequence().mapNotNull { id ->
            val value = index.optJSONObject(id) ?: return@mapNotNull null
            val identity = value.optString("identity")
            val fallback = identity.split('\u0000', limit = 2)
            Entry(id, value.optString("title").ifBlank { fallback.getOrNull(0).orEmpty() },
                value.optString("artist").ifBlank { fallback.getOrNull(1).orEmpty() }, value.optString("source"),
                value.optInt("offsetMs", 0), value.optLong("updatedAt", 0)).takeIf { it.offsetMs != 0 }
        }.sortedByDescending { it.updatedAt }.toList()
    }

    private fun delete(entry: Entry) {
        val key = LyricsOverlayService.PREF_LYRIC_OFFSET_ENTRY_PREFIX + entry.id
        removeIndexEntry(entry.id, key)
        if (LyricsOverlayService.isRunning) {
            startService(Intent(this, LyricsOverlayService::class.java).apply {
                action = LyricsOverlayService.ACTION_DELETE_LYRIC_OFFSET_MEMORY
                putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MEMORY_KEY, key)
            })
        }
        render()
    }

    private fun removeIndexEntry(id: String, preferenceKey: String) {
        val index = runCatching {
            JSONObject(prefs.getString(LyricsOverlayService.PREF_LYRIC_OFFSET_INDEX, "{}").orEmpty().ifBlank { "{}" })
        }.getOrDefault(JSONObject())
        index.remove(id)
        prefs.edit().remove(preferenceKey).putString(LyricsOverlayService.PREF_LYRIC_OFFSET_INDEX, index.toString()).apply()
    }

    private fun clearAll() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(LyricsOverlayService.PREF_LYRIC_OFFSET_ENTRY_PREFIX) }.forEach { editor.remove(it) }
        editor.remove(LyricsOverlayService.PREF_LYRIC_OFFSET_INDEX).putInt(LyricsOverlayService.PREF_LYRIC_OFFSET_MS, 0).apply()
        if (LyricsOverlayService.isRunning) {
            startService(Intent(this, LyricsOverlayService::class.java).apply { action = LyricsOverlayService.ACTION_CLEAR_LYRIC_OFFSET_MEMORIES })
        }
        render()
    }

    private fun showClearDialog() {
        NativeUi.confirm(this, "重置全部同步调整？", "所有歌曲和歌词源都会恢复原始同步时间，此操作无法撤销。", "确认重置") { clearAll() }
    }
}
