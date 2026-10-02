package com.luoh.music.lrc

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

class ApiProfileManagerActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("supplement_translation", Context.MODE_PRIVATE) }
    private lateinit var name: EditText
    private lateinit var endpoint: EditText
    private lateinit var model: EditText
    private lateinit var key: EditText
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var save: MaterialButton
    private var editingId: String? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val content = NativeUi.screen(this, "API 配置")
        content.addView(NativeUi.text(this, "可添加多套兼容 Chat Completions 的服务，并随时切换。", 12f))
        val editor = NativeUi.column(this)
        editor.addView(NativeUi.text(this, "添加 API", 19f))
        editor.addView(NativeUi.field(this, "配置名称").also { name = it.editText!! })
        editor.addView(NativeUi.field(this, "HTTPS 服务地址").also { endpoint = it.editText!! })
        editor.addView(NativeUi.field(this, "模型名称").also { model = it.editText!! })
        editor.addView(NativeUi.field(this, "API Key").also {
            key = it.editText!!
            key.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            it.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        })
        save = NativeUi.button(this, "保存并使用", true) { saveProfile() }
        editor.addView(save)
        status = NativeUi.text(this, "填写完整后保存；密钥会加密保存在本机。", 12f).apply { setPadding(0, NativeUi.dp(context, 10), 0, 0) }
        editor.addView(status)
        content.addView(NativeUi.card(this).apply { addView(editor) })
        val saved = NativeUi.column(this)
        saved.addView(NativeUi.text(this, "API 配置", 19f))
        saved.addView(NativeUi.text(this, "内置与自行添加的配置都可以编辑或删除。", 12f))
        list = NativeUi.column(this, 0)
        saved.addView(list)
        content.addView(NativeUi.card(this).apply { addView(saved) })
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
            (secret.isBlank() && editingId?.let { SecretStorage(this, it).read().isBlank() } != false)) {
            showStatus("请填写名称、HTTPS 地址、模型和 API Key")
            return
        }
        val profile = editingId?.let { id ->
            TranslationApiProfiles.rename(this, id, label)
            TranslationApiProfiles.find(this, id)
        } ?: TranslationApiProfiles.add(this, label)
        prefs.edit().putString(TranslationApiProfiles.endpointKey(profile.id), address)
            .putString(TranslationApiProfiles.modelKey(profile.id), modelName)
            .putString("active_api_profile", profile.id).putString("mode", "api").apply()
        if (secret.isNotBlank()) SecretStorage(this, profile.id).save(secret)
        editingId = profile.id
        save.text = "保存修改并使用"
        showStatus("${profile.label} 已保存并切换使用")
        renderProfiles()
        LyricsOverlayService.instance?.refreshSupplementTranslation()
    }

    private fun edit(profile: TranslationApiProfile) {
        editingId = profile.id
        name.setText(profile.label)
        endpoint.setText(prefs.getString(TranslationApiProfiles.endpointKey(profile.id), ""))
        model.setText(prefs.getString(TranslationApiProfiles.modelKey(profile.id), ""))
        key.setText(SecretStorage(this, profile.id).read())
        key.setSelection(key.text.length)
        save.text = "保存修改并使用"
        showStatus("正在编辑 ${profile.label}")
    }

    private fun renderProfiles() {
        list.removeAllViews()
        val profiles = TranslationApiProfiles.all(this)
        if (profiles.isEmpty()) {
            list.addView(NativeUi.text(this, "还没有 API 配置，请在上方添加", 13f))
            return
        }
        profiles.forEach { profile ->
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = NativeUi.dp(context, 8) }
                addView(NativeUi.button(context, profile.label) { edit(profile) },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = NativeUi.dp(context, 6) })
                addView(NativeUi.button(context, "删除") { confirmDelete(profile) },
                    LinearLayout.LayoutParams(NativeUi.dp(context, 82), -2))
            })
        }
    }

    private fun confirmDelete(profile: TranslationApiProfile) {
        NativeUi.confirm(this, "删除 ${profile.label}？", "地址、模型和本机加密密钥都会删除，此操作无法恢复。", "确认删除") {
            TranslationApiProfiles.remove(this, profile.id)
            if (editingId == profile.id) clearEditor()
            if (prefs.getString("active_api_profile", "") == profile.id) {
                prefs.edit().putString("active_api_profile", TranslationApiProfiles.all(this).firstOrNull()?.id ?: "none").apply()
            }
            showStatus("已删除 ${profile.label}")
            renderProfiles()
        }
    }

    private fun clearEditor() {
        editingId = null
        name.setText(""); endpoint.setText(""); model.setText(""); key.setText("")
        save.text = "保存并使用"
    }
}
