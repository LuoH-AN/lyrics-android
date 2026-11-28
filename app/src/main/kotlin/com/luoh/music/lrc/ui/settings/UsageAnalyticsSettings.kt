package com.luoh.music.lrc.ui.settings

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
import com.luoh.music.lrc.R
import com.luoh.music.lrc.diagnostics.UsageAnalytics
import com.luoh.music.lrc.ui.agora.SettingsItem

@Composable
internal fun UsageAnalyticsSettings() {
    val context = LocalContext.current
    val preferences by UsageAnalytics.preferences.collectAsStateWithLifecycle()
    SettingsItem(
        headlineContent = { Text(context.getString(R.string.usage_analytics)) },
        trailingContent = { Switch(checked = preferences.enabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(preferences.enabled, role = Role.Switch) { value ->
            if (!UsageAnalytics.setEnabled(value)) Toast.makeText(context, context.getString(R.string.settings_save_failed), Toast.LENGTH_SHORT).show()
        }
    )
}
