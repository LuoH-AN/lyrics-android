package com.luoh.music.lrc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class TranslationSettingsActivity : AppCompatActivity() {
    private fun col(res: Int) = androidx.core.content.ContextCompat.getColor(this, res)
    private val prefs by lazy { getSharedPreferences("supplement_translation", Context.MODE_PRIVATE) }
    private val manager by lazy { RemoteModelManager.getInstance() }
    private lateinit var languages: LinearLayout
    private lateinit var search: EditText
    private var selectedMode = 0
    private lateinit var modeSegments: List<MaterialButton>
    private lateinit var apiBox: LinearLayout
    private lateinit var offlineBox: LinearLayout
    private lateinit var endpoint: EditText
    private lateinit var model: EditText
    private lateinit var key: EditText
    private lateinit var apiProfileButton: MaterialButton
    private var currentApiProfileId = "glm"
    private lateinit var status: TextView
    private lateinit var lastTranslationStatus: TextView
    private val translationStatusListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "last_status" || key == "last_track") updateLastTranslationStatus()
    }

    private fun updateLastTranslationStatus() {
        if (!::lastTranslationStatus.isInitialized) return
        val value = prefs.getString("last_status", "").orEmpty()
        val track = prefs.getString("last_track", "").orEmpty()
        lastTranslationStatus.text = if (value.isBlank()) "" else if (track.isBlank()) value else "《$track》 · $value"
        lastTranslationStatus.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(translationStatusListener)
        updateLastTranslationStatus()
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(translationStatusListener)
        super.onStop()
    }
    private lateinit var apiVerifyButton: MaterialButton
    private lateinit var downloadProgress: LinearProgressIndicator
    private val apiExecutor = Executors.newSingleThreadExecutor()
    private val downloadTimeouts = mutableMapOf<String, Runnable>()
    private var downloaded = emptySet<String>()
    private val downloading = mutableSetOf<String>()
    private val downloadStartedAt = mutableMapOf<String, Long>()
    private val downloadTicker = Handler(Looper.getMainLooper())
    private val downloadTick = object : Runnable {
        override fun run() {
            if (downloading.isNotEmpty()) {
                val seconds = downloading.mapNotNull { downloadStartedAt[it] }
                    .minOfOrNull { (System.currentTimeMillis() - it) / 1000 } ?: 0
                status.text = "语言包下载中… 已用时 ${seconds}s（系统未提供百分比/速度）"
                downloadTicker.postDelayed(this, 1000)
            }
        }
    }
    private val codes = listOf("en", "ja", "ko", "zh") + TranslateLanguage.getAllLanguages()
        .filter { it !in setOf("en", "ja", "ko", "zh") }.sortedBy { Locale.forLanguageTag(it).getDisplayLanguage(Locale.SIMPLIFIED_CHINESE) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun title(code: String) = Locale.forLanguageTag(code).getDisplayLanguage(Locale.SIMPLIFIED_CHINESE) + " · $code"
    private fun label(text: String, size: Float = 14f) = NativeUi.text(this, text, size).apply {
        setPadding(0, dp(10), 0, dp(8))
    }
    private fun button(text: String, action: () -> Unit) = NativeUi.button(this, text, action = action)
    private fun showStatus(message: String, success: Boolean = false) {
        status.text = message
        status.setTextColor(col(R.color.text_secondary))
        status.visibility = View.VISIBLE
        status.alpha = 0f
        status.translationY = dp(5).toFloat()
        status.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }
    private fun updateModeUi(animate: Boolean) {
        modeSegments.forEachIndexed { index, view ->
            view.isChecked = index == selectedMode
        }
        val showApi = selectedMode == 2
        setPanelVisible(apiBox, showApi, animate)
        setPanelVisible(offlineBox, selectedMode == 1, animate)
    }

    private fun setPanelVisible(panel: View, visible: Boolean, animate: Boolean) {
        panel.animate().cancel()
        if (!animate) {
            panel.visibility = if (visible) View.VISIBLE else View.GONE
            panel.alpha = 1f
            panel.translationY = 0f
        } else if (visible) {
            panel.visibility = View.VISIBLE
            panel.alpha = 0f
            panel.translationY = -dp(8).toFloat()
            panel.animate().alpha(1f).translationY(0f).setDuration(220).start()
        } else if (panel.visibility == View.VISIBLE) {
            panel.animate().alpha(0f).translationY(-dp(8).toFloat()).setDuration(150)
                .withEndAction { panel.visibility = View.GONE }.start()
        }
    }

    private fun applyMode(index: Int) {
        selectedMode = index.coerceIn(0, 2)
        val selected = listOf("off", "offline", "api")[selectedMode]
        prefs.edit().putString("mode", selected).putString("language", "auto").apply()
        updateModeUi(true)
        LyricsOverlayService.instance?.refreshSupplementTranslation()
        showStatus(when (selected) {
            "off" -> "补充翻译已关闭"
            "offline" -> "离线机翻已应用，请确认所需语言包已下载"
            else -> "自定义 API 已应用；修改配置后请确认并验证连通性"
        }, selected != "api")
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!prefs.contains("active_api_profile")) {
            val legacyEndpoint = prefs.getString("endpoint", "").orEmpty()
            val legacyModel = prefs.getString("model", "").orEmpty()
            val legacyKey = SecretStorage(this, "custom").read()
            currentApiProfileId = if (legacyEndpoint.isNotBlank() || legacyModel.isNotBlank() || legacyKey.isNotBlank()) {
                val legacy = TranslationApiProfiles.add(this, "原自定义服务")
                prefs.edit()
                    .putString(TranslationApiProfiles.endpointKey(legacy.id), legacyEndpoint)
                    .putString(TranslationApiProfiles.modelKey(legacy.id), legacyModel).apply()
                if (legacyKey.isNotBlank()) SecretStorage(this, legacy.id).save(legacyKey)
                legacy.id
            } else "glm"
            prefs.edit().putString("active_api_profile", currentApiProfileId).apply()
        } else {
            currentApiProfileId = TranslationApiProfiles.find(this, prefs.getString("active_api_profile", null)).id
        }
        val content = NativeUi.screen(this, "补充翻译")
        content.addView(label("双语只控制显示；需要机翻时，请选择下面的补充方式。", 12f))
        lastTranslationStatus = label("", 12f).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(lastTranslationStatus)
        val settingsCard = NativeUi.column(this)
        settingsCard.addView(label("翻译方式", 19f))
        selectedMode = listOf("off", "offline", "api").indexOf(prefs.getString("mode", "off")).coerceAtLeast(0)
        val segmentRail = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        modeSegments = listOf("关闭", "离线机翻", "自定义 API").mapIndexed { index, text ->
            NativeUi.button(this, text) { if (selectedMode != index) applyMode(index) }.apply {
                id = View.generateViewId()
                isCheckable = true
                textSize = 12f
                setPadding(dp(4), dp(10), dp(4), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
        }
        modeSegments.forEach(segmentRail::addView)
        settingsCard.addView(segmentRail)

        apiBox = NativeUi.column(this, 0)
        apiProfileButton = button("") { showApiProfileMenu() }
        apiBox.addView(apiProfileButton)
        apiBox.addView(NativeUi.field(this, "HTTPS 服务地址").also { endpoint = it.editText!! })
        apiBox.addView(NativeUi.field(this, "模型名称").also { model = it.editText!! })
        apiBox.addView(NativeUi.field(this, "API Key").also {
            key = it.editText!!
            key.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            it.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        })
        apiBox.addView(button("删除已保存的密钥") {
            NativeUi.confirm(this, "删除已保存的密钥？", "只删除当前 API 配置的本机密钥，地址和模型仍会保留。", "删除密钥") {
                SecretStorage(this, currentApiProfileId).save("")
                key.setText("")
                showStatus("已删除当前服务保存的 API Key", true)
            }
        })
        apiVerifyButton = NativeUi.button(this, "确认配置并验证连通性", true) { confirmApi() }
        apiBox.addView(apiVerifyButton)
        settingsCard.addView(apiBox)
        loadApiProfile(currentApiProfileId)

        offlineBox = NativeUi.column(this, 0)
        offlineBox.addView(label("离线语言包 · 需要代理", 16f))
        downloadProgress = LinearProgressIndicator(this).apply {
            isIndeterminate = true
            visibility = View.GONE
            setIndicatorColor(col(R.color.control_active))
            trackColor = col(R.color.control_track)
        }
        offlineBox.addView(downloadProgress, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(6) })
        offlineBox.addView(NativeUi.field(this, "搜索更多语言").also { search = it.editText!! })
        val languageActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        languageActions.addView(button("刷新状态") {
            downloading.clear()
            downloadProgress.visibility = View.VISIBLE
            showStatus("正在读取已下载语言包…")
            refreshModels()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5); topMargin = dp(8) })
        languageActions.addView(button("清理译文缓存") {
            NativeUi.confirm(this, "清理译文缓存？", "删除已缓存的补充译文，保留 API 配置与下载的语言包。", "清理缓存") {
                File(cacheDir, "translations").listFiles()?.filter { it.isFile }?.forEach { it.delete() }
                showStatus("补充翻译缓存已清理", true)
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5); topMargin = dp(8) })
        offlineBox.addView(languageActions)
        languages = NativeUi.column(this, 0)
        offlineBox.addView(languages)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderLanguages() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        settingsCard.addView(offlineBox)
        status = label("").apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        settingsCard.addView(status)
        content.addView(NativeUi.card(this).apply { addView(settingsCard) })
        updateModeUi(false)
        refreshModels()
    }

    override fun onResume() {
        super.onResume()
        if (!::apiProfileButton.isInitialized) return
        val requested = prefs.getString("active_api_profile", currentApiProfileId)
        val resolved = TranslationApiProfiles.find(this, requested)
        if (resolved.id != currentApiProfileId) {
            currentApiProfileId = resolved.id
            loadApiProfile(currentApiProfileId)
        }
        updateApiProfileButton()
    }
    override fun onDestroy() {
        downloadTicker.removeCallbacks(downloadTick)
        downloadTimeouts.values.forEach(downloadTicker::removeCallbacks)
        apiExecutor.shutdownNow()
        super.onDestroy()
    }
    private fun loadApiProfile(profileId: String) {
        val profile = TranslationApiProfiles.find(this, profileId)
        endpoint.setText(prefs.getString(TranslationApiProfiles.endpointKey(profile.id), profile.defaultEndpoint).orEmpty())
        model.setText(prefs.getString(TranslationApiProfiles.modelKey(profile.id), profile.defaultModel).orEmpty())
        key.setText(SecretStorage(this, profile.id).read())
        key.setSelection(key.text.length)
        updateApiProfileButton()
    }
    private fun updateApiProfileButton() {
        if (::apiProfileButton.isInitialized) {
            apiProfileButton.text = TranslationApiProfiles.find(this, currentApiProfileId).label + "  ▾"
        }
    }
    private fun showApiProfileMenu() {
        val profiles = TranslationApiProfiles.all(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("选择翻译 API")
            .setSingleChoiceItems(profiles.map { it.label }.toTypedArray(), profiles.indexOfFirst { it.id == currentApiProfileId }) { dialog, which ->
                dialog.dismiss()
                switchApiProfile(profiles[which])
            }
            .setNeutralButton("管理 API") { _, _ ->
                saveApiProfileDraft()
                startActivity(Intent(this, ApiProfileManagerActivity::class.java))
            }
            .setNegativeButton("取消", null)
            .show()
    }
    private fun switchApiProfile(selected: TranslationApiProfile) {
        if (selected.id == currentApiProfileId) return
        saveApiProfileDraft()
        currentApiProfileId = selected.id
        prefs.edit().putString("active_api_profile", currentApiProfileId).putString("mode", "api").apply()
        selectedMode = 2
        loadApiProfile(currentApiProfileId)
        updateModeUi(false)
        LyricsOverlayService.instance?.refreshSupplementTranslation()
        showStatus("已切换到 ${selected.label}；首次使用请填写密钥并验证")
    }
    private fun saveApiProfileDraft() {
        prefs.edit()
            .putString(TranslationApiProfiles.endpointKey(currentApiProfileId), endpoint.text.toString().trim())
            .putString(TranslationApiProfiles.modelKey(currentApiProfileId), model.text.toString().trim())
            .apply()
        if (key.text.isNotBlank()) SecretStorage(this, currentApiProfileId).save(key.text.toString().trim())
    }
    private fun confirmApi() {
        val address = endpoint.text.toString().trim()
        val modelName = model.text.toString().trim()
        if (!address.startsWith("https://") || modelName.isBlank() ||
                (key.text.isBlank() && SecretStorage(this, currentApiProfileId).read().isBlank())) {
            showStatus("请完整填写 HTTPS 服务地址、模型和 API Key"); return
        }
        runCatching {
            if (key.text.isNotBlank()) SecretStorage(this, currentApiProfileId).save(key.text.toString().trim())
            selectedMode = 2
            prefs.edit().putString("mode", "api").putString("active_api_profile", currentApiProfileId)
                .putString(TranslationApiProfiles.endpointKey(currentApiProfileId), address)
                .putString(TranslationApiProfiles.modelKey(currentApiProfileId), modelName)
                .putString("language", "auto").apply()
            updateModeUi(false)
            LyricsOverlayService.instance?.refreshSupplementTranslation()
            apiVerifyButton.isEnabled = false
            apiVerifyButton.text = "正在验证 API…"
            showStatus("配置已保存，正在连接翻译服务…")
            val apiKey = key.text.toString().trim().ifBlank { SecretStorage(this, currentApiProfileId).read() }
            apiExecutor.execute {
                val tested = runCatching { SupplementTranslation(this).testApi(address, modelName, apiKey) }
                runOnUiThread {
                    apiVerifyButton.isEnabled = true
                    if (tested.isSuccess) {
                        showStatus("API 连接成功，测试翻译：${tested.getOrNull()}", true)
                        apiVerifyButton.text = "连接成功  ✓"
                        LyricsOverlayService.instance?.refreshSupplementTranslation()
                    } else {
                        val reason = tested.exceptionOrNull()?.message.orEmpty().ifBlank { "未知错误" }
                        showStatus("API 获取失败：${reason.take(180)}")
                        apiVerifyButton.text = "重试验证"
                    }
                    apiVerifyButton.postDelayed({ apiVerifyButton.text = "确认配置并验证连通性" }, 2200)
                }
            }
        }.onFailure { showStatus("设置保存失败，请重试") }
    }
    private fun refreshModels() {
        manager.getDownloadedModels(TranslateRemoteModel::class.java).addOnSuccessListener(this) { result ->
            downloaded = result.map { it.language }.toSet()
            downloadProgress.visibility = if (downloading.isEmpty()) View.GONE else View.VISIBLE
            renderLanguages()
        }.addOnFailureListener(this) {
            downloadProgress.visibility = View.GONE
            showStatus("暂时无法读取语言包状态")
        }
    }
    private fun needed(code: String) = setOf(code, "zh").filter { it != "en" }
    private fun showDownloadDialog(code: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("下载 ${title(code)}")
            .setMessage("将下载该语言及缺失的共用中文模型。请先确保手机代理可用；每个模型约 30 MB。")
            .setPositiveButton("仅 Wi-Fi 下载") { _, _ -> download(code, true) }
            .setNeutralButton("允许当前网络") { _, _ -> download(code, false) }
            .setNegativeButton("取消", null)
            .show()
    }
    private fun showDeleteDialog(code: String, removable: String) {
        val message = if (removable == "zh") {
            "删除共用中文模型后，所有离线中文翻译都需要重新下载。已缓存译文仍会保留。"
        } else "删除 ${title(code)} 语言包？已缓存译文仍会保留。"
        NativeUi.confirm(this, "删除语言包", message, "确认删除") {
            showStatus("正在删除语言包…")
            manager.deleteDownloadedModel(TranslateRemoteModel.Builder(removable).build())
                .addOnSuccessListener(this) { showStatus("语言包已删除", true); refreshModels() }
                .addOnFailureListener(this) { showStatus("删除失败，请停止离线翻译后重试") }
        }
    }
    private fun renderLanguages() {
        languages.removeAllViews()
        val query = search.text.toString().trim()
        val filtered = if (query.isBlank()) codes.filter { it in setOf("en", "ja", "ko", "zh") }
            else codes.filter { title(it).contains(query, true) }
        filtered.forEach { code ->
            val ready = downloaded.containsAll(needed(code))
            val label = title(code) + when {
                code in downloading -> " · 下载中…"
                ready -> " · 已下载"
                code in downloaded -> " · 缺中文模型"
                else -> " · 下载"
            }
            languages.addView(button(label) {
                if (ready || code in downloaded) {
                    val removable = if (code == "en") "zh" else code
                    showDeleteDialog(code, removable)
                } else {
                    showDownloadDialog(code)
                }
            }.apply { isEnabled = code !in downloading })
        }
    }
    private fun download(code: String, wifi: Boolean) {
        downloading += code
        downloadStartedAt[code] = System.currentTimeMillis()
        downloadProgress.visibility = View.VISIBLE
        showStatus("正在连接语言包服务…")
        downloadTicker.removeCallbacks(downloadTick)
        downloadTicker.post(downloadTick)
        renderLanguages()
        val timeout = Runnable {
            if (code in downloading) {
                downloading -= code
                downloadStartedAt.remove(code)
                downloadProgress.visibility = if (downloading.isEmpty()) View.GONE else View.VISIBLE
                showStatus("下载等待超过 2 分钟，请检查手机代理后重试；系统后台若继续完成，可点“刷新状态”确认")
                renderLanguages()
            }
            downloadTimeouts.remove(code)
        }
        downloadTimeouts[code]?.let(downloadTicker::removeCallbacks)
        downloadTimeouts[code] = timeout
        downloadTicker.postDelayed(timeout, 120_000)
        val conditions = DownloadConditions.Builder().apply { if (wifi) requireWifi() }.build()
        val tasks = needed(code).map { manager.download(TranslateRemoteModel.Builder(it).build(), conditions) }
        com.google.android.gms.tasks.Tasks.whenAll(tasks).addOnCompleteListener(this) { result ->
            downloadTimeouts.remove(code)?.let(downloadTicker::removeCallbacks)
            downloading -= code
            downloadStartedAt.remove(code)
            downloadProgress.visibility = if (downloading.isEmpty()) View.GONE else View.VISIBLE
            if (result.isSuccessful) showStatus("语言包已就绪，可以立即使用离线机翻", true)
            else showStatus("下载未完成，请确认手机当前网络可访问 Google ML Kit；电脑代理不会自动作用于手机")
            refreshModels()
            if (result.isSuccessful) LyricsOverlayService.instance?.refreshSupplementTranslation()
        }
    }
}
