package com.luoh.music.lrc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.luoh.music.lrc.overlay.OverlayNotificationAccess
import com.luoh.music.lrc.ui.navigation.ComposeNavigationActivity

class SettingsActivity : ComposeNavigationActivity() {
    enum class Section { ACCESS, OVERLAY, SYNC, LYRICS, APPEARANCE, HOME, MOTION, ABOUT }

    private var startOverlayOnResume = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        startOverlayFromTile()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startOverlayOnResume = savedInstanceState == null && intent.getBooleanExtra(EXTRA_START_OVERLAY, false)
    }

    override fun onPostResume() {
        super.onPostResume()
        if (!startOverlayOnResume) return
        startOverlayOnResume = false
        if (OverlayNotificationAccess.needsPermission(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startOverlayFromTile()
        }
    }

    private fun startOverlayFromTile() {
        LyricsOverlayService.start(this)
        finish()
    }

    companion object {
        private const val EXTRA_START_OVERLAY = "start_overlay"

        fun intent(context: Context, section: Section, startOverlay: Boolean = false) =
            Intent(context, SettingsActivity::class.java).putExtra("settings_section", section.name).apply {
                if (startOverlay) putExtra(EXTRA_START_OVERLAY, true)
            }
    }
}
