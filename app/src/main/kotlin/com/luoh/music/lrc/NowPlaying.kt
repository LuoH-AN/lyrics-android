package com.luoh.music.lrc

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

/** 读取当前正在播放的歌曲，给自定义歌词页预填歌名和歌手。 */
object NowPlaying {
    data class Track(val title: String, val artist: String)

    fun current(context: Context): Track? {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val controllers = try {
            manager.getActiveSessions(ComponentName(context, MediaListenerService::class.java))
        } catch (_: SecurityException) {
            return null
        }
        val best = controllers
            .filter { it.packageName != context.packageName }
            .maxByOrNull(::score) ?: return null
        val metadata = best.metadata ?: return null
        val title = firstString(
            metadata,
            MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_DISPLAY_TITLE
        )
        if (title.isBlank()) return null
        val artist = firstString(
            metadata,
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_AUTHOR,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
        )
        return Track(title, artist)
    }

    fun score(controller: MediaController): Int {
        val stateScore = when (controller.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> 1000
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> 800
            PlaybackState.STATE_PAUSED -> 600
            else -> 100
        }
        val hasTitle = !controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
        return stateScore + if (hasTitle) 100 else 0
    }

    private fun firstString(metadata: MediaMetadata, vararg keys: String): String {
        for (key in keys) metadata.getString(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }
}
