package com.luoh.music.lrc.ui.library

import android.content.Context
import com.luoh.music.lrc.R
import com.luoh.music.lrc.data.lyrics.CustomLyricsStore
import com.luoh.music.lrc.data.lyrics.KuGouLyricsProvider
import com.luoh.music.lrc.data.lyrics.NetEaseLyricsProvider
import com.luoh.music.lrc.data.lyrics.QqMusicLyricsProvider

internal fun Context.lyricSourceLabel(source: String): String = when (source) {
    QqMusicLyricsProvider.SOURCE -> getString(R.string.source_qq)
    NetEaseLyricsProvider.SOURCE -> getString(R.string.source_netease)
    KuGouLyricsProvider.SOURCE -> getString(R.string.source_kugou)
    CustomLyricsStore.SOURCE -> getString(R.string.custom_lyrics)
    "歌词源", "" -> getString(R.string.lyrics_source)
    else -> source
}
