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
    val context = LocalContext.current.applicationContext
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
    var status by rememberSaveable { mutableStateOf("填写配置后保存，密钥在本机加密存储。") }
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
                status = "正在编辑 ${profile.label}"
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { status = "无法读取配置，请重试" }
            finally { busy = false }
        }
    }
    CollapsingSettingsLazyScaffold(title = "API 配置", onBack = onBack) {
        item(key = "intro") { LibraryStatus("支持添加和切换兼容 Chat Completions 的 API 服务。") }
        item(key = "editor") {
            SettingsGroup(if (editingId == null) "添加 API 配置" else "编辑 API 配置", items = listOf({
                Column {
                    LibraryField("配置名称", name, { name = it }, !busy)
                    LibraryField("HTTPS 服务地址", endpoint, { endpoint = it }, !busy)
                    LibraryField("模型名称", model, { model = it }, !busy)
                    LibraryField("API Key", key, { key = it }, !busy, secret = true)
                    Button(enabled = !busy, modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = {
                        val label = name.trim(); val address = endpoint.trim(); val modelName = model.trim(); val secret = key.trim(); val id = editingId
                        if (label.isBlank() || !address.startsWith("https://") || modelName.isBlank()) status = "请填写名称、HTTPS 地址、模型和 API Key"
                        else {
                            busy = true
                            scope.launch {
                                try {
                                    val saved = withContext(Dispatchers.IO) {
                                        require(secret.isNotBlank() || (id != null && SecretStorage(context, id).read().isNotBlank())) { "请填写 API Key" }
                                        val profile = if (id != null) { TranslationApiProfiles.rename(context, id, label); TranslationApiProfiles.find(context, id) }
                                            else TranslationApiProfiles.add(context, label)
                                        prefs.edit().putString(TranslationApiProfiles.endpointKey(profile.id), address)
                                            .putString(TranslationApiProfiles.modelKey(profile.id), modelName)
                                            .putString("active_api_profile", profile.id).putString("mode", "api").apply()
                                        if (secret.isNotBlank()) SecretStorage(context, profile.id).save(secret)
                                        profile
                                    }
                                    editingId = saved.id; status = "已保存并启用 ${saved.label}"
                                    LyricsOverlayService.instance?.refreshSupplementTranslation()
                                } catch (error: CancellationException) { throw error }
                                catch (error: Exception) { status = error.message ?: "保存失败，请重试" }
                                finally { busy = false }
                            }
                        }
                    }) { Text(if (busy) "处理中…" else "保存并启用") }
                    if (editingId != null) LibraryAction("新建配置", !busy) { clearEditor() }
                    LibraryStatus(status)
                }
            }))
        }
        if (profiles.isEmpty()) item(key = "empty") { LibraryStatus("暂无 API 配置，请填写并保存") }
        itemsIndexed(profiles, key = { _, profile -> "profile-${profile.id}" }, contentType = { _, _ -> "profile" }) { index, profile ->
            LibraryLazyGroupItem(index, profiles.size) {
                SettingsItem(modifier = Modifier.clickable(enabled = !busy) { edit(profile) }, headlineContent = { Text(profile.label) },
                    supportingContent = { Text(if (profile.id == activeId) "使用中 · 点击编辑" else "点击编辑") },
                    trailingContent = { TextButton(enabled = !busy, onClick = {
                        confirmation = LibraryConfirmation("删除 ${profile.label}？", "删除服务地址、模型与本机密钥，此操作无法撤销。", "确认删除") {
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        TranslationApiProfiles.remove(context, profile.id)
                                        if (prefs.getString("active_api_profile", "") == profile.id)
                                            prefs.edit().putString("active_api_profile", TranslationApiProfiles.all(context).firstOrNull()?.id ?: "none").apply()
                                    }
                                    if (editingId == profile.id) clearEditor()
                                    status = "已删除 ${profile.label}"
                                    LyricsOverlayService.instance?.refreshSupplementTranslation()
                                } catch (error: CancellationException) { throw error }
                                catch (error: Exception) { status = "删除失败，请重试" }
                                finally { busy = false }
                            }
                        }
                    }) { Text("删除") } })
            }
        }
    }
    LibraryConfirmationDialog(confirmation) { confirmation = null }
}
