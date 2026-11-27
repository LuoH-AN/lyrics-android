package com.luoh.music.lrc.ui.library

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.R
import com.luoh.music.lrc.ui.agora.CollapsingSettingsScaffold
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.settings.UsageAnalyticsSettings

@Composable
internal fun PrivacyComposeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = remember(context) { context.getString(R.string.privacy_summary).split("\n\n") }
    CollapsingSettingsScaffold(title = "隐私说明", onBack = onBack) {
        SettingsGroup(title = "使用情况统计", items = listOf({ UsageAnalyticsSettings() }))
        LibraryStatus("权限与数据处理说明")
        paragraphs.forEachIndexed { index, paragraph -> LibraryLazyGroupItem(index, paragraphs.size) {
            Text(paragraph, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp))
        } }
    }
}
