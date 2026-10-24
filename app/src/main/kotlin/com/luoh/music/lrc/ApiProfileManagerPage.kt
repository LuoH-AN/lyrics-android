package com.luoh.music.lrc

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

class ApiProfileManagerPage : NativePage() {
    private val prefs by lazy { getSharedPreferences("supplement_translation", Context.MODE_PRIVATE) }
    private lateinit var name: EditText
    private lateinit var endpoint: EditText
    private lateinit var model: EditText
    private lateinit var key: EditText
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var editorTitle: TextView
    private lateinit var save: MaterialButton
    private var editingId: String? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val content = NativeUi.screen(this, "API 配置")
        content.addView(NativeUi.text(uiContext, "可添加多套兼容 Chat Completions 的服务，并随时切换。", 12f))
        val editor = NativeUi.column(uiContext)
        editorTitle = NativeUi.text(uiContext, "添加 API", 19f)
        editor.addView(editorTitle)
        editor.addView(NativeUi.field(uiContext, "配置名称").also { name = it.editText!! })
        editor.addView(NativeUi.field(uiContext, "HTTPS 服务地址").also { endpoint = it.editText!! })
        editor.addView(NativeUi.field(uiContext, "模型名称").also { model = it.editText!! })
        editor.addView(NativeUi.field(uiContext, "API Key").also {
            key = it.editText!!
            key.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            it.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        })
        save = NativeUi.button(uiContext, "保存并使用", true) { saveProfile() }
        editor.addView(save)
        status = NativeUi.text(uiContext, "填写完整后保存；密钥会加密保存在本机。", 12f).apply { setPadding(0, NativeUi.dp(context, 10), 0, 0) }
        editor.addView(status)
        content.addView(NativeUi.card(uiContext).apply { addView(editor) })
        val saved = NativeUi.column(uiContext)
        saved.addView(NativeUi.text(uiContext, "API 配置", 19f))
        saved.addView(NativeUi.text(uiContext, "内置与自行添加的配置都可以编辑或删除。", 12f))
        list = NativeUi.column(uiContext, 0)
        saved.addView(list)
        content.addView(NativeUi.card(uiContext).apply { addView(saved) })
        renderProfiles()
    }

    private fun showStatus(message: String) {
        status.text = message
        status.alpha = 0f
        status.animate().alpha(1f).setDuration(180).start()
    }

    private fun saveProfile() {
        val label = name.text.toString().trim()
        val address = endpoint.text.toString().trim()
        val modelName = model.text.toString().trim()
        val secret = key.text.toString().trim()
        if (label.isBlank() || !address.startsWith("https://") || modelName.isBlank() ||
            (secret.isBlank() && editingId?.let { SecretStorage(uiContext, it).read().isBlank() } != false)) {
            showStatus("请填写名称、HTTPS 地址、模型和 API Key")
            return
        }
        val profile = editingId?.let { id ->
            TranslationApiProfiles.rename(uiContext, id, label)
            TranslationApiProfiles.find(uiContext, id)
        } ?: TranslationApiProfiles.add(uiContext, label)
        prefs.edit().putString(TranslationApiProfiles.endpointKey(profile.id), address)
            .putString(TranslationApiProfiles.modelKey(profile.id), modelName)
            .putString("active_api_profile", profile.id).putString("mode", "api").apply()
        if (secret.isNotBlank()) SecretStorage(uiContext, profile.id).save(secret)
        editingId = profile.id
        save.text = "保存修改并使用"
        editorTitle.text = "编辑 API 配置"
        showStatus("${profile.label} 已保存并切换使用")
        renderProfiles()
        LyricsOverlayService.instance?.refreshSupplementTranslation()
    }

    private fun edit(profile: TranslationApiProfile) {
        editingId = profile.id
        name.setText(profile.label)
        endpoint.setText(prefs.getString(TranslationApiProfiles.endpointKey(profile.id), ""))
        model.setText(prefs.getString(TranslationApiProfiles.modelKey(profile.id), ""))
        key.setText(SecretStorage(uiContext, profile.id).read())
        key.setSelection(key.text.length)
        save.text = "保存修改并使用"
        editorTitle.text = "编辑 API 配置"
        showStatus("正在编辑 ${profile.label}")
    }

    private fun renderProfiles() {
        list.removeAllViews()
        val profiles = TranslationApiProfiles.all(uiContext)
        if (profiles.isEmpty()) {
            list.addView(NativeUi.text(uiContext, "还没有 API 配置，请在上方添加", 13f))
            return
        }
        val activeId = prefs.getString("active_api_profile", "").orEmpty()
        profiles.forEach { profile ->
            list.addView(LinearLayout(uiContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = NativeUi.dp(context, 8) }
                addView(NativeUi.button(context, profile.label) { edit(profile) },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = NativeUi.dp(context, 6) })
                if (profile.id == activeId) {
                    addView(NativeUi.text(context, "使用中", 12f).apply {
                        setTextColor(AccentPrefs.textAccent(context, R.color.text_primary))
                        setPadding(0, 0, NativeUi.dp(context, 8), 0)
                    })
                }
                addView(NativeUi.button(context, "删除") { confirmDelete(profile) },
                    LinearLayout.LayoutParams(NativeUi.dp(context, 82), -2))
            })
        }
    }

    private fun confirmDelete(profile: TranslationApiProfile) {
        NativeUi.confirm(uiContext, "删除 ${profile.label}？", "地址、模型和本机加密密钥都会删除，此操作无法恢复。", "确认删除") {
            TranslationApiProfiles.remove(uiContext, profile.id)
            if (editingId == profile.id) clearEditor()
            if (prefs.getString("active_api_profile", "") == profile.id) {
                prefs.edit().putString("active_api_profile", TranslationApiProfiles.all(uiContext).firstOrNull()?.id ?: "none").apply()
            }
            showStatus("已删除 ${profile.label}")
            renderProfiles()
        }
    }

    private fun clearEditor() {
        editingId = null
        name.setText(""); endpoint.setText(""); model.setText(""); key.setText("")
        save.text = "保存并使用"
        editorTitle.text = "添加 API"
    }
}
