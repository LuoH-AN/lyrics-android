package com.luoh.music.lrc.ui.library

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.luoh.music.lrc.LyricsOverlayService
import com.luoh.music.lrc.R
import com.luoh.music.lrc.data.translation.SecretStorage
import com.luoh.music.lrc.data.translation.SupplementTranslation
import com.luoh.music.lrc.data.translation.TranslationApiProfile
import com.luoh.music.lrc.data.translation.TranslationApiProfiles
import com.luoh.music.lrc.data.translation.translationPrefs
import com.luoh.music.lrc.ui.agora.CollapsingSettingsLazyScaffold
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.navigation.ComposeRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale

private val translationModes = listOf("off" to R.string.off, "offline" to R.string.offline_translation, "api" to R.string.custom_api)

@Composable
private fun TranslationModeDialog(mode: String, dismiss: () -> Unit, select: (String) -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = dismiss, title = { Text(context.getString(R.string.choose_translation_method)) }, text = {
        Column {
            translationModes.forEach { (value, label) ->
                SettingsItem(
                    modifier = Modifier.testTag("translation-mode-$value")
                        .selectable(selected = mode == value, role = Role.RadioButton, onClick = { select(value) }),
                    headlineContent = { Text(context.getString(label)) },
                    leadingContent = { RadioButton(selected = mode == value, onClick = null) },
                )
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text(context.getString(R.string.cancel)) } })
}

/** One-time migration keeps older API credentials usable without retaining their old UI. */
private fun ensureTranslationProfile(context: Context): String {
    val prefs = context.translationPrefs()
    if (!prefs.contains("active_api_profile")) {
        val address = prefs.getString("endpoint", "").orEmpty()
        val model = prefs.getString("model", "").orEmpty()
        val secret = SecretStorage(context, "custom").read()
        val id = if (address.isNotBlank() || model.isNotBlank() || secret.isNotBlank()) {
            val profile = TranslationApiProfiles.add(context, context.getString(R.string.previous_custom_service))
            prefs.edit().putString(TranslationApiProfiles.endpointKey(profile.id), address)
                .putString(TranslationApiProfiles.modelKey(profile.id), model).apply()
            if (secret.isNotBlank()) SecretStorage(context, profile.id).save(secret)
            profile.id
        } else "glm"
        prefs.edit().putString("active_api_profile", id).apply()
    }
    return TranslationApiProfiles.find(context, prefs.getString("active_api_profile", null)).id
}

@Composable
internal fun TranslationComposeScreen(onNavigate: (ComposeRoute) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.translationPrefs() }
    val revision = preferenceRevision(prefs, setOf("mode", "last_status", "last_status_locale", "last_track"))
    val profileRevision = preferenceRevision(prefs, setOf("custom_api_profiles", "deleted_api_profiles", "api_profile_labels", "active_api_profile"))
    val scope = rememberLibraryScope()
    val active = LocalSettingsScrollRetentionActive.current
    var profileId by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable(profileId) { mutableStateOf("") }
    var model by rememberSaveable(profileId) { mutableStateOf("") }
    // Encrypted credentials are read on IO, never stored in the activity's saved-state bundle.
    var apiKey by remember(profileId) { mutableStateOf("") }
    var profileLoaded by remember(profileId) { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var verifyJob by remember { mutableStateOf<Job?>(null) }
    var profileMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    var downloadCode by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var downloaded by remember { mutableStateOf(emptySet<String>()) }
    var downloading by remember { mutableStateOf(emptyMap<String, Long>()) }
    var elapsed by remember { mutableStateOf(0L) }
    var modelsBusy by remember { mutableStateOf(false) }
    var modelRefresh by remember { mutableIntStateOf(0) }
    val manager by remember { lazy { RemoteModelManager.getInstance() } }
    val mode = remember(revision) { prefs.getString("mode", "off").orEmpty() }
    var profiles by remember { mutableStateOf(emptyList<TranslationApiProfile>()) }
    var profileMigrationReady by remember { mutableStateOf(false) }
    val profile = profiles.find { it.id == profileId } ?: TranslationApiProfile("none", context.getString(R.string.loading_api_configurations), "", "")
    val locale = context.resources.configuration.locales[0]
    val lastStatus = remember(revision, locale) {
        val statusLocale = prefs.getString("last_status_locale", null)
        if (statusLocale != null && statusLocale != locale.toLanguageTag() ||
            statusLocale == null && locale.language != "zh") return@remember ""
        val value = prefs.getString("last_status", "").orEmpty()
        val track = prefs.getString("last_track", "").orEmpty()
        if (value.isBlank() || track.isBlank()) value else context.getString(R.string.song_translation_status, track, value)
    }
    var languages by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    val filteredLanguages = remember(languages, search) {
        if (search.isBlank()) languages.take(4) else languages.filter { it.second.contains(search.trim(), true) }
    }
    fun languageTitle(code: String) = languages.firstOrNull { it.first == code }?.second ?: code
    fun needed(code: String) = setOf(code, "zh").filter { it != "en" }
    fun saveDraft(id: String = profileId, address: String = endpoint, modelName: String = model, secret: String = apiKey) {
        prefs.edit().putString(TranslationApiProfiles.endpointKey(id), address.trim())
            .putString(TranslationApiProfiles.modelKey(id), modelName.trim()).apply()
        if (secret.isNotBlank()) SecretStorage(context, id).save(secret.trim())
    }
    fun applyMode(value: String) {
        if (value != "api") { verifyJob?.cancel(); verifying = false }
        prefs.edit().putString("mode", value).putString("language", "auto").apply()
        LyricsOverlayService.instance?.refreshSupplementTranslation()
        status = when (value) {
            "off" -> context.getString(R.string.supplemental_translation_is_off)
            "offline" -> context.getString(R.string.offline_translation_enabled)
            else -> context.getString(R.string.api_translation_enabled)
        }
    }
    fun switchProfile(selected: TranslationApiProfile) {
        if (selected.id == profileId) return
        verifyJob?.cancel(); verifying = false
        val previousId = profileId; val previousAddress = endpoint; val previousModel = model; val previousKey = apiKey
        scope.launch {
            try {
                withContext(Dispatchers.IO) { saveDraft(previousId, previousAddress, previousModel, previousKey) }
                profileId = selected.id
                prefs.edit().putString("active_api_profile", selected.id).putString("mode", "api").apply()
                LyricsOverlayService.instance?.refreshSupplementTranslation()
                status = context.getString(R.string.api_profile_switched, selected.label)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { status = context.getString(R.string.configuration_save_failed) }
        }
    }
    fun downloadLanguage(code: String, wifi: Boolean) {
        downloading = downloading + (code to System.currentTimeMillis())
        status = context.getString(R.string.connecting_language_pack_service)
        scope.launch {
            try {
                val conditions = DownloadConditions.Builder().apply { if (wifi) requireWifi() }.build()
                withTimeout(120_000) {
                    withContext(Dispatchers.IO) {
                        Tasks.whenAll(needed(code).map { manager.download(TranslateRemoteModel.Builder(it).build(), conditions) }).await()
                    }
                }
                status = context.getString(R.string.language_packs_ready)
                LyricsOverlayService.instance?.refreshSupplementTranslation()
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                status = context.getString(R.string.language_pack_download_timeout)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { status = context.getString(R.string.language_pack_download_failed)
            } finally { downloading = downloading - code; modelRefresh++ }
        }
    }
    LaunchedEffect(active) {
        if (!active || profileMigrationReady) return@LaunchedEffect
        try { withContext(Dispatchers.IO) { ensureTranslationProfile(context) } }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { status = context.getString(R.string.old_api_settings_read_failed) }
        profileMigrationReady = true
    }
    LaunchedEffect(profileRevision, active, profileMigrationReady) {
        if (!active || !profileMigrationReady) return@LaunchedEffect
        val values = withContext(Dispatchers.IO) {
            val all = TranslationApiProfiles.all(context)
            val selected = prefs.getString("active_api_profile", null)
            all to (all.firstOrNull { it.id == selected }?.id ?: all.firstOrNull()?.id ?: "none")
        }
        profiles = values.first
        if (profileId != values.second) {
            verifyJob?.cancel(); verifying = false; profileId = values.second
        }
    }
    LaunchedEffect(profileId, mode, active) {
        if (!active || mode != "api" || profileId.isBlank() || profileLoaded) return@LaunchedEffect
        val id = profileId
        val values = withContext(Dispatchers.IO) {
            val resolved = TranslationApiProfiles.find(context, id)
            Triple(prefs.getString(TranslationApiProfiles.endpointKey(id), resolved.defaultEndpoint).orEmpty(),
                prefs.getString(TranslationApiProfiles.modelKey(id), resolved.defaultModel).orEmpty(), SecretStorage(context, id).read())
        }
        if (endpoint.isEmpty()) endpoint = values.first
        if (model.isEmpty()) model = values.second
        apiKey = values.third; profileLoaded = true
    }
    LaunchedEffect(mode, active, locale) {
        if (!active || mode != "offline") return@LaunchedEffect
        languages = withContext(Dispatchers.IO) {
            val common = listOf("en", "ja", "ko", "zh")
            val titles = TranslateLanguage.getAllLanguages().associateWith {
                Locale.forLanguageTag(it).getDisplayLanguage(locale) + " · $it"
            }
            (common + titles.keys.filter { it !in common }.sortedBy { titles[it] })
                .map { it to (titles[it] ?: it) }
        }
    }
    LaunchedEffect(modelRefresh, mode, active) {
        if (!active || mode != "offline") return@LaunchedEffect
        modelsBusy = true
        try {
            downloaded = withContext(Dispatchers.IO) {
                manager.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { status = context.getString(R.string.language_pack_status_unavailable) }
        finally { modelsBusy = false }
    }
    LaunchedEffect(downloading, active) {
        if (!active) return@LaunchedEffect
        while (downloading.isNotEmpty()) {
            elapsed = (System.currentTimeMillis() - (downloading.values.minOrNull() ?: System.currentTimeMillis())) / 1000
            delay(1000)
        }
    }
    CollapsingSettingsLazyScaffold(title = context.getString(R.string.supplemental_translation), onBack = onBack) {
        if (lastStatus.isNotBlank()) item(key = "last-status") { LibraryStatus(lastStatus) }
        item(key = "mode") {
            SettingsGroup(context.getString(R.string.translation_method), items = listOf({
                SettingsItem(
                    modifier = Modifier.clickable { modeMenu = true },
                    headlineContent = { Text(context.getString(R.string.supplemental_translation_method)) },
                    supportingContent = { Text(context.getString(translationModes.firstOrNull { it.first == mode }?.second ?: R.string.off)) },
                )
            }))
        }
        if (mode == "api") {
            item(key = "api-title") { LibraryGroupTitle(context.getString(R.string.translation_api)) }
            item(key = "api-profile", contentType = "setting") {
                LibraryLazyGroupItem(0, 6) {
                    SettingsItem(modifier = Modifier.clickable(enabled = profileLoaded) { profileMenu = true },
                        headlineContent = { Text(profile.label) }, supportingContent = { Text(context.getString(R.string.choose_or_manage_translation_api)) })
                }
            }
            item(key = "api-endpoint", contentType = "field") { LibraryLazyGroupItem(1, 6) {
                LibraryField(context.getString(R.string.https_endpoint), endpoint, { endpoint = it }, profileLoaded && !verifying)
            } }
            item(key = "api-model", contentType = "field") { LibraryLazyGroupItem(2, 6) {
                LibraryField(context.getString(R.string.model_name), model, { model = it }, profileLoaded && !verifying)
            } }
            item(key = "api-key", contentType = "field") { LibraryLazyGroupItem(3, 6) {
                LibraryField(context.getString(R.string.api_key), apiKey, { apiKey = it }, profileLoaded && !verifying, secret = true)
            } }
            item(key = "api-delete", contentType = "action") { LibraryLazyGroupItem(4, 6) {
                LibraryAction(context.getString(R.string.delete_key), profileLoaded && !verifying) {
                    val id = profileId
                    confirmation = LibraryConfirmation(context.getString(R.string.delete_the_saved_key), context.getString(R.string.delete_api_key_message), context.getString(R.string.delete_key)) {
                        scope.launch {
                            withContext(Dispatchers.IO) { SecretStorage(context, id).save("") }
                            if (profileId == id) { apiKey = ""; status = context.getString(R.string.api_key_deleted) }
                        }
                    }
                }
            } }
            item(key = "api-confirm", contentType = "action") { LibraryLazyGroupItem(5, 6) {
                    Button(enabled = profileLoaded && !verifying, modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = {
                        val id = profileId; val address = endpoint.trim(); val modelName = model.trim(); val secret = apiKey.trim()
                        if (!address.startsWith("https://") || modelName.isBlank() || secret.isBlank()) {
                            status = context.getString(R.string.api_translation_required_fields)
                        } else {
                            verifying = true
                            verifyJob = scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { saveDraft(id, address, modelName, secret) }
                                    prefs.edit().putString("mode", "api").putString("active_api_profile", id).putString("language", "auto").apply()
                                    LyricsOverlayService.instance?.refreshSupplementTranslation()
                                    status = context.getString(R.string.verifying_translation_service)
                                    val translated = withContext(Dispatchers.IO) { SupplementTranslation(context).testApi(address, modelName, secret) }
                                    if (profileId == id) { status = context.getString(R.string.api_verification_success, translated); LyricsOverlayService.instance?.refreshSupplementTranslation() }
                                } catch (error: CancellationException) { throw error
                                } catch (error: Exception) { if (profileId == id) status = context.getString(R.string.api_verification_failed, error.message.orEmpty().ifBlank { context.getString(R.string.unknown_error) }.take(180))
                                } finally { if (profileId == id) verifying = false }
                            }
                        }
                    }) { Text(if (verifying) context.getString(R.string.verifying) else context.getString(R.string.save_and_verify)) }
            } }
        }
        if (mode == "offline") {
            item(key = "offline-tools") {
                SettingsGroup(context.getString(R.string.offline_language_packs), items = listOf({
                    Column {
                        if (modelsBusy || downloading.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        if (downloading.isNotEmpty()) LibraryStatus(context.getString(R.string.language_pack_download_progress, elapsed))
                        LibraryField(context.getString(R.string.search_languages), search, { search = it })
                        Row(Modifier.fillMaxWidth()) {
                            LibraryAction(context.getString(R.string.refresh_status)) { modelRefresh++ }
                            LibraryAction(context.getString(R.string.clear_translation_cache)) {
                                confirmation = LibraryConfirmation(context.getString(R.string.clear_translation_cache_title), context.getString(R.string.clear_translation_cache_message), context.getString(R.string.clear_cache)) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { File(context.cacheDir, "translations").listFiles()?.filter { it.isFile }?.forEach { it.delete() } }
                                        status = context.getString(R.string.supplemental_translation_cache_cleared)
                                    }
                                }
                            }
                        }
                    }
                }))
            }
            itemsIndexed(filteredLanguages, key = { _, language -> "language-${language.first}" }, contentType = { _, _ -> "language" }) { index, language ->
                val (code, title) = language
                val ready = downloaded.containsAll(needed(code))
                val hasModel = code in downloaded || (code == "en" && "zh" in downloaded)
                val inProgress = code in downloading
                LibraryLazyGroupItem(index, filteredLanguages.size) {
                    SettingsItem(headlineContent = { Text(title) },
                        supportingContent = { Text(when { inProgress -> context.getString(R.string.downloading); ready -> context.getString(R.string.downloaded); hasModel -> context.getString(R.string.chinese_model_required); else -> context.getString(R.string.not_downloaded) }) },
                        trailingContent = {
                            TextButton(enabled = !inProgress, onClick = {
                                if (hasModel) {
                                    val removable = if (code == "en") "zh" else code
                                    confirmation = LibraryConfirmation(context.getString(R.string.delete_language_pack), if (removable == "zh")
                                        context.getString(R.string.delete_chinese_model_message)
                                        else context.getString(R.string.delete_language_pack_message, languageTitle(code)), context.getString(R.string.confirm_deletion)) {
                                        scope.launch {
                                            try { withContext(Dispatchers.IO) { manager.deleteDownloadedModel(TranslateRemoteModel.Builder(removable).build()).await() }; status = context.getString(R.string.language_pack_deleted); modelRefresh++ }
                                            catch (error: CancellationException) { throw error }
                                            catch (error: Exception) { status = context.getString(R.string.language_pack_delete_failed) }
                                        }
                                    }
                                } else downloadCode = code
                            }) { Text(if (hasModel) context.getString(R.string.delete) else context.getString(R.string.download)) }
                        })
                }
            }
        }
        if (status.isNotBlank()) item(key = "status") { LibraryStatus(status) }
    }
    if (modeMenu && active) TranslationModeDialog(mode, { modeMenu = false }) { selected ->
        modeMenu = false
        applyMode(selected)
    }
    if (profileMenu && active) AlertDialog(onDismissRequest = { profileMenu = false }, title = { Text(context.getString(R.string.choose_a_translation_api)) },
        text = {
            Column {
                profiles.forEach { item ->
                    Row(Modifier.fillMaxWidth().clickable { profileMenu = false; switchProfile(item) }.padding(vertical = 4.dp)) {
                        RadioButton(selected = item.id == profileId, onClick = { profileMenu = false; switchProfile(item) })
                        Text(item.label, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = {
        profileMenu = false
        val id = profileId; val address = endpoint; val modelName = model; val secret = apiKey
        scope.launch {
            try { withContext(Dispatchers.IO) { saveDraft(id, address, modelName, secret) }; onNavigate(ComposeRoute("profiles")) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { status = context.getString(R.string.configuration_save_failed) }
        }
        }) { Text(context.getString(R.string.manage_apis)) } }, dismissButton = { TextButton(onClick = { profileMenu = false }) { Text(context.getString(R.string.cancel)) } })
    val code = downloadCode
    if (code != null && active) {
        AlertDialog(
            onDismissRequest = { downloadCode = null },
            title = { Text(context.getString(R.string.download_language_pack_title, languageTitle(code))) },
            text = { Text(context.getString(R.string.download_language_pack_message)) },
            confirmButton = {
                TextButton(onClick = {
                    downloadCode = null
                    downloadLanguage(code, wifi = true)
                }) { Text(context.getString(R.string.download_over_wi_fi_only)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        downloadCode = null
                        downloadLanguage(code, wifi = false)
                    }) { Text(context.getString(R.string.use_current_network)) }
                    TextButton(onClick = { downloadCode = null }) { Text(context.getString(R.string.cancel)) }
                }
            },
        )
    } else LibraryConfirmationDialog(confirmation) { confirmation = null }
}
