package com.luoh.music.lrc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lists manual LRC assignments without changing their matching or persistence rules. */
class CustomLyricsManagerActivity : AppCompatActivity() {
    private lateinit var content: RecyclerView
    private var loadJob: Job? = null
    private data class Item(val entry: CustomLyricsStore.Entry?, val title: String, val artist: String, val summary: String)

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(state: Bundle?) {
        ThemePrefs.apply(getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE).getString(ThemePrefs.KEY, ThemePrefs.FOLLOW))
        super.onCreate(state)
        content = NativeUi.listScreen(this, "自定义歌词")
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val playing = NowPlaying.current(this)
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                val saved = CustomLyricsStore.all(this@CustomLyricsManagerActivity).map { entry ->
                    val parsed = CustomLyricsStore.parse(entry.lyrics)
                    Item(entry, entry.title.ifBlank { "未命名" }, entry.artist.ifBlank { "所有歌手" },
                        "${parsed.lineCount} 行歌词${if (parsed.translationCount > 0) " · 含译文" else ""}" +
                            " · ${timeFormat.format(Date(entry.updatedAt))}")
                }
                buildList {
                    playing?.let { add(Item(null, it.title.ifBlank { "未知歌曲" }, it.artist, "正在播放")) }
                    add(Item(null, "新建自定义歌词", "手动填写歌名或导入 LRC · 已保存 ${saved.size} 份", ""))
                    if (saved.isEmpty()) add(Item(null, "还没有自定义歌词", "", "")) else addAll(saved)
                }
            }
            NativeUi.items(content, rows, { LibraryRow(it) }) { view, item ->
                val row = view as LibraryRow
                val entry = item.entry
                when {
                    entry != null -> row.bind(item.title, item.artist, item.summary,
                        "编辑", { startActivity(CustomLyricsEditActivity.editIntent(this@CustomLyricsManagerActivity, entry.id)) },
                        "删除", { confirmDelete(entry) })
                    item.summary == "正在播放" && playing != null -> row.bind(item.title, item.artist, item.summary,
                        "添加或编辑歌词", { startActivity(CustomLyricsEditActivity.intent(this@CustomLyricsManagerActivity, playing.title, playing.artist)) })
                    item.title == "新建自定义歌词" -> row.bind(item.title, item.artist, action = "新建", onAction = {
                        startActivity(CustomLyricsEditActivity.intent(this@CustomLyricsManagerActivity, "", ""))
                    })
                    else -> row.bind(item.title, item.artist)
                }
            }
        }
    }

    override fun onPause() {
        loadJob?.cancel()
        super.onPause()
    }

    private fun confirmDelete(entry: CustomLyricsStore.Entry) {
        NativeUi.confirm(this, "删除这份自定义歌词？",
            "${entry.title.ifBlank { "未命名" }}${if (entry.artist.isNotBlank()) " · ${entry.artist}" else ""}\n删除后这首歌会恢复自动匹配。", "确认删除") {
            CustomLyricsStore.delete(this, entry.id)
            LyricsOverlayService.instance?.reloadCustomLyrics()
            render()
        }
    }
}
