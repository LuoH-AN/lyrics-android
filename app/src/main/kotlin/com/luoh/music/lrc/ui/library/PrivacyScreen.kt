package com.luoh.music.lrc.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.R
import com.luoh.music.lrc.ui.agora.CollapsingSettingsScaffold
import com.luoh.music.lrc.ui.agora.SettingsGroup

@Composable
internal fun PrivacyComposeScreen(onBack: () -> Unit) {
    CollapsingSettingsScaffold(title = "隐私说明", onBack = onBack) {
        LibraryStatus(stringResource(R.string.privacy_summary))
        PrivacySection("权限与播放", R.string.privacy_permissions)
        PrivacySection("歌词与联网", R.string.privacy_lyrics)
        PrivacySection("补充翻译", R.string.privacy_translation)
        PrivacySection("使用情况统计", R.string.privacy_analytics)
    }
}

@Composable
private fun PrivacySection(title: String, @StringRes body: Int) {
    SettingsGroup(title = title, items = listOf({
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }))
}
