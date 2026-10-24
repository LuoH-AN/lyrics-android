package com.luoh.music.lrc

import android.content.Context
import android.content.Intent

/** Android entry point; in-app navigation uses the shared page host. */
class CustomLyricsEditActivity : ComposeNavigationActivity() {
    companion object {
        private const val EXTRA_ID = "custom_lyrics_id"
        private const val EXTRA_TITLE = "custom_lyrics_title"
        private const val EXTRA_ARTIST = "custom_lyrics_artist"
        private const val MAX_FILE_BYTES = 1_000_000

        /** 按歌名/歌手打开：已有自定义歌词就编辑它，否则新建并预填。 */
        fun intent(context: Context, title: String, artist: String): Intent =
            Intent(context, CustomLyricsEditActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)

        fun editIntent(context: Context, id: String): Intent =
            Intent(context, CustomLyricsEditActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
