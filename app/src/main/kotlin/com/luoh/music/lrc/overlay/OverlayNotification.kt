package com.luoh.music.lrc.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.LyricsOverlayService.Companion.ACTION_LOCK
import com.luoh.music.lrc.LyricsOverlayService.Companion.ACTION_STOP
import com.luoh.music.lrc.LyricsOverlayService.Companion.ACTION_UNLOCK
import com.luoh.music.lrc.MainActivity
import com.luoh.music.lrc.R

@SuppressLint("ForegroundServiceType")
internal class OverlayNotification(private val service: Service) {
    private val manager: NotificationManager
        get() = service.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager

    fun refresh(positionLocked: Boolean) {
        manager.notify(NOTIFICATION_ID, build(positionLocked))
    }

    fun start(positionLocked: Boolean) {
        val notification = build(positionLocked)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun build(positionLocked: Boolean): Notification {
        val openIntent = Intent(service, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            service,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(service, LyricsOverlayService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            service,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val movementPendingIntent = PendingIntent.getService(
            service,
            if (positionLocked) 2 else 3,
            Intent(service, LyricsOverlayService::class.java).setAction(if (positionLocked) ACTION_UNLOCK else ACTION_LOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val controls = RemoteViews(service.packageName, R.layout.notification_lyrics).apply {
            setTextViewText(R.id.notification_lyrics_state, if (positionLocked) "已锁定" else "可拖动")
            setImageViewResource(R.id.notification_lyrics_lock,
                if (positionLocked) R.drawable.ic_overlay_locked else R.drawable.ic_overlay_unlocked)
            setContentDescription(R.id.notification_lyrics_lock, if (positionLocked) "解锁" else "锁定")
            setOnClickPendingIntent(R.id.notification_lyrics_lock, movementPendingIntent)
            setOnClickPendingIntent(R.id.notification_lyrics_close, stopPendingIntent)
        }

        return NotificationCompat.Builder(service, LyricsOverlayService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("桌面歌词")
            .setContentText(if (positionLocked) "已锁定" else "可拖动")
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(controls)
            .setCustomBigContentView(controls)
            .build()
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(
                LyricsOverlayService.CHANNEL_ID,
                "桌面歌词",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持桌面歌词与正在播放的音乐实时同步"
                setShowBadge(false)
            }
        )
    }

    private companion object {
        const val NOTIFICATION_ID = 4202
    }
}
