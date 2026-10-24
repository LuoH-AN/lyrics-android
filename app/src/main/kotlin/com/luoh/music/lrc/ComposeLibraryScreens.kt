package com.luoh.music.lrc

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.luoh.music.lrc.ui.agora.CollapsingSettingsLazyScaffold
import com.luoh.music.lrc.ui.agora.CollapsingSettingsScaffold
import com.luoh.music.lrc.ui.agora.SettingsGroup
import com.luoh.music.lrc.ui.agora.SettingsItem
import com.luoh.music.lrc.ui.agora.LocalSettingsScrollRetentionActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** Business destinations use Agora's Compose scaffolds and no legacy screen containers. */
@Composable
internal fun LibraryDestination(route: ComposeRoute, onNavigate: (ComposeRoute) -> Unit, onBack: () -> Unit) {
    when (route.kind) {
        "translation" -> TranslationComposeScreen(onNavigate, onBack)
        "profiles" -> ApiProfilesComposeScreen(onBack)
        "offsets" -> OffsetMemoriesComposeScreen(onBack)
        "sources" -> SourcesComposeScreen(route, onNavigate, onBack)
        "custom" -> CustomLibraryComposeScreen(onNavigate, onBack)
        "editor" -> CustomEditorComposeScreen(route, onBack)
        "privacy" -> PrivacyComposeScreen(onBack)
    }
}

private data class LibraryConfirmation(val title: String, val body: String, val action: String, val accept: () -> Unit)

@Composable
private fun LibraryConfirmationDialog(value: LibraryConfirmation?, dismiss: () -> Unit) {
    if (value != null && LocalSettingsScrollRetentionActive.current) AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(value.title) },
        text = { Text(value.body) },
        confirmButton = { TextButton(onClick = { dismiss(); value.accept() }) { Text(value.action) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } }
    )
}

@Composable
private fun preferenceRevision(prefs: SharedPreferences, watchedKeys: Set<String>? = null): Int {
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
private fun rememberLibraryScope(): CoroutineScope {
    val scope = rememberCoroutineScope()
    val active = LocalSettingsScrollRetentionActive.current
    DisposableEffect(scope, active) {
        if (!active) scope.coroutineContext.cancelChildren()
        onDispose { }
    }
    return scope
}

@Composable
private fun LibraryStatus(message: String) {
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
private fun LibraryField(label: String, value: String, onValueChange: (String) -> Unit,
    enabled: Boolean = true, secret: Boolean = false) {
    var visible by remember(label) { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) },
        enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
        trailingIcon = if (secret) ({ TextButton(onClick = { visible = !visible }) { Text(if (visible) "隐藏" else "显示") } }) else null)
}

@Composable
private fun LibraryAction(label: String, enabled: Boolean = true, action: () -> Unit) {
    TextButton(onClick = action, enabled = enabled, modifier = Modifier.padding(horizontal = 8.dp)) { Text(label) }
}

/** The exact SettingsGroup row surfaces, split into lazy items for dynamic collections. */
@Composable
private fun LibraryLazyGroupItem(index: Int, count: Int, content: @Composable () -> Unit) {
    val shape = when {
        count == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(5.dp)
    }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = if (index == count - 1) 24.dp else 2.dp), content = content)
}

private fun Context.translationPrefs() = getSharedPreferences("supplement_translation", Context.MODE_PRIVATE)

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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TranslationComposeScreen(onNavigate: (ComposeRoute) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.translationPrefs() }
    val revision = preferenceRevision(prefs)
    val scope = rememberLibraryScope()
    val active = LocalSettingsScrollRetentionActive.current
    var profileId by rememberSaveable { mutableStateOf(TranslationApiProfiles.find(context, prefs.getString("active_api_profile", null)).id) }
    var endpoint by rememberSaveable(profileId) { mutableStateOf("") }
    var model by rememberSaveable(profileId) { mutableStateOf("") }
    // Encrypted credentials are read on IO, never stored in the activity's saved-state bundle.
    var apiKey by remember(profileId) { mutableStateOf("") }
    var profileLoaded by remember(profileId) { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var verifyJob by remember { mutableStateOf<Job?>(null) }
    var profileMenu by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var downloaded by remember { mutableStateOf(emptySet<String>()) }
    var downloading by remember { mutableStateOf(emptyMap<String, Long>()) }
    var elapsed by remember { mutableStateOf(0L) }
    var modelsBusy by remember { mutableStateOf(false) }
    var modelRefresh by remember { mutableIntStateOf(0) }
    val manager = remember { RemoteModelManager.getInstance() }
    val mode = remember(revision) { prefs.getString("mode", "off").orEmpty() }
    val profiles = remember(revision) { TranslationApiProfiles.all(context) }
    val profile = profiles.find { it.id == profileId } ?: TranslationApiProfiles.find(context, profileId)
    val lastStatus = remember(revision) {
        val value = prefs.getString("last_status", "").orEmpty()
        val track = prefs.getString("last_track", "").orEmpty()
        if (value.isBlank() || track.isBlank()) value else "《$track》 · $value"
    }
    val languages = remember { (listOf("en", "ja", "ko", "zh") + TranslateLanguage.getAllLanguages()
        .filter { it !in setOf("en", "ja", "ko", "zh") }
        .sortedBy { Locale.forLanguageTag(it).getDisplayLanguage(Locale.SIMPLIFIED_CHINESE) }) }
    fun languageTitle(code: String) = Locale.forLanguageTag(code).getDisplayLanguage(Locale.SIMPLIFIED_CHINESE) + " · $code"
    fun needed(code: String) = setOf(code, "zh").filter { it != "en" }
    fun saveDraft(id: String = profileId, address: String = endpoint, modelName: String = model, secret: String = apiKey) {
        prefs.edit().putString(TranslationApiProfiles.endpointKey(id), address.trim())
            .putString(TranslationApiProfiles.modelKey(id), modelName.trim()).apply()
        if (secret.isNotBlank()) SecretStorage(context, id).save(secret.trim())
    }
    fun applyMode(value: String) {
        prefs.edit().putString("mode", value).putString("language", "auto").apply()
        LyricsOverlayService.instance?.refreshSupplementTranslation()
        status = when (value) {
            "off" -> "补充翻译已关闭"
            "offline" -> "离线机翻已应用，请确认所需语言包已下载"
            else -> "自定义 API 已应用；修改配置后请确认并验证连通性"
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
                status = "已切换到 ${selected.label}；首次使用请填写密钥并验证"
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
                    Tasks.whenAll(needed(code).map { manager.download(TranslateRemoteModel.Builder(it).build(), conditions) }).await()
                }
                status = "语言包已就绪，可以立即使用离线机翻"
                LyricsOverlayService.instance?.refreshSupplementTranslation()
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                status = "下载等待超过 2 分钟，请检查手机代理后重试；后台若完成，可刷新状态确认"
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { status = "下载未完成，请确认手机当前网络可访问 Google ML Kit"
            } finally { downloading = downloading - code; modelRefresh++ }
        }
    }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        try {
            val migrated = withContext(Dispatchers.IO) { ensureTranslationProfile(context) }
            if (migrated != profileId) profileId = migrated
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { status = "旧配置读取失败，请重新填写 API 配置" }
    }
    LaunchedEffect(profileId, active) {
        if (!active) return@LaunchedEffect
        val id = profileId
        val values = withContext(Dispatchers.IO) {
            val resolved = TranslationApiProfiles.find(context, id)
            Triple(prefs.getString(TranslationApiProfiles.endpointKey(id), resolved.defaultEndpoint).orEmpty(),
                prefs.getString(TranslationApiProfiles.modelKey(id), resolved.defaultModel).orEmpty(), SecretStorage(context, id).read())
        }
        // Draft fields survive configuration changes; initial persisted data only fills empty drafts.
        if (endpoint.isEmpty()) endpoint = values.first
        if (model.isEmpty()) model = values.second
        apiKey = values.third; profileLoaded = true
    }
    LaunchedEffect(revision, active) {
        if (!active) return@LaunchedEffect
        val selected = TranslationApiProfiles.find(context, prefs.getString("active_api_profile", null)).id
        if (selected != profileId) { verifyJob?.cancel(); verifying = false; profileId = selected }
    }
    LaunchedEffect(modelRefresh, active) {
        if (!active) return@LaunchedEffect
        modelsBusy = true
        try { downloaded = manager.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet() }
        catch (error: CancellationException) { throw error }
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
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                    listOf("off" to "关闭", "offline" to "离线机翻", "api" to "自定义 API").forEachIndexed { index, pair ->
                        SegmentedButton(selected = mode == pair.first, onClick = { applyMode(pair.first) },
                            shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(pair.second) }
                    }
                }
            }))
        }
        if (mode == "api") item(key = "api") {
            SettingsGroup("翻译 API", items = listOf({
                Column {
                    SettingsItem(modifier = Modifier.clickable(enabled = profileLoaded) { profileMenu = true },
                        headlineContent = { Text(profile.label) }, supportingContent = { Text("选择服务或管理 API 配置") })
                    LibraryField("HTTPS 服务地址", endpoint, { endpoint = it }, profileLoaded && !verifying)
                    LibraryField("模型名称", model, { model = it }, profileLoaded && !verifying)
                    LibraryField("API Key", apiKey, { apiKey = it }, profileLoaded && !verifying, secret = true)
                    LibraryAction("删除已保存的密钥", profileLoaded && !verifying) {
                        val id = profileId
                        confirmation = LibraryConfirmation("删除已保存的密钥？", "只删除当前配置的本机密钥，地址和模型仍会保留。", "删除密钥") {
                            scope.launch {
                                withContext(Dispatchers.IO) { SecretStorage(context, id).save("") }
                                if (profileId == id) { apiKey = ""; status = "已删除当前服务保存的 API Key" }
                            }
                        }
                    }
                    Button(enabled = profileLoaded && !verifying, modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = {
                        val id = profileId; val address = endpoint.trim(); val modelName = model.trim(); val secret = apiKey.trim()
                        if (!address.startsWith("https://") || modelName.isBlank() || secret.isBlank()) {
                            status = "请完整填写 HTTPS 服务地址、模型和 API Key"
                        } else {
                            verifying = true
                            verifyJob = scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { saveDraft(id, address, modelName, secret) }
                                    prefs.edit().putString("mode", "api").putString("active_api_profile", id).putString("language", "auto").apply()
                                    LyricsOverlayService.instance?.refreshSupplementTranslation()
                                    status = "配置已保存，正在连接翻译服务…"
                                    val translated = withContext(Dispatchers.IO) { SupplementTranslation(context).testApi(address, modelName, secret) }
                                    if (profileId == id) { status = "API 连接成功，测试翻译：$translated"; LyricsOverlayService.instance?.refreshSupplementTranslation() }
                                } catch (error: CancellationException) { throw error
                                } catch (error: Exception) { if (profileId == id) status = "API 获取失败：${error.message.orEmpty().ifBlank { "未知错误" }.take(180)}"
                                } finally { if (profileId == id) verifying = false }
                            }
                        }
                    }) { Text(if (verifying) "正在验证 API…" else "确认配置并验证连通性") }
                }
            }))
        }
        if (mode == "offline") {
            item(key = "offline-tools") {
                SettingsGroup("离线语言包 · 需要代理", items = listOf({
                    Column {
                        if (modelsBusy || downloading.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        if (downloading.isNotEmpty()) LibraryStatus("语言包下载中… 已用时 ${elapsed}s（系统未提供百分比/速度）")
                        LibraryField("搜索更多语言", search, { search = it })
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
            val filtered = if (search.isBlank()) languages.take(4) else languages.filter { languageTitle(it).contains(search.trim(), true) }
            itemsIndexed(filtered, key = { _, code -> "language-$code" }) { index, code ->
                val ready = downloaded.containsAll(needed(code))
                val hasModel = code in downloaded || (code == "en" && "zh" in downloaded)
                val inProgress = code in downloading
                LibraryLazyGroupItem(index, filtered.size) {
                    SettingsItem(headlineContent = { Text(languageTitle(code)) },
                        supportingContent = { Text(when { inProgress -> "下载中…"; ready -> "已下载"; hasModel -> "缺中文模型"; else -> "未下载" }) },
                        trailingContent = {
                            TextButton(enabled = !inProgress, onClick = {
                                if (hasModel) {
                                    val removable = if (code == "en") "zh" else code
                                    confirmation = LibraryConfirmation("删除语言包", if (removable == "zh")
                                        "删除共用中文模型后，所有离线中文翻译都需要重新下载。已缓存译文仍会保留。"
                                        else "删除 ${languageTitle(code)} 语言包？已缓存译文仍会保留。", "确认删除") {
                                        scope.launch {
                                            try { manager.deleteDownloadedModel(TranslateRemoteModel.Builder(removable).build()).await(); status = "语言包已删除"; modelRefresh++ }
                                            catch (error: CancellationException) { throw error }
                                            catch (error: Exception) { status = "删除失败，请停止离线翻译后重试" }
                                        }
                                    }
                                } else confirmation = LibraryConfirmation("下载 ${languageTitle(code)}", "将下载该语言及缺失的共用中文模型，每个模型约 30 MB。请确保手机代理可用。", "仅 Wi-Fi 下载") { downloadLanguage(code, true) }
                            }) { Text(if (hasModel) "删除" else "下载") }
                        })
                }
            }
        }
        if (status.isNotBlank()) item(key = "status") { LibraryStatus(status) }
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
                val code = languages.firstOrNull { downloadDialog.title == "下载 ${languageTitle(it)}" }
                confirmation = null; if (code != null) downloadLanguage(code, false)
            }) { Text("允许当前网络") }
            TextButton(onClick = { confirmation = null }) { Text("取消") }
        } })
    else LibraryConfirmationDialog(confirmation) { confirmation = null }
}

@Composable
private fun ApiProfilesComposeScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.translationPrefs() }
    val revision = preferenceRevision(prefs)
    val scope = rememberLibraryScope()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("填写完整后保存；密钥会加密保存在本机。") }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    val profiles = remember(revision) { TranslationApiProfiles.all(context) }
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
        item(key = "intro") { LibraryStatus("可添加多套兼容 Chat Completions 的服务，并随时切换。") }
        item(key = "editor") {
            SettingsGroup(if (editingId == null) "添加 API" else "编辑 API 配置", items = listOf({
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
                                    editingId = saved.id; status = "${saved.label} 已保存并切换使用"
                                    LyricsOverlayService.instance?.refreshSupplementTranslation()
                                } catch (error: CancellationException) { throw error }
                                catch (error: Exception) { status = error.message ?: "保存失败，请重试" }
                                finally { busy = false }
                            }
                        }
                    }) { Text(if (busy) "正在处理…" else if (editingId == null) "保存并使用" else "保存修改并使用") }
                    if (editingId != null) LibraryAction("添加另一套 API", !busy) { clearEditor() }
                    LibraryStatus(status)
                }
            }))
        }
        if (profiles.isEmpty()) item(key = "empty") { LibraryStatus("还没有 API 配置，请在上方添加") }
        itemsIndexed(profiles, key = { _, profile -> "profile-${profile.id}" }) { index, profile ->
            LibraryLazyGroupItem(index, profiles.size) {
                SettingsItem(modifier = Modifier.clickable(enabled = !busy) { edit(profile) }, headlineContent = { Text(profile.label) },
                    supportingContent = { Text(if (profile.id == activeId) "使用中 · 点击编辑" else "点击编辑") },
                    trailingContent = { TextButton(enabled = !busy, onClick = {
                        confirmation = LibraryConfirmation("删除 ${profile.label}？", "地址、模型和本机加密密钥都会删除，此操作无法恢复。", "确认删除") {
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

private data class ComposeOffsetEntry(val id: String, val title: String, val artist: String, val source: String, val offsetMs: Int, val updatedAt: Long)

private fun readOffsetEntries(prefs: SharedPreferences): List<ComposeOffsetEntry> {
    val index = runCatching { JSONObject(prefs.getString(LyricsOverlayService.PREF_LYRIC_OFFSET_INDEX, "{}").orEmpty().ifBlank { "{}" }) }.getOrDefault(JSONObject())
    return index.keys().asSequence().mapNotNull { id ->
        val value = index.optJSONObject(id) ?: return@mapNotNull null
        val fallback = value.optString("identity").split('\u0000', limit = 2)
        ComposeOffsetEntry(id, value.optString("title").ifBlank { fallback.getOrNull(0).orEmpty() },
            value.optString("artist").ifBlank { fallback.getOrNull(1).orEmpty() }, value.optString("source"),
            value.optInt("offsetMs", 0), value.optLong("updatedAt", 0)).takeIf { it.offsetMs != 0 }
    }.sortedByDescending { it.updatedAt }.toList()
}

@Composable
private fun OffsetMemoriesComposeScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE) }
    val revision = preferenceRevision(prefs, setOf(LyricsOverlayService.PREF_LYRIC_OFFSET_INDEX))
    val scope = rememberLibraryScope()
    val active = LocalSettingsScrollRetentionActive.current
    var entries by remember { mutableStateOf(emptyList<ComposeOffsetEntry>()) }
    var loaded by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    LaunchedEffect(revision, active) {
        if (!active) return@LaunchedEffect
        entries = withContext(Dispatchers.IO) { readOffsetEntries(prefs) }; loaded = true
    }
    CollapsingSettingsLazyScaffold(title = "同步记忆", onBack = onBack) {
        item(key = "summary") {
            SettingsGroup("同步调整", items = listOf({
                Column {
                    SettingsItem(headlineContent = { Text(if (!loaded) "正在读取…" else if (entries.isEmpty()) "还没有保存的同步调整" else "已保存 ${entries.size} 条") })
                    if (entries.isNotEmpty()) LibraryAction("重置全部同步记忆") {
                        confirmation = LibraryConfirmation("重置全部同步调整？", "所有歌曲和歌词源都会恢复原始同步时间，此操作无法撤销。", "确认重置") {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val editor = prefs.edit()
                                    prefs.all.keys.filter { it.startsWith(LyricsOverlayService.PREF_LYRIC_OFFSET_ENTRY_PREFIX) }.forEach { editor.remove(it) }
                                    editor.remove(LyricsOverlayService.PREF_LYRIC_OFFSET_INDEX).putInt(LyricsOverlayService.PREF_LYRIC_OFFSET_MS, 0).apply()
                                }
                                if (LyricsOverlayService.isRunning) context.startService(Intent(context, LyricsOverlayService::class.java)
                                    .setAction(LyricsOverlayService.ACTION_CLEAR_LYRIC_OFFSET_MEMORIES))
                            }
                        }
                    }
                }
            }))
        }
        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            LibraryLazyGroupItem(index, entries.size) {
                SettingsItem(headlineContent = { Text(entry.title.ifBlank { "未知歌曲" }) },
                    supportingContent = { Text(listOf(entry.artist, "${entry.source.ifBlank { "歌词源" }} · ${LyricSyncText.format(entry.offsetMs)}").filter { it.isNotBlank() }.joinToString("\n")) },
                    trailingContent = { TextButton(onClick = {
                        confirmation = LibraryConfirmation("删除这条同步记忆？", "《${entry.title}》在 ${entry.source} 的歌词将恢复原始同步时间。", "删除记忆") {
                            scope.launch {
                                val key = LyricsOverlayService.PREF_LYRIC_OFFSET_ENTRY_PREFIX + entry.id
                                withContext(Dispatchers.IO) {
                                    val index = runCatching { JSONObject(prefs.getString(LyricsOverlayService.PREF_LYRIC_OFFSET_INDEX, "{}").orEmpty()) }.getOrDefault(JSONObject())
                                    index.remove(entry.id)
                                    prefs.edit().remove(key).putString(LyricsOverlayService.PREF_LYRIC_OFFSET_INDEX, index.toString()).apply()
                                }
                                if (LyricsOverlayService.isRunning) context.startService(Intent(context, LyricsOverlayService::class.java)
                                    .setAction(LyricsOverlayService.ACTION_DELETE_LYRIC_OFFSET_MEMORY).putExtra(LyricsOverlayService.EXTRA_LYRIC_OFFSET_MEMORY_KEY, key))
                            }
                        }
                    }) { Text("删除") } })
            }
        }
    }
    LibraryConfirmationDialog(confirmation) { confirmation = null }
}

private fun readSourceEntries(prefs: SharedPreferences): List<JSONObject> {
    val array = runCatching { JSONArray(prefs.getString("match_memory_v2", "[]").orEmpty().ifBlank { "[]" }) }.getOrDefault(JSONArray())
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

private fun saveSourceEntries(context: Context, prefs: SharedPreferences, values: List<JSONObject>, notify: Boolean = true) {
    prefs.edit().putString("match_memory_v2", JSONArray(values).toString()).apply()
    if (notify && LyricsOverlayService.isRunning) context.startService(Intent(context, LyricsOverlayService::class.java).setAction(LyricsOverlayService.ACTION_REFRESH_MATCHES))
}

private data class ComposeSourceVersion(val id: String, val title: String, val artist: String, val summary: String, val preview: String, val candidate: JSONObject, val current: Boolean)

private fun sourceVersions(entry: JSONObject): List<ComposeSourceVersion> {
    val history = entry.optJSONArray("history") ?: JSONArray().put(entry.optJSONObject("candidate"))
    return (0 until history.length()).mapNotNull { index ->
        val candidate = history.optJSONObject(index) ?: return@mapNotNull null
        val seconds = candidate.optLong("duration") / 1000
        val current = !entry.optBoolean("needsReview") && candidate.optString("recordId") == entry.optJSONObject("candidate")?.optString("recordId")
        ComposeSourceVersion("${candidate.optString("recordId")}:$index",
            candidate.optString("title").ifBlank { entry.optString("title") },
            candidate.optString("artist").ifBlank { entry.optString("artist") } + " · " + candidate.optString("source"),
            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} · " + (if (candidate.optString("wordLyrics").isNotBlank()) "含逐字数据" else "普通歌词") +
                (if (candidate.optString("translatedLyrics").isNotBlank()) " · 含译文" else ""),
            candidate.optString("lyrics").lineSequence().map { it.replace(Regex("^(\\[[^]]+])+"), "").trim() }
                .filter { it.isNotBlank() }.take(16).joinToString("\n"), candidate, current)
    }
}

@Composable
private fun SourcesComposeScreen(route: ComposeRoute, onNavigate: (ComposeRoute) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences(LyricsOverlayService.PREFS_NAME, Context.MODE_PRIVATE) }
    val revision = preferenceRevision(prefs, setOf("match_memory_v2"))
    val scope = rememberLibraryScope()
    val active = LocalSettingsScrollRetentionActive.current
    val selectedKey = route.id.takeIf { it.isNotBlank() }
    var query by rememberSaveable(route.id) { mutableStateOf(route.category) }
    var entries by remember { mutableStateOf(emptyList<JSONObject>()) }
    var filtered by remember { mutableStateOf(emptyList<JSONObject>()) }
    var versions by remember { mutableStateOf(emptyList<ComposeSourceVersion>()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable(route.id) { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    val entry = entries.find { it.optString("key") == selectedKey }
    LaunchedEffect(revision, route.id, query, active) {
        if (!active) return@LaunchedEffect
        if (query.isNotBlank() && selectedKey == null) delay(120)
        val values = withContext(Dispatchers.IO) {
            val values = readSourceEntries(prefs)
            Triple(values, values.find { it.optString("key") == selectedKey }?.let(::sourceVersions).orEmpty(),
                values.asReversed().filter { (it.optString("title") + " " + it.optString("artist")).contains(query.trim(), true) })
        }
        entries = values.first; versions = values.second; filtered = values.third; loaded = true
    }
    fun update(key: String, change: (JSONObject) -> Unit) {
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) {
                val values = readSourceEntries(prefs); values.find { it.optString("key") == key }?.let(change)
                saveSourceEntries(context, prefs, values)
            } } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = "无法保存来源选择，请重试" }
            finally { busy = false }
        }
    }
    fun searchNext(item: JSONObject) {
        if (busy) return
        if (item.optString("title").isBlank()) { message = "旧版记录缺少歌曲信息，请先播放这首歌，获取歌词后再打开此页。"; return }
        busy = true; message = ""
        val key = item.optString("key")
        val history = item.optJSONArray("history") ?: JSONArray().put(item.optJSONObject("candidate"))
        val excluded = (0 until history.length()).mapNotNull { history.optJSONObject(it)?.optString("recordId") }.filter { it.isNotBlank() }.toSet()
        scope.launch {
            try {
                val candidate = withContext(Dispatchers.IO) {
                    val repository = DirectLyricsRepository()
                    try { repository.rematch(item.optString("source"), item.optString("title"), item.optString("artist"),
                        item.optString("album"), item.optLong("duration"), excluded)?.toJson() }
                    finally { repository.close() }
                }
                val stored = withContext(Dispatchers.IO) {
                    val values = readSourceEntries(prefs); val latest = values.find { it.optString("key") == key }
                    if (candidate == null || latest == null) false else {
                        candidate.remove("alternatives")
                        val recent = latest.optJSONArray("history") ?: history
                        if ((0 until recent.length()).none { recent.optJSONObject(it)?.optString("recordId") == candidate.optString("recordId") }) recent.put(candidate)
                        latest.put("history", recent).put("at", System.currentTimeMillis())
                        saveSourceEntries(context, prefs, values, false); true
                    }
                }
                message = if (stored) "已找到新候选，预览后点击选用。当前歌词保持不变。" else "没有更多可靠的匹配结果，已保留现有版本。"
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = "请求失败或来源暂时不可用，请稍后重试。" }
            finally { busy = false }
        }
    }
    CollapsingSettingsLazyScaffold(title = if (selectedKey == null) "歌词源管理" else "版本与预览", onBack = onBack) {
        if (message.isNotBlank()) item(key = "message") { LibraryStatus(message) }
        if (selectedKey == null) {
            item(key = "search") { LibraryField("搜索已记录的歌名或歌手", query, { query = it }) }
            if (filtered.isEmpty()) item(key = "empty") { LibraryStatus(if (!loaded) "正在读取…" else if (entries.isEmpty()) "暂无重匹配记录" else "没有符合搜索条件的歌曲") }
            itemsIndexed(filtered, key = { _, item -> item.optString("key") }) { index, item ->
                LibraryLazyGroupItem(index, filtered.size) {
                    SettingsItem(modifier = Modifier.clickable { onNavigate(ComposeRoute("sources", id = item.optString("key"))) },
                        headlineContent = { Text(item.optString("title").ifBlank { "未知歌曲" }) },
                        supportingContent = { Text(item.optString("artist") + "\n" + item.optString("source")) },
                        trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("sources", id = item.optString("key"))) }) { Text("查看版本") } })
                }
            }
            item(key = "cache") {
                SettingsGroup("匹配记录与缓存", items = listOf({
                    Column {
                        SettingsItem(headlineContent = { Text("已记录 ${entries.size} 个来源") })
                        LibraryAction("清除匹配记录与缓存…", !busy) {
                            val count = entries.map { it.optString("title") + "\u0000" + it.optString("artist") }.distinct().size
                            confirmation = LibraryConfirmation("清除匹配记录与缓存？", "将清除 $count 首歌曲的匹配记录（包括手动选定的版本与历史版本），以及主页缓存的歌词。此操作无法撤销。\n\n自定义歌词、翻译语言包和同步设置都会保留。", "确认清除") {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        saveSourceEntries(context, prefs, emptyList())
                                        context.getSharedPreferences("home_lyric_cache_v1", Context.MODE_PRIVATE).edit().clear().apply()
                                    }
                                    message = "已清除匹配记录与缓存，下次搜索恢复自动匹配"
                                }
                            }
                        }
                    }
                }))
            }
        } else if (entry == null) item(key = "missing") { LibraryStatus(if (loaded) "这首歌的来源记忆已不存在" else "正在读取…") }
        else {
            item(key = "song") {
                SettingsGroup("歌曲", items = listOf({
                    Column {
                        SettingsItem(headlineContent = { Text(entry.optString("title")) }, supportingContent = { Text(entry.optString("artist")) })
                        LibraryAction(if (busy) "正在处理…" else "搜索下一个匹配结果（仅预览）", !busy) { searchNext(entry) }
                        if (entry.optJSONObject("original") != null) LibraryAction("恢复最初的歌词", !busy) {
                            val original = entry.optJSONObject("original") ?: return@LibraryAction
                            update(entry.optString("key")) { it.put("candidate", original).put("needsReview", false) }
                        }
                    }
                }))
            }
            items(versions, key = { it.id }) { version ->
                SettingsGroup(if (version.current) "当前选择" else "候选版本", items = listOf({
                    Column {
                        SettingsItem(headlineContent = { Text(version.title) }, supportingContent = { Text(version.artist + "\n" + version.summary) })
                        Text(version.preview, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        LibraryAction(if (version.current) "正在使用" else "选用这个版本", !version.current && !busy) {
                            update(entry.optString("key")) {
                                it.remove("auto"); it.put("candidate", version.candidate).put("needsReview", false).put("at", System.currentTimeMillis())
                            }
                        }
                    }
                }))
            }
            item(key = "delete") {
                SettingsGroup("来源记忆", items = listOf({
                    LibraryAction("删除记忆 / 恢复自动匹配", !busy) {
                        confirmation = LibraryConfirmation("删除这首歌的来源记忆？", "将删除《${entry.optString("title")}》在 ${entry.optString("source")} 的选择及历史版本，下次搜索恢复自动匹配。", "删除记忆") {
                            scope.launch {
                                withContext(Dispatchers.IO) { saveSourceEntries(context, prefs, readSourceEntries(prefs).filter { it.optString("key") != entry.optString("key") }) }
                                onBack()
                            }
                        }
                    }
                }))
            }
        }
    }
    LibraryConfirmationDialog(confirmation) { confirmation = null }
}

private data class ComposeCustomEntry(val entry: CustomLyricsStore.Entry, val summary: String)

@Composable
private fun CustomLibraryComposeScreen(onNavigate: (ComposeRoute) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("custom_lyrics_v1", Context.MODE_PRIVATE) }
    val revision = preferenceRevision(prefs)
    val scope = rememberLibraryScope()
    val active = LocalSettingsScrollRetentionActive.current
    var entries by remember { mutableStateOf(emptyList<ComposeCustomEntry>()) }
    var playing by remember { mutableStateOf<NowPlaying.Track?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    LaunchedEffect(revision, active) {
        if (!active) return@LaunchedEffect
        val values = withContext(Dispatchers.IO) {
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            CustomLyricsStore.all(context).map { entry ->
                val parsed = CustomLyricsStore.parse(entry.lyrics)
                ComposeCustomEntry(entry, "${parsed.lineCount} 行歌词${if (parsed.translationCount > 0) " · 含译文" else ""} · ${timeFormat.format(Date(entry.updatedAt))}")
            } to NowPlaying.current(context)
        }
        entries = values.first; playing = values.second; loaded = true
    }
    CollapsingSettingsLazyScaffold(title = "自定义歌词", onBack = onBack) {
        playing?.let { track -> item(key = "playing") {
            SettingsGroup("正在播放", items = listOf({
                SettingsItem(headlineContent = { Text(track.title.ifBlank { "未知歌曲" }) }, supportingContent = { Text(track.artist) },
                    trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("editor", title = track.title, artist = track.artist)) }) { Text("添加或编辑") } })
            }))
        } }
        item(key = "new") {
            SettingsGroup("歌词库", items = listOf({
                SettingsItem(headlineContent = { Text("新建自定义歌词") }, supportingContent = { Text("手动填写歌名或导入 LRC · 已保存 ${entries.size} 份") },
                    trailingContent = { TextButton(onClick = { onNavigate(ComposeRoute("editor")) }) { Text("新建") } })
            }))
        }
        if (entries.isEmpty()) item(key = "empty") { LibraryStatus(if (loaded) "还没有自定义歌词" else "正在读取…") }
        itemsIndexed(entries, key = { _, item -> item.entry.id }) { index, item ->
            val entry = item.entry
            LibraryLazyGroupItem(index, entries.size) {
                Column {
                    SettingsItem(modifier = Modifier.clickable { onNavigate(ComposeRoute("editor", id = entry.id)) },
                        headlineContent = { Text(entry.title.ifBlank { "未命名" }) },
                        supportingContent = { Text(entry.artist.ifBlank { "所有歌手" } + "\n" + item.summary) })
                    Row {
                        LibraryAction("编辑") { onNavigate(ComposeRoute("editor", id = entry.id)) }
                        LibraryAction("删除") {
                            confirmation = LibraryConfirmation("删除这份自定义歌词？", "${entry.title.ifBlank { "未命名" }}${if (entry.artist.isNotBlank()) " · ${entry.artist}" else ""}\n删除后这首歌会恢复自动匹配。", "确认删除") {
                                scope.launch {
                                    withContext(Dispatchers.IO) { CustomLyricsStore.delete(context, entry.id) }
                                    LyricsOverlayService.instance?.reloadCustomLyrics()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LibraryConfirmationDialog(confirmation) { confirmation = null }
}

/** Saved-state fields hold drafts, while parsing/import/network work stays in cancellable IO jobs. */
@Composable
private fun CustomEditorComposeScreen(route: ComposeRoute, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberLibraryScope()
    val active = LocalSettingsScrollRetentionActive.current
    var editingId by rememberSaveable(route.id, route.title, route.artist) { mutableStateOf(route.id.takeIf { it.isNotBlank() }) }
    var title by rememberSaveable(route.id, route.title, route.artist) { mutableStateOf(route.title) }
    var artist by rememberSaveable(route.id, route.title, route.artist) { mutableStateOf(route.artist) }
    var lyrics by rememberSaveable(route.id, route.title, route.artist, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var savedSnapshot by rememberSaveable(route.id, route.title, route.artist) { mutableStateOf("") }
    var loaded by rememberSaveable(route.id, route.title, route.artist) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var confirmation by remember { mutableStateOf<LibraryConfirmation?>(null) }
    // A digest avoids duplicating up to 200K characters in Android's saved-state transaction.
    fun snapshot() = MessageDigest.getInstance("SHA-256")
        .digest(listOf(title, artist, lyrics.text).joinToString("\u0000").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun fillTags(raw: String) {
        val tags = CustomLyricsStore.metadata(raw)
        if (title.isBlank()) title = tags["ti"].orEmpty()
        if (artist.isBlank()) artist = tags["ar"].orEmpty()
    }
    fun leave() {
        if (saving) return
        if (!loaded || snapshot() == savedSnapshot) onBack()
        else confirmation = LibraryConfirmation("放弃未保存的修改？", "刚才输入或导入的歌词不会保存。", "放弃修改", onBack)
    }
    fun importFile(uri: Uri) {
        if (saving || !loaded) return
        importJob?.cancel(); importing = true
        val before = snapshot()
        importJob = scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readCustomLyricsFile(context, uri) }
                if (snapshot() != before || saving) return@launch
                if (text.isBlank()) { message = "无法读取这个文件，请确认是文本格式的 LRC 歌词"; return@launch }
                lyrics = TextFieldValue(text.trim()); fillTags(text)
                val parsed = withContext(Dispatchers.IO) { CustomLyricsStore.parse(text) }
                message = if (parsed.lineCount > 0) "已导入 ${parsed.lineCount} 行歌词" else "文件里没有识别到带时间轴的歌词行"
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = "无法读取这个文件，请确认是文本格式的 LRC 歌词且不超过 1 MB" }
            finally { importing = false }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importFile(uri) }
    fun save() {
        if (saving || !loaded) return
        val name = title.trim(); val performer = artist.trim(); val raw = lyrics.text.trim(); val oldId = editingId
        if (name.isBlank()) { message = "请填写歌名"; return }
        if (raw.length > CustomLyricsStore.MAX_LENGTH) { message = "歌词太长，请删减后再保存"; return }
        saving = true; importJob?.cancel()
        scope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    CustomLyricsStore.parse(raw).also { if (it.lineCount > 0) CustomLyricsStore.save(context, name, performer, raw, oldId) }
                }
                if (parsed.lineCount == 0) { message = "没有识别到带时间轴的歌词行，格式示例：[01:23.45]歌词"; return@launch }
                savedSnapshot = snapshot()
                LyricsOverlayService.instance?.reloadCustomLyrics(); onBack()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = "保存失败，请重试" }
            finally { saving = false }
        }
    }
    LaunchedEffect(route.id, route.title, route.artist, active) {
        if (!active) return@LaunchedEffect
        if (!loaded) {
            try {
                val existing = withContext(Dispatchers.IO) {
                    route.id.takeIf { it.isNotBlank() }?.let { CustomLyricsStore.get(context, it) } ?: CustomLyricsStore.find(context, route.title, route.artist)
                }
                if (existing != null) { editingId = existing.id; title = existing.title; artist = existing.artist; lyrics = TextFieldValue(existing.lyrics) }
                savedSnapshot = snapshot(); loaded = true
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = "无法读取已保存的歌词，请重试" }
        }
    }
    LaunchedEffect(lyrics.text, active) {
        if (!active) return@LaunchedEffect
        val raw = lyrics.text
        status = when { raw.isBlank() -> "粘贴、输入或导入 LRC 歌词"; raw.length > CustomLyricsStore.MAX_LENGTH -> "歌词太长，请删减到 ${CustomLyricsStore.MAX_LENGTH / 1000}K 字符以内"; else -> "" }
        if (raw.isBlank() || raw.length > CustomLyricsStore.MAX_LENGTH) return@LaunchedEffect
        delay(120)
        val parsed = withContext(Dispatchers.IO) { CustomLyricsStore.parse(raw) }
        status = if (parsed.lineCount == 0) "没有识别到带时间轴的歌词行" else buildString {
            append("识别到 ${parsed.lineCount} 行歌词")
            if (parsed.translationCount > 0) append(" · ${parsed.translationCount} 行译文")
            if (parsed.ignoredCount > 0) append(" · ${parsed.ignoredCount} 行没有时间轴，已忽略")
        }
    }
    BackHandler(enabled = active) { leave() }
    CollapsingSettingsScaffold(title = if (editingId == null) "添加自定义歌词" else "编辑自定义歌词", onBack = ::leave,
        actions = { TextButton(enabled = loaded && !saving, onClick = ::save) { Text(if (saving) "保存中…" else "保存") } }) {
        SettingsGroup("歌曲信息", items = listOf({
            Column {
                LibraryField("歌名（必填）", title, { title = it }, loaded && !saving)
                LibraryField("歌手（留空匹配所有同名歌曲）", artist, { artist = it }, loaded && !saving)
            }
        }))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = loaded && !saving && !importing, onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text(if (importing) "正在导入…" else "导入 .lrc 文件") }
            TextButton(enabled = loaded && !saving, onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (text.isBlank()) message = "剪贴板里没有文字"
                else {
                    if (focused) {
                        val start = lyrics.selection.min.coerceIn(0, lyrics.text.length); val end = lyrics.selection.max.coerceIn(0, lyrics.text.length)
                        lyrics = TextFieldValue(lyrics.text.replaceRange(start, end, text), TextRange(start + text.length))
                    } else lyrics = TextFieldValue(text.trim())
                    fillTags(text)
                }
            }) { Text("粘贴剪贴板") }
        }
        OutlinedTextField(value = lyrics, onValueChange = { lyrics = it }, enabled = loaded && !saving,
            label = { Text("LRC 时间轴歌词") }, minLines = 10, maxLines = 16,
            placeholder = { Text("[00:12.30]第一句歌词\n[00:16.85]第二句歌词\n[00:16.85]同一时间戳的第二行作为译文") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused })
        LibraryStatus(if (!loaded) "正在读取歌词…" else status)
        LibraryStatus(message)
        if (editingId != null) LibraryAction("删除这份自定义歌词", loaded && !saving) {
            val id = editingId ?: return@LibraryAction
            confirmation = LibraryConfirmation("删除这份自定义歌词？", "《$title》会恢复自动匹配歌词。", "确认删除") {
                scope.launch {
                    try { withContext(Dispatchers.IO) { CustomLyricsStore.delete(context, id) }; LyricsOverlayService.instance?.reloadCustomLyrics(); onBack() }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { message = "删除失败，请重试" }
                }
            }
        }
    }
    LibraryConfirmationDialog(confirmation) { confirmation = null }
}

/** Bounded read plus the same UTF-8/BOM/UTF-16/GB18030 fallback as the previous LRC editor. */
private fun readCustomLyricsFile(context: Context, uri: Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer); if (read < 0) break
            output.write(buffer, 0, read); require(output.size() <= 1_000_000) { "File too large" }
        }
        output.toByteArray()
    } ?: return ""
    val text = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> runCatching { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { String(bytes, Charset.forName("GB18030")) }
    }
    return text.removePrefix(0xFEFF.toChar().toString())
}

@Composable
private fun PrivacyComposeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = remember(context) { context.getString(R.string.privacy_summary).split("\n\n") }
    CollapsingSettingsScaffold(title = "隐私说明", onBack = onBack) {
        LibraryStatus("本应用如何处理你的数据")
        paragraphs.forEachIndexed { index, paragraph -> LibraryLazyGroupItem(index, paragraphs.size) {
            Text(paragraph, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp))
        } }
    }
}
