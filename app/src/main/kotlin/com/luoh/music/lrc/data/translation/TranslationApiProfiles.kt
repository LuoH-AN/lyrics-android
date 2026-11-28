package com.luoh.music.lrc.data.translation

import android.content.Context
import com.luoh.music.lrc.R
import org.json.JSONArray
import org.json.JSONObject

data class TranslationApiProfile(
    val id: String,
    val label: String,
    val defaultEndpoint: String,
    val defaultModel: String
)

object TranslationApiProfiles {
    private val builtIns = listOf(
        TranslationApiProfile("glm", "Zhipu GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4.7-flash"),
        TranslationApiProfile("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-v4-flash"),
        TranslationApiProfile("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.1-flash-lite")
    )
    private const val CUSTOM_KEY = "custom_api_profiles"
    private const val DELETED_KEY = "deleted_api_profiles"
    private const val LABELS_KEY = "api_profile_labels"
    private fun custom(context: Context): List<TranslationApiProfile> = runCatching {
        val rows = JSONArray(context.translationPrefs().getString(CUSTOM_KEY, "[]"))
        (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let { row ->
                val id = row.optString("id")
                val label = row.optString("label")
                if (id.startsWith("custom_") && label.isNotBlank()) TranslationApiProfile(id, label, "", "") else null
            }
        }
    }.getOrDefault(emptyList())
    private fun deleted(context: Context) = context.translationPrefs().getStringSet(DELETED_KEY, emptySet()).orEmpty()
    private fun labels(context: Context): JSONObject = runCatching {
        JSONObject(context.translationPrefs().getString(LABELS_KEY, "{}").orEmpty())
    }.getOrDefault(JSONObject())
    fun all(context: Context): List<TranslationApiProfile> {
        val deleted = deleted(context)
        val labels = labels(context)
        return builtIns.filterNot { it.id in deleted }.map { profile ->
            val defaultLabel = if (profile.id == "glm") context.getString(R.string.profile_zhipu) else profile.label
            profile.copy(label = labels.optString(profile.id, defaultLabel))
        } + custom(context).map { profile ->
            profile.copy(label = labels.optString(profile.id, profile.label))
        }
    }
    fun find(context: Context, id: String?) = all(context).firstOrNull { it.id == id }
        ?: all(context).firstOrNull()
        ?: TranslationApiProfile("none", context.getString(R.string.no_api_configured), "", "")
    fun add(context: Context, label: String): TranslationApiProfile {
        val profile = TranslationApiProfile("custom_${System.currentTimeMillis()}", label.trim().ifBlank { context.getString(R.string.custom_api) }, "", "")
        val updated = custom(context) + profile
        saveCustom(context, updated)
        return profile
    }
    fun rename(context: Context, id: String, label: String) {
        val current = find(context, id)
        val value = label.trim().ifBlank { current.label }
        val labels = labels(context).put(id, value)
        context.translationPrefs().edit().putString(LABELS_KEY, labels.toString()).apply()
        if (id.startsWith("custom_")) {
            saveCustom(context, custom(context).map { if (it.id == id) it.copy(label = value) else it })
        }
    }
    fun remove(context: Context, id: String) {
        if (builtIns.any { it.id == id }) {
            context.translationPrefs().edit().putStringSet(DELETED_KEY, deleted(context) + id).apply()
        } else {
            saveCustom(context, custom(context).filterNot { it.id == id })
        }
        val labels = labels(context).apply { remove(id) }
        context.translationPrefs().edit().remove(endpointKey(id)).remove(modelKey(id)).apply()
        context.translationPrefs().edit().putString(LABELS_KEY, labels.toString()).apply()
        SecretStorage(context, id).save("")
    }
    private fun saveCustom(context: Context, values: List<TranslationApiProfile>) {
        val rows = JSONArray().apply { values.forEach { put(JSONObject().put("id", it.id).put("label", it.label)) } }
        context.translationPrefs().edit().putString(CUSTOM_KEY, rows.toString()).apply()
    }
    fun endpointKey(id: String) = "api_${id}_endpoint"
    fun modelKey(id: String) = "api_${id}_model"
}
