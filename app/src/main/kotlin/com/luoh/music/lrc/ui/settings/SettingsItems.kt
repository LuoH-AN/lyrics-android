package com.luoh.music.lrc.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.ui.agora.AgoraDropdownMenu
import com.luoh.music.lrc.ui.agora.AgoraDropdownMenuItem
import com.luoh.music.lrc.ui.agora.SettingsItem
import kotlin.math.roundToInt

@Composable
internal fun SettingsLeadingIcon(icon: ImageVector) {
    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
}

@Composable
internal fun SettingsNavigationItem(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { SettingsLeadingIcon(icon) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
internal fun SettingsSwitchItem(
    title: String,
    summary: String?,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { SettingsLeadingIcon(icon) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.alpha(if (enabled) 1f else 0.38f)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
internal fun SettingsChoiceItem(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    icon: ImageVector,
    enabled: Boolean = true,
    showSummary: Boolean = true,
    summary: String? = null,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: options.first().second
    SettingsItem(
        headlineContent = { Text(title) },
        supportingContent = (summary ?: label.takeIf { showSummary })?.let { { Text(it) } },
        leadingContent = { SettingsLeadingIcon(icon) },
        trailingContent = {
            Box {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                AgoraDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 16.dp,
                ) {
                    options.forEach { (value, text) ->
                        AgoraDropdownMenuItem(
                            text = { Text(text) },
                            leadingIcon = {
                                if (value == selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = { expanded = false; onSelect(value) },
                        )
                    }
                }
            }
        },
        modifier = Modifier.alpha(if (enabled) 1f else 0.38f).clickable(enabled = enabled) { expanded = true },
    )
}

/** Commit slider changes on release; dragging updates the display without flooding service intents. */
@Composable
internal fun SettingsSliderItem(
    title: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    unit: String = "%",
    icon: ImageVector,
    enabled: Boolean = true,
    onCommit: (Int) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        SettingsItem(
            headlineContent = { Text(title) },
            leadingContent = { SettingsLeadingIcon(icon) },
            trailingContent = { Text("${draft.roundToInt()}$unit", color = MaterialTheme.colorScheme.primary) },
        )
        Slider(
            value = draft.coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            enabled = enabled,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = title },
        )
    }
}
