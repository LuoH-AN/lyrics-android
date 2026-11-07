package com.luoh.music.lrc

import android.widget.Toast
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luoh.music.lrc.ui.agora.SettingsItem

@Composable
internal fun UsageAnalyticsSettings() {
    val context = LocalContext.current
    val preferences by UsageAnalytics.preferences.collectAsStateWithLifecycle()
    SettingsItem(
        headlineContent = { Text("使用情况统计") },
        supportingContent = { Text("用于改进使用体验，可随时关闭。") },
        trailingContent = { Switch(checked = preferences.enabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(preferences.enabled, role = Role.Switch) { value ->
            if (!UsageAnalytics.setEnabled(value)) Toast.makeText(context, "无法保存设置，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    )
}
