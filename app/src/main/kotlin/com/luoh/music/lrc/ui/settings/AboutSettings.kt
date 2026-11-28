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
import com.luoh.music.lrc.R
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
    SettingsGroup(title = context.getString(R.string.app), items = listOf(
        {
            SettingsNavigationItem(if (updates.stable) context.getString(R.string.check_for_updates) else context.getString(R.string.nightly_builds),
                if (updates.checking) context.getString(R.string.checking) else version, Icons.Default.Update, !updates.checking) {
                if (!updates.stable) {
                    settingsOpenUrl(context, UpdateChecker.NIGHTLY_BUILDS_URL)
                } else {
                    scope.launch {
                        updates.checkManually().onSuccess {
                            if (!UpdateChecker.isNewer(it.version, version)) settingsToast(context, context.getString(R.string.you_have_the_latest_version))
                        }.onFailure {
                            settingsToast(context, if (it is UpdateChecker.NoReleaseException) context.getString(R.string.no_releases_available) else context.getString(R.string.update_check_failed))
                        }
                    }
                }
            }
        },
        { SettingsNavigationItem(context.getString(R.string.maintainer_luoh_an), icon = Icons.Default.Person) { settingsOpenUrl(context, "https://github.com/LuoH-AN") } },
        { SettingsNavigationItem(context.getString(R.string.project_on_github), icon = Icons.Default.Link) { settingsOpenUrl(context, "https://github.com/LuoH-AN/lyrics-android") } },
        { SettingsNavigationItem(context.getString(R.string.privacy), icon = Icons.Default.Lock) { onNavigate(ComposeRoute("privacy")) } },
    ))
    SettingsGroup(title = context.getString(R.string.help_improve_the_app), items = listOf(
        { UsageAnalyticsSettings() },
    ))
    SettingsGroup(title = context.getString(R.string.storage), items = listOf({
        SettingsNavigationItem(context.getString(R.string.clear_temporary_cache), context.getString(R.string.clear_temporary_cache_description), Icons.Default.Layers, !clearingCache) {
            clearingCache = true
            scope.launch {
                try {
                    val cleared = withContext(Dispatchers.IO) { TemporaryCache.clear(context) }
                    settingsToast(context, if (cleared) context.getString(R.string.temporary_cache_cleared) else context.getString(R.string.temporary_cache_clear_failed))
                } finally { clearingCache = false }
            }
        }
    }))
    if (sharedUpdates == null) AppUpdateDialog(updates)
}
