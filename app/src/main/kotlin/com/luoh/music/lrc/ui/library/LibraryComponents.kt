package com.luoh.music.lrc.ui.library

import android.content.SharedPreferences
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren

internal data class LibraryConfirmation(val title: String, val body: String, val action: String, val accept: () -> Unit)

@Composable
internal fun LibraryConfirmationDialog(value: LibraryConfirmation?, dismiss: () -> Unit) {
    if (value != null && LocalSettingsScrollRetentionActive.current) AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(value.title) },
        text = { Text(value.body) },
        confirmButton = { TextButton(onClick = { dismiss(); value.accept() }) { Text(value.action) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } }
    )
}

@Composable
internal fun preferenceRevision(prefs: SharedPreferences, watchedKeys: Set<String>? = null): Int {
    var revision by remember(prefs, watchedKeys) { mutableIntStateOf(0) }
    val active = LocalSettingsScrollRetentionActive.current
    DisposableEffect(prefs, watchedKeys, active) {
        if (active) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || watchedKeys == null || key in watchedKeys) revision++
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            // A returning slot may have missed preference changes while it was outgoing.
            revision++
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        } else {
            onDispose { }
        }
    }
    return revision
}

/** Outgoing Agora slots remain visible briefly but stop owning business jobs immediately. */
@Composable
internal fun rememberLibraryScope(): CoroutineScope {
    val scope = rememberCoroutineScope()
    val active = LocalSettingsScrollRetentionActive.current
    DisposableEffect(scope, active) {
        if (!active) scope.coroutineContext.cancelChildren()
        onDispose { }
    }
    return scope
}

@Composable
internal fun LibraryStatus(message: String) {
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
internal fun LibraryField(label: String, value: String, onValueChange: (String) -> Unit,
    enabled: Boolean = true, secret: Boolean = false) {
    var visible by remember(label) { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) },
        enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
        trailingIcon = if (secret) ({ TextButton(onClick = { visible = !visible }) { Text(if (visible) "隐藏" else "显示") } }) else null)
}

@Composable
internal fun LibraryAction(label: String, enabled: Boolean = true, action: () -> Unit) {
    TextButton(onClick = action, enabled = enabled, modifier = Modifier.padding(horizontal = 8.dp)) { Text(label) }
}

/** The exact SettingsGroup row surfaces, split into lazy items for dynamic collections. */
@Composable
internal fun LibraryLazyGroupItem(index: Int, count: Int, content: @Composable () -> Unit) {
    val shape = when {
        count == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(5.dp)
    }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = if (index == count - 1) 24.dp else 2.dp), content = content)
}

@Composable
internal fun LibraryGroupTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}
