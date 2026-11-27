package com.luoh.music.lrc.ui.settings

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.luoh.music.lrc.data.lyrics.TemporaryCache
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.icons.filled.Layers
import com.luoh.music.lrc.ui.icons.filled.Link
import com.luoh.music.lrc.ui.icons.filled.Update
import com.luoh.music.lrc.ui.navigation.ComposeRoute
import com.luoh.music.lrc.update.AppUpdateDialog
import com.luoh.music.lrc.update.LocalAppUpdates
import com.luoh.music.lrc.update.UpdateChecker
import com.luoh.music.lrc.update.rememberAppUpdateModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun rememberSettingsBusinessScope(): CoroutineScope {
    val scope = rememberCoroutineScope()
    val active = LocalSettingsScrollRetentionActive.current
    LaunchedEffect(active) {
        if (!active) scope.coroutineContext.cancelChildren()
    }
    return scope
}

@Composable
internal fun AboutSettings(context: Context, onNavigate: (ComposeRoute) -> Unit) {
    val sharedUpdates = LocalAppUpdates.current
    val updates = sharedUpdates ?: rememberAppUpdateModel()
    val version = updates.currentVersion
    var clearingCache by remember { mutableStateOf(false) }
    val scope = rememberSettingsBusinessScope()
    SettingsGroup(title = "应用", items = listOf(
        {
            SettingsNavigationItem(if (updates.stable) "检查更新" else "Nightly 构建",
                if (updates.checking) "检查中…" else version, Icons.Default.Update, !updates.checking) {
                if (!updates.stable) {
                    settingsOpenUrl(context, UpdateChecker.NIGHTLY_BUILDS_URL)
                } else {
                    scope.launch {
                        updates.checkManually().onSuccess {
                            if (!UpdateChecker.isNewer(it.version, version)) settingsToast(context, "当前已是最新版")
                        }.onFailure {
                            settingsToast(context, if (it is UpdateChecker.NoReleaseException) "暂无已发布版本" else "更新检查失败，请稍后重试")
                        }
                    }
                }
            }
        },
        { SettingsNavigationItem("维护者 · LuoH-AN", icon = Icons.Default.Person) { settingsOpenUrl(context, "https://github.com/LuoH-AN") } },
        { SettingsNavigationItem("项目主页 · GitHub", icon = Icons.Default.Link) { settingsOpenUrl(context, "https://github.com/LuoH-AN/lyrics-android") } },
        { SettingsNavigationItem("隐私说明", icon = Icons.Default.Lock) { onNavigate(ComposeRoute("privacy")) } },
    ))
    SettingsGroup(title = "帮助改进", items = listOf(
        { UsageAnalyticsSettings() },
    ))
    SettingsGroup(title = "存储", items = listOf({
        SettingsNavigationItem("清理临时缓存", "清理译文与分享图片，保留设置、歌词及语言包", Icons.Default.Layers, !clearingCache) {
            clearingCache = true
            scope.launch {
                try {
                    val cleared = withContext(Dispatchers.IO) { TemporaryCache.clear(context) }
                    settingsToast(context, if (cleared) "临时缓存已清理" else "部分缓存暂时无法清理，请稍后重试")
                } finally { clearingCache = false }
            }
        }
    }))
    if (sharedUpdates == null) AppUpdateDialog(updates)
}
