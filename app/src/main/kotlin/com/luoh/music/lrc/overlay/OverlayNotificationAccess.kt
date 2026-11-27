package com.luoh.music.lrc.overlay

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.luoh.music.lrc.LyricsOverlayService

/** Reading media notifications and posting the overlay's own notification are separate permissions. */
internal object OverlayNotificationAccess {
    fun needsPermission(context: Context): Boolean = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun isEnabled(context: Context): Boolean {
        if (needsPermission(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.getNotificationChannel(LyricsOverlayService.CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun settingsIntent(context: Context): Intent {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelBlocked = !needsPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(LyricsOverlayService.CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        return Intent(if (channelBlocked) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .apply { if (channelBlocked) putExtra(Settings.EXTRA_CHANNEL_ID, LyricsOverlayService.CHANNEL_ID) }
    }
}
