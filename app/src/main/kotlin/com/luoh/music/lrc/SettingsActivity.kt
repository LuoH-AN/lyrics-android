package com.luoh.music.lrc

import android.content.Context
import android.content.Intent

/** Android entry point; in-app navigation uses the shared page host. */
class SettingsActivity : NativeNavigationActivity() {
    enum class Section(val title: String, val summary: String, val layout: Int, val icon: Int) {
        ACCESS("桌面歌词", "开关与权限", R.layout.settings_access, R.drawable.ic_home_lyrics),
        OVERLAY("桌面歌词外观", "字号、字重、上下文与颜色", R.layout.settings_overlay, R.drawable.ic_home_edit),
        SYNC("歌词同步", "调整时间与管理同步记忆", R.layout.settings_sync, R.drawable.ic_home_refresh),
        LYRICS("歌词与翻译", "显示方式、自定义歌词与歌词源", R.layout.settings_lyrics, R.drawable.ic_home_translate),
        APPEARANCE("应用外观", "主题与主调颜色", R.layout.settings_appearance, R.drawable.ic_home_settings),
        HOME("主页歌词", "歌词对齐方式", R.layout.settings_home, R.drawable.ic_home_music),
        MOTION("主页歌词动效", "滚动、错位、缩放与模糊", R.layout.settings_motion, R.drawable.ic_home_play),
        ABOUT("关于", "版本、更新与项目信息", R.layout.settings_about, R.drawable.ic_home_library)
    }

    companion object {
        private const val EXTRA_SECTION = "settings_section"
        fun intent(context: Context, section: Section) = Intent(context, SettingsActivity::class.java)
            .putExtra(EXTRA_SECTION, section.name)
    }

}
