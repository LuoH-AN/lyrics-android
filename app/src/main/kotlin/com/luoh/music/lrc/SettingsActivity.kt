package com.luoh.music.lrc
import android.content.Context
import android.content.Intent
class SettingsActivity : ComposeNavigationActivity() {
    enum class Section { ACCESS, OVERLAY, SYNC, LYRICS, APPEARANCE, HOME, MOTION, ABOUT }
    companion object {
        fun intent(context: Context, section: Section) = Intent(context, SettingsActivity::class.java).putExtra("settings_section", section.name)
    }
}
