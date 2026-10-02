package com.luoh.music.lrc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lists manual LRC assignments without changing their matching or persistence rules. */
class CustomLyricsManagerActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(state: Bundle?) {
        ThemePrefs.apply(getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE).getString(ThemePrefs.KEY, ThemePrefs.FOLLOW))
        super.onCreate(state)
        content = NativeUi.screen(this, "自定义歌词")
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        NowPlaying.current(this)?.let { track ->
            val existing = CustomLyricsStore.find(this, track.title, track.artist)
            val body = NativeUi.column(this)
            body.addView(NativeUi.text(this, "正在播放", 12f))
            body.addView(NativeUi.text(this, track.title.ifBlank { "未知歌曲" }, 17f).apply {
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            })
            if (track.artist.isNotBlank()) body.addView(NativeUi.text(this, track.artist, 12f))
            body.addView(NativeUi.button(this, if (existing != null) "编辑这首歌的歌词" else "为这首歌指定 LRC 歌词", true) {
                startActivity(CustomLyricsEditActivity.intent(this, track.title, track.artist))
            })
            content.addView(NativeUi.card(this).apply { addView(body) })
        }
        content.addView(NativeUi.button(this, "手动填写歌名新建") {
            startActivity(CustomLyricsEditActivity.intent(this, "", ""))
        })
        val entries = CustomLyricsStore.all(this)
        if (entries.isEmpty()) {
            content.addView(NativeUi.card(this).apply {
                addView(NativeUi.column(context).apply {
                    addView(NativeUi.text(context, "还没有自定义歌词", 15f).apply { gravity = Gravity.CENTER })
                })
            })
            return
        }
        content.addView(NativeUi.text(this, "已保存 ${entries.size} 份", 12f).apply {
            setPadding(0, NativeUi.dp(context, 16), 0, 0)
        })
        entries.forEach { entry ->
            val body = NativeUi.column(this)
            body.addView(NativeUi.text(this, entry.title.ifBlank { "未命名" }, 17f).apply {
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            })
            body.addView(NativeUi.text(this, entry.artist.ifBlank { "所有歌手" }, 12f))
            val parsed = CustomLyricsStore.parse(entry.lyrics)
            val summary = buildString {
                append("${parsed.lineCount} 行歌词")
                if (parsed.translationCount > 0) append(" · 含译文")
                append(" · 更新于 ${timeFormat.format(Date(entry.updatedAt))}")
            }
            body.addView(NativeUi.text(this, summary, 12f).apply { setPadding(0, NativeUi.dp(context, 8), 0, 0) })
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(NativeUi.button(context, "编辑") {
                    startActivity(CustomLyricsEditActivity.editIntent(this@CustomLyricsManagerActivity, entry.id))
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = NativeUi.dp(context, 6); topMargin = NativeUi.dp(context, 8) })
                addView(NativeUi.button(context, "删除") { confirmDelete(entry) },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = NativeUi.dp(context, 6); topMargin = NativeUi.dp(context, 8) })
            })
            content.addView(NativeUi.card(this).apply { addView(body) })
        }
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
