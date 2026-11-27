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

private val translationModes = listOf("off" to "关闭", "offline" to "离线翻译", "api" to "自定义 API")

@Composable
private fun TranslationModeDialog(mode: String, dismiss: () -> Unit, select: (String) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("选择补充翻译方式") }, text = {
        Column {
            translationModes.forEach { (value, label) ->
                SettingsItem(
                    modifier = Modifier.testTag("translation-mode-$value")
                        .selectable(selected = mode == value, role = Role.RadioButton, onClick = { select(value) }),
                    headlineContent = { Text(label) },
                    leadingContent = { RadioButton(selected = mode == value, onClick = null) },
                )
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

/** One-time migration keeps older API credentials usable without retaining their old UI. */
private fun ensureTranslationProfile(context: Context): String {
    val prefs = context.translationPrefs()
    if (!prefs.contains("active_api_profile")) {
        val address = prefs.getString("endpoint", "").orEmpty()
        val model = prefs.getString("model", "").orEmpty()
        val secret = SecretStorage(context, "custom").read()
        val id = if (address.isNotBlank() || model.isNotBlank() || secret.isNotBlank()) {
            val profile = TranslationApiProfiles.add(context, "原自定义服务")
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
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.translationPrefs() }
    val revision = preferenceRevision(prefs, setOf("mode", "last_status", "last_track"))
    val profileRevision = preferenceRevision(prefs, setOf("custom_api_profiles", "deleted_api_profiles", "api_profile_labels", "active_api_profile"))
    val scope = rememberLibraryScope()
    val active = LocalSettingsScrollRetentionActive.current
    var profileId by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable(profileId) { mutableStateOf("") }
    var model by rememberSaveable(profileId) { mutableStateOf("") }
    // Encrypted credentials are read on IO, never stored in the activity's saved-state bundle.
    var apiKey by remember(profileId) { mutableStateOf("") }
    var profileLoaded by remember(profileId) { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var verifyJob by remember { mutableStateOf<Job?>(null) }
    var profileMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
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
    val profile = profiles.find { it.id == profileId } ?: TranslationApiProfile("none", "正在读取 API 配置…", "", "")
    val lastStatus = remember(revision) {
        val value = prefs.getString("last_status", "").orEmpty()
        val track = prefs.getString("last_track", "").orEmpty()
        if (value.isBlank() || track.isBlank()) value else "《$track》 · $value"
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
            "off" -> "补充翻译已关闭"
            "offline" -> "已启用离线翻译，请下载所需语言包"
            else -> "已启用 API 翻译，修改配置后请保存并验证"
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
                status = "已切换至 ${selected.label}，首次使用请配置密钥并验证"
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { status = "配置保存失败，请重试" }
        }
    }
    fun downloadLanguage(code: String, wifi: Boolean) {
        downloading = downloading + (code to System.currentTimeMillis())
        status = "正在连接语言包服务…"
        scope.launch {
            try {
                val conditions = DownloadConditions.Builder().apply { if (wifi) requireWifi() }.build()
                withTimeout(120_000) {
                    withContext(Dispatchers.IO) {
                        Tasks.whenAll(needed(code).map { manager.download(TranslateRemoteModel.Builder(it).build(), conditions) }).await()
                    }
                }
                status = "语言包已就绪，可使用离线翻译"
                LyricsOverlayService.instance?.refreshSupplementTranslation()
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                status = "下载等待超时，请检查网络并刷新状态；仍未完成时重试"
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { status = "下载未完成，请确认网络可访问 Google ML Kit"
            } finally { downloading = downloading - code; modelRefresh++ }
        }
    }
    LaunchedEffect(active) {
        if (!active || profileMigrationReady) return@LaunchedEffect
        try { withContext(Dispatchers.IO) { ensureTranslationProfile(context) } }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { status = "旧配置读取失败，请重新填写 API 配置" }
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
    LaunchedEffect(mode, active) {
        if (!active || mode != "offline" || languages.isNotEmpty()) return@LaunchedEffect
        languages = withContext(Dispatchers.IO) {
            val common = listOf("en", "ja", "ko", "zh")
            val titles = TranslateLanguage.getAllLanguages().associateWith {
                Locale.forLanguageTag(it).getDisplayLanguage(Locale.SIMPLIFIED_CHINESE) + " · $it"
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
        catch (error: Exception) { status = "暂时无法读取语言包状态" }
        finally { modelsBusy = false }
    }
    LaunchedEffect(downloading, active) {
        if (!active) return@LaunchedEffect
        while (downloading.isNotEmpty()) {
            elapsed = (System.currentTimeMillis() - (downloading.values.minOrNull() ?: System.currentTimeMillis())) / 1000
            delay(1000)
        }
    }
    CollapsingSettingsLazyScaffold(title = "补充翻译", onBack = onBack) {
        if (lastStatus.isNotBlank()) item(key = "last-status") { LibraryStatus(lastStatus) }
        item(key = "mode") {
            SettingsGroup("翻译方式", items = listOf({
                SettingsItem(
                    modifier = Modifier.clickable { modeMenu = true },
                    headlineContent = { Text("补充翻译方式") },
                    supportingContent = { Text(translationModes.firstOrNull { it.first == mode }?.second ?: "关闭") },
                )
            }))
        }
        if (mode == "api") {
            item(key = "api-title") { LibraryGroupTitle("翻译 API") }
            item(key = "api-profile", contentType = "setting") {
                LibraryLazyGroupItem(0, 6) {
                    SettingsItem(modifier = Modifier.clickable(enabled = profileLoaded) { profileMenu = true },
                        headlineContent = { Text(profile.label) }, supportingContent = { Text("选择翻译服务或管理 API 配置") })
                }
            }
            item(key = "api-endpoint", contentType = "field") { LibraryLazyGroupItem(1, 6) {
                LibraryField("HTTPS 服务地址", endpoint, { endpoint = it }, profileLoaded && !verifying)
            } }
            item(key = "api-model", contentType = "field") { LibraryLazyGroupItem(2, 6) {
                LibraryField("模型名称", model, { model = it }, profileLoaded && !verifying)
            } }
            item(key = "api-key", contentType = "field") { LibraryLazyGroupItem(3, 6) {
                LibraryField("API Key", apiKey, { apiKey = it }, profileLoaded && !verifying, secret = true)
            } }
            item(key = "api-delete", contentType = "action") { LibraryLazyGroupItem(4, 6) {
                LibraryAction("删除密钥", profileLoaded && !verifying) {
                    val id = profileId
                    confirmation = LibraryConfirmation("删除已保存的密钥？", "删除当前配置的本机密钥，保留服务地址与模型。", "删除密钥") {
                        scope.launch {
                            withContext(Dispatchers.IO) { SecretStorage(context, id).save("") }
                            if (profileId == id) { apiKey = ""; status = "当前服务的密钥已删除" }
                        }
                    }
                }
            } }
            item(key = "api-confirm", contentType = "action") { LibraryLazyGroupItem(5, 6) {
                    Button(enabled = profileLoaded && !verifying, modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = {
                        val id = profileId; val address = endpoint.trim(); val modelName = model.trim(); val secret = apiKey.trim()
                        if (!address.startsWith("https://") || modelName.isBlank() || secret.isBlank()) {
                            status = "请填写 HTTPS 服务地址、模型与 API Key"
                        } else {
                            verifying = true
                            verifyJob = scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { saveDraft(id, address, modelName, secret) }
                                    prefs.edit().putString("mode", "api").putString("active_api_profile", id).putString("language", "auto").apply()
                                    LyricsOverlayService.instance?.refreshSupplementTranslation()
                                    status = "配置已保存，正在连接翻译服务…"
                                    val translated = withContext(Dispatchers.IO) { SupplementTranslation(context).testApi(address, modelName, secret) }
                                    if (profileId == id) { status = "连接成功，测试译文：$translated"; LyricsOverlayService.instance?.refreshSupplementTranslation() }
                                } catch (error: CancellationException) { throw error
                                } catch (error: Exception) { if (profileId == id) status = "API 验证失败：${error.message.orEmpty().ifBlank { "未知错误" }.take(180)}"
                                } finally { if (profileId == id) verifying = false }
                            }
                        }
                    }) { Text(if (verifying) "验证中…" else "保存并验证") }
            } }
        }
        if (mode == "offline") {
            item(key = "offline-tools") {
                SettingsGroup("离线语言包", items = listOf({
                    Column {
                        if (modelsBusy || downloading.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        if (downloading.isNotEmpty()) LibraryStatus("正在下载语言包 · 已用时 ${elapsed} 秒")
                        LibraryField("搜索语言", search, { search = it })
                        Row(Modifier.fillMaxWidth()) {
                            LibraryAction("刷新状态") { modelRefresh++ }
                            LibraryAction("清理译文缓存") {
                                confirmation = LibraryConfirmation("清理译文缓存？", "删除已缓存的补充译文，保留 API 配置与下载的语言包。", "清理缓存") {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { File(context.cacheDir, "translations").listFiles()?.filter { it.isFile }?.forEach { it.delete() } }
                                        status = "补充翻译缓存已清理"
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
                        supportingContent = { Text(when { inProgress -> "下载中…"; ready -> "已下载"; hasModel -> "缺少中文模型"; else -> "未下载" }) },
                        trailingContent = {
                            TextButton(enabled = !inProgress, onClick = {
                                if (hasModel) {
                                    val removable = if (code == "en") "zh" else code
                                    confirmation = LibraryConfirmation("删除语言包", if (removable == "zh")
                                        "删除共用中文模型后，使用离线中文翻译需重新下载。已缓存译文保留。"
                                        else "删除 ${languageTitle(code)} 语言包，保留已缓存译文。", "确认删除") {
                                        scope.launch {
                                            try { withContext(Dispatchers.IO) { manager.deleteDownloadedModel(TranslateRemoteModel.Builder(removable).build()).await() }; status = "语言包已删除"; modelRefresh++ }
                                            catch (error: CancellationException) { throw error }
                                            catch (error: Exception) { status = "删除失败，请停止离线翻译后重试" }
                                        }
                                    }
                                } else confirmation = LibraryConfirmation("下载 ${languageTitle(code)}", "下载该语言及缺失的共用中文模型，每个约 30 MB。网络需能访问 Google ML Kit。", "仅 Wi-Fi 下载") { downloadLanguage(code, true) }
                            }) { Text(if (hasModel) "删除" else "下载") }
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
    if (profileMenu && active) AlertDialog(onDismissRequest = { profileMenu = false }, title = { Text("选择翻译 API") },
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
            catch (error: Exception) { status = "配置保存失败，请重试" }
        }
        }) { Text("管理 API") } }, dismissButton = { TextButton(onClick = { profileMenu = false }) { Text("取消") } })
    // Preserve the old choice to use mobile data as well as Wi-Fi.
    val downloadDialog = confirmation?.takeIf { it.title.startsWith("下载 ") }
    if (downloadDialog != null && active) AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(downloadDialog.title) },
        text = { Text(downloadDialog.body) },
        confirmButton = { TextButton(onClick = { confirmation = null; downloadDialog.accept() }) { Text("仅 Wi-Fi 下载") } },
        dismissButton = { Row {
            TextButton(onClick = {
                val code = languages.firstOrNull { downloadDialog.title == "下载 ${it.second}" }?.first
                confirmation = null; if (code != null) downloadLanguage(code, false)
            }) { Text("允许当前网络") }
            TextButton(onClick = { confirmation = null }) { Text("取消") }
        } })
    else LibraryConfirmationDialog(confirmation) { confirmation = null }
}
