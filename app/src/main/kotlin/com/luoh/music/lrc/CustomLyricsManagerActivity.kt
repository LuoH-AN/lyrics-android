package com.luoh.music.lrc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
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
            content.addView(NativeUi.sectionLabel(this, "正在播放"))
            content.addView(NativeUi.cardWithRow(this, NativeUi.row(
                this,
                title = track.title.ifBlank { "未知歌曲" },
                iconRes = R.drawable.ic_home_music,
                subtitle = buildString {
                    append(track.artist.ifBlank { "未知歌手" })
                    append(if (existing != null) " · 已有自定义歌词" else " · 点击添加")
                },
                chevron = true
            ) {
                startActivity(CustomLyricsEditActivity.intent(this, track.title, track.artist))
            }))
        }
        content.addView(NativeUi.button(this, "手动填写歌名新建") {
            startActivity(CustomLyricsEditActivity.intent(this, "", ""))
        })
        val entries = CustomLyricsStore.all(this)
        if (entries.isEmpty()) {
            content.addView(NativeUi.cardWithRow(this, NativeUi.row(
                this,
                title = "还没有自定义歌词",
                iconRes = R.drawable.ic_home_edit,
                subtitle = "为正在播放的歌曲或手动填写歌名添加"
            )))
            return
        }
        content.addView(NativeUi.sectionLabel(this, "已保存 ${entries.size} 份"))
        val card = NativeUi.card(this)
        val rows = NativeUi.cardColumn(this)
        entries.forEachIndexed { index, entry ->
            if (index > 0) rows.addView(NativeUi.divider(this))
            val parsed = CustomLyricsStore.parse(entry.lyrics)
            val summary = buildString {
                append("${parsed.lineCount} 行歌词")
                if (parsed.translationCount > 0) append(" · 含译文")
                append(" · 更新于 ${timeFormat.format(Date(entry.updatedAt))}")
            }
            rows.addView(NativeUi.row(
                this,
                title = entry.title.ifBlank { "未命名" },
                iconRes = R.drawable.ic_home_edit,
                subtitle = "${entry.artist.ifBlank { "所有歌手" }} · $summary",
                chevron = true,
                trailing = NativeUi.dangerButton(this, "删除") { confirmDelete(entry) }
            ) {
                startActivity(CustomLyricsEditActivity.editIntent(this, entry.id))
            })
        }
        card.addView(rows)
        content.addView(card)
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
