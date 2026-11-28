package com.luoh.music.lrc.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.R
import com.luoh.music.lrc.data.translation.SecretStorage
import com.luoh.music.lrc.data.translation.TranslationApiProfile
import com.luoh.music.lrc.data.translation.TranslationApiProfiles
import com.luoh.music.lrc.data.translation.translationPrefs
import com.luoh.music.lrc.ui.agora.CollapsingSettingsLazyScaffold
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ApiProfilesComposeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.translationPrefs() }
    val revision = preferenceRevision(prefs, setOf("custom_api_profiles", "deleted_api_profiles", "api_profile_labels", "active_api_profile"))
    val active = LocalSettingsScrollRetentionActive.current
    val scope = rememberLibraryScope()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(context.getString(R.string.api_configuration_intro)) }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    var profiles by remember { mutableStateOf(emptyList<TranslationApiProfile>()) }
    LaunchedEffect(revision, active) {
        if (active) profiles = withContext(Dispatchers.IO) { TranslationApiProfiles.all(context) }
    }
    val activeId = remember(revision) { prefs.getString("active_api_profile", "").orEmpty() }
    fun clearEditor() { editingId = null; name = ""; endpoint = ""; model = ""; key = "" }
    fun edit(profile: TranslationApiProfile) {
        busy = true
        scope.launch {
            try {
                val values = withContext(Dispatchers.IO) { Triple(
                    prefs.getString(TranslationApiProfiles.endpointKey(profile.id), profile.defaultEndpoint).orEmpty(),
                    prefs.getString(TranslationApiProfiles.modelKey(profile.id), profile.defaultModel).orEmpty(), SecretStorage(context, profile.id).read()) }
                editingId = profile.id; name = profile.label; endpoint = values.first; model = values.second; key = values.third
                status = context.getString(R.string.editing, profile.label)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { status = context.getString(R.string.configuration_load_failed) }
            finally { busy = false }
        }
    }
    CollapsingSettingsLazyScaffold(title = context.getString(R.string.api_configurations), onBack = onBack) {
        item(key = "intro") { LibraryStatus(context.getString(R.string.api_profiles_intro)) }
        item(key = "editor") {
            SettingsGroup(if (editingId == null) context.getString(R.string.add_api_configuration) else context.getString(R.string.edit_api_configuration), items = listOf({
                Column {
                    LibraryField(context.getString(R.string.configuration_name), name, { name = it }, !busy)
                    LibraryField(context.getString(R.string.https_endpoint), endpoint, { endpoint = it }, !busy)
                    LibraryField(context.getString(R.string.model_name), model, { model = it }, !busy)
                    LibraryField(context.getString(R.string.api_key), key, { key = it }, !busy, secret = true)
                    Button(enabled = !busy, modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = {
                        val label = name.trim(); val address = endpoint.trim(); val modelName = model.trim(); val secret = key.trim(); val id = editingId
                        if (label.isBlank() || !address.startsWith("https://") || modelName.isBlank()) status = context.getString(R.string.api_profile_required_fields)
                        else {
                            busy = true
                            scope.launch {
                                try {
                                    val saved = withContext(Dispatchers.IO) {
                                        require(secret.isNotBlank() || (id != null && SecretStorage(context, id).read().isNotBlank())) { context.getString(R.string.enter_an_api_key) }
                                        val profile = if (id != null) { TranslationApiProfiles.rename(context, id, label); TranslationApiProfiles.find(context, id) }
                                            else TranslationApiProfiles.add(context, label)
                                        prefs.edit().putString(TranslationApiProfiles.endpointKey(profile.id), address)
                                            .putString(TranslationApiProfiles.modelKey(profile.id), modelName)
                                            .putString("active_api_profile", profile.id).putString("mode", "api").apply()
                                        if (secret.isNotBlank()) SecretStorage(context, profile.id).save(secret)
                                        profile
                                    }
                                    editingId = saved.id; status = context.getString(R.string.saved_and_enabled, saved.label)
                                    LyricsOverlayService.instance?.refreshSupplementTranslation()
                                } catch (error: CancellationException) { throw error }
                                catch (error: Exception) { status = error.message ?: context.getString(R.string.save_failed) }
                                finally { busy = false }
                            }
                        }
                    }) { Text(if (busy) context.getString(R.string.working) else context.getString(R.string.save_and_enable)) }
                    if (editingId != null) LibraryAction(context.getString(R.string.new_configuration), !busy) { clearEditor() }
                    LibraryStatus(status)
                }
            }))
        }
        if (profiles.isEmpty()) item(key = "empty") { LibraryStatus(context.getString(R.string.api_profiles_empty)) }
        itemsIndexed(profiles, key = { _, profile -> "profile-${profile.id}" }, contentType = { _, _ -> "profile" }) { index, profile ->
            LibraryLazyGroupItem(index, profiles.size) {
                SettingsItem(modifier = Modifier.clickable(enabled = !busy) { edit(profile) }, headlineContent = { Text(profile.label) },
                    supportingContent = { Text(if (profile.id == activeId) context.getString(R.string.active_tap_to_edit) else context.getString(R.string.tap_to_edit)) },
                    trailingContent = { TextButton(enabled = !busy, onClick = {
                        confirmation = LibraryConfirmation(context.getString(R.string.delete_named_profile, profile.label), context.getString(R.string.delete_api_profile_message), context.getString(R.string.confirm_deletion)) {
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        TranslationApiProfiles.remove(context, profile.id)
                                        if (prefs.getString("active_api_profile", "") == profile.id)
                                            prefs.edit().putString("active_api_profile", TranslationApiProfiles.all(context).firstOrNull()?.id ?: "none").apply()
                                    }
                                    if (editingId == profile.id) clearEditor()
                                    status = context.getString(R.string.deleted, profile.label)
                                    LyricsOverlayService.instance?.refreshSupplementTranslation()
                                } catch (error: CancellationException) { throw error }
                                catch (error: Exception) { status = context.getString(R.string.delete_failed) }
                                finally { busy = false }
                            }
                        }
                    }) { Text(context.getString(R.string.delete)) } })
            }
        }
    }
    LibraryConfirmationDialog(confirmation) { confirmation = null }
}
