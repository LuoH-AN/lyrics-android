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
    CollapsingSettingsScaffold(title = stringResource(R.string.privacy), onBack = onBack) {
        LibraryStatus(stringResource(R.string.privacy_summary))
        PrivacySection(stringResource(R.string.permissions_and_playback), R.string.privacy_permissions)
        PrivacySection(stringResource(R.string.lyrics_and_network_access), R.string.privacy_lyrics)
        PrivacySection(stringResource(R.string.supplemental_translation), R.string.privacy_translation)
        PrivacySection(stringResource(R.string.usage_analytics), R.string.privacy_analytics)
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
