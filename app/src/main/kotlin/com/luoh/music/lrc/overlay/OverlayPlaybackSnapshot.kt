package com.luoh.music.lrc.overlay

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Base64
import com.luoh.music.lrc.data.lyrics.SongSearchCorrections
import com.luoh.music.lrc.diagnostics.UsageAnalytics
import com.luoh.music.lrc.playback.firstMetadataString
import com.luoh.music.lrc.playback.playbackPositionMs
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max

internal fun overlayTrackTitle(metadata: MediaMetadata?): String = firstMetadataString(
    metadata,
    MediaMetadata.METADATA_KEY_TITLE,
    MediaMetadata.METADATA_KEY_DISPLAY_TITLE
)

internal class OverlayPlaybackSnapshot(private val context: Context) {
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var cachedArtworkKey = ""
    private var cachedArtworkDataUrl = ""

    fun clearArtwork() {
        cachedArtworkKey = ""
        cachedArtworkDataUrl = ""
    }

    fun build(controller: MediaController): JSONObject {
        val metadata = controller.metadata
        val playback = controller.playbackState
        if (playback?.state == PlaybackState.STATE_PLAYING) UsageAnalytics.playerUsed(controller.packageName)
        val title = overlayTrackTitle(metadata)
        val artist = firstMetadataString(
            metadata,
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_AUTHOR,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
        )
        val album = firstMetadataString(metadata, MediaMetadata.METADATA_KEY_ALBUM)
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val state = when (playback?.state) {
            PlaybackState.STATE_PLAYING -> "playing"
            PlaybackState.STATE_PAUSED -> "paused"
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> "buffering"
            PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE -> "stopped"
            else -> "paused"
        }
        val speed = playback?.playbackSpeed?.toDouble() ?: 0.0
        val actions = playback?.actions ?: 0L
        val position = playbackPositionMs(playback, duration)
        val artwork = artworkDataUrl(metadata, "$title\u0000$artist\u0000$album")

        return JSONObject()
            .put("hasSession", title.isNotBlank() || playback != null)
            .put("permissionRequired", false)
            .put("track", title)
            .put("searchRevision", SongSearchCorrections.resolve(context, title, artist).revision)
            .put("artist", artist)
            .put("album", album)
            .put("packageName", controller.packageName)
            .put("state", state)
            .put("positionMs", position)
            .put("durationMs", max(0L, duration))
            .put("speed", if (speed.isFinite()) speed else 1.0)
            .put(
                "canPlay",
                actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE) != 0L
            )
            .put(
                "canPause",
                actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) != 0L
            )
            .put("canPrevious", actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L)
            .put("canNext", actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
            .put("canSeek", actions and PlaybackState.ACTION_SEEK_TO != 0L)
            .put("capturedAtMs", System.currentTimeMillis())
            .put("cover", artwork)
            .put("volumePct", mediaVolumePercent())
    }

    private fun mediaVolumePercent(): Int {
        return try {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVolume > 0) (current * 100f / maxVolume).toInt().coerceIn(0, 100) else 0
        } catch (_: Exception) {
            0
        }
    }

    private fun artworkDataUrl(metadata: MediaMetadata?, key: String): String {
        // Media apps often publish title/artist first and artwork in a later callback.
        // Do not permanently cache an empty first result for the lifetime of the track.
        if (key == cachedArtworkKey && cachedArtworkDataUrl.isNotEmpty()) {
            return cachedArtworkDataUrl
        }
        cachedArtworkKey = key
        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        cachedArtworkDataUrl = bitmap?.let { bitmapDataUrl(it) }.orEmpty()
        return cachedArtworkDataUrl
    }

    private fun bitmapDataUrl(source: Bitmap): String {
        return try {
            val maxSide = max(source.width, source.height)
            val scaled = if (maxSide > 640) {
                val ratio = 640f / maxSide.toFloat()
                Bitmap.createScaledBitmap(
                    source,
                    max(1, (source.width * ratio).toInt()),
                    max(1, (source.height * ratio).toInt()),
                    true
                )
            } else {
                source
            }
            val bytes = ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, output)
                output.toByteArray()
            }
            if (scaled !== source) scaled.recycle()
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }
}
