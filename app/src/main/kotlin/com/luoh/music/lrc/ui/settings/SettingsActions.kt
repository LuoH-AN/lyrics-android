package com.luoh.music.lrc.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.R
import com.luoh.music.lrc.diagnostics.UsageAnalytics

internal fun settingsOverlayAction(context: Context, action: String, extras: Intent.() -> Unit = {}) {
    if (!LyricsOverlayService.isRunning) return
    runCatching { context.startService(Intent(context, LyricsOverlayService::class.java).setAction(action).apply(extras)) }
        .onFailure { settingsToast(context, context.getString(R.string.the_lyrics_overlay_is_temporarily_unavailable)) }
}

internal fun settingsOpenIntent(context: Context, intent: Intent) {
    when (intent.action) {
        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS -> UsageAnalytics.permissionRequested(UsageAnalytics.Permission.NOTIFICATION)
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION -> UsageAnalytics.permissionRequested(UsageAnalytics.Permission.OVERLAY)
    }
    runCatching { context.startActivity(intent) }.onFailure { settingsToast(context, context.getString(R.string.could_not_open_this_page)) }
}

internal fun settingsOpenUrl(context: Context, url: String) = settingsOpenIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
internal fun settingsToast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
internal fun settingsHexColor(color: Int): String = "#%06X".format(color and 0xFFFFFF)
internal fun settingsParseColor(hex: String): Color = Color(runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(android.graphics.Color.WHITE))
