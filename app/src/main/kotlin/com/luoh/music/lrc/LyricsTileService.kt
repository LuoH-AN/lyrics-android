package com.luoh.music.lrc

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Reflects the overlay service, including stops from its floating close button. */
class LyricsTileService : TileService() {
    private var listening = false
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        if (!listening) {
            ContextCompat.registerReceiver(this, stateReceiver,
                IntentFilter(LyricsOverlayService.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            listening = true
        }
        updateTileState()
    }

    override fun onStopListening() {
        if (listening) { unregisterReceiver(stateReceiver); listening = false }
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::toggleOverlay) else toggleOverlay()
    }

    private fun toggleOverlay() {
        if (LyricsOverlayService.isRunning) {
            stopService(Intent(this, LyricsOverlayService::class.java))
        } else if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName) ||
            !Settings.canDrawOverlays(this)) {
            openPermissions()
        } else {
            try {
                ContextCompat.startForegroundService(this,
                    Intent(this, LyricsOverlayService::class.java).setAction(LyricsOverlayService.ACTION_START))
            } catch (_: IllegalStateException) {
                openPermissions()
            } catch (_: SecurityException) {
                openPermissions()
            }
        }
        updateTileState()
    }

    @Suppress("DEPRECATION")
    private fun openPermissions() {
        val intent = SettingsActivity.intent(this, SettingsActivity.Section.ACCESS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else startActivityAndCollapse(intent)
    }

    private fun updateTileState() {
        qsTile?.apply {
            state = if (LyricsOverlayService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= 29) subtitle = if (LyricsOverlayService.isRunning) "已开启" else "已关闭"
            contentDescription = "桌面歌词：${if (LyricsOverlayService.isRunning) "已开启" else "已关闭"}"
            updateTile()
        }
    }

    companion object {
        fun requestAdd(context: Context) {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                    ComponentName(context, LyricsTileService::class.java), context.getString(R.string.app_name),
                    Icon.createWithResource(context, R.drawable.ic_notification), context.mainExecutor,
                ) { result ->
                    val message = when (result) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已添加桌面歌词快捷开关"
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "快捷开关已添加，可在下拉面板中使用"
                        else -> "可在下拉快捷设置的编辑页面添加桌面歌词"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            } else Toast.makeText(context, "下拉快捷设置，点编辑并添加「桌面歌词」", Toast.LENGTH_LONG).show()
        }

        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, LyricsTileService::class.java)) }
        }
    }
}
