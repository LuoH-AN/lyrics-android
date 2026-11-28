package com.luoh.music.lrc

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.luoh.music.lrc.diagnostics.UsageAnalytics
import com.luoh.music.lrc.overlay.OverlayNotificationAccess

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
        UsageAnalytics.feature(UsageAnalytics.Feature.TILE_TOGGLE)
        if (LyricsOverlayService.isRunning) {
            stopService(Intent(this, LyricsOverlayService::class.java))
        } else if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName) ||
            !Settings.canDrawOverlays(this)) {
            openPermissions()
        } else if (OverlayNotificationAccess.needsPermission(this)) {
            openPermissions(startOverlay = true)
        } else {
            try {
                LyricsOverlayService.start(this)
            } catch (_: IllegalStateException) {
                openPermissions()
            } catch (_: SecurityException) {
                openPermissions()
            }
        }
        updateTileState()
    }

    @Suppress("DEPRECATION")
    private fun openPermissions(startOverlay: Boolean = false) {
        val intent = SettingsActivity.intent(this, SettingsActivity.Section.ACCESS, startOverlay)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else startActivityAndCollapse(intent)
    }

    private fun updateTileState() {
        qsTile?.apply {
            icon = Icon.createWithResource(this@LyricsTileService, R.drawable.ic_quick_settings)
            state = if (LyricsOverlayService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            val status = getString(if (LyricsOverlayService.isRunning) R.string.enabled else R.string.disabled)
            if (Build.VERSION.SDK_INT >= 29) subtitle = status
            contentDescription = getString(R.string.lyrics_tile_description, status)
            updateTile()
        }
    }

    companion object {
        fun requestAdd(context: Context) {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                    ComponentName(context, LyricsTileService::class.java), context.getString(R.string.app_name),
                    Icon.createWithResource(context, R.drawable.ic_quick_settings), context.mainExecutor,
                ) { result ->
                    val message = when (result) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> context.getString(R.string.lyrics_tile_added)
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> context.getString(R.string.the_lyrics_tile_is_already_available)
                        else -> context.getString(R.string.you_can_add_the_lyrics_tile)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            } else Toast.makeText(context, context.getString(R.string.open_quick_settings_tap_edit_and), Toast.LENGTH_LONG).show()
        }

        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, LyricsTileService::class.java)) }
        }
    }
}
