package com.luoh.music.lrc.overlay

import android.content.Context
import android.os.Handler
import com.luoh.music.lrc.data.translation.SupplementOutcome
import com.luoh.music.lrc.data.translation.SupplementTranslation
import com.luoh.music.lrc.data.translation.TranslationApiProfiles
import com.luoh.music.lrc.data.translation.translationPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException

internal class OverlaySupplement(
    private val context: Context,
    private val mainHandler: Handler,
    private val scope: CoroutineScope,
    private val onResult: (Int, JSONObject) -> Unit,
) {
    private val supplements by lazy { SupplementTranslation(context) }
    private var job: Job? = null
    private var generation = 0

    fun strategy(): String {
        val translationPrefs = context.translationPrefs()
        val profile = TranslationApiProfiles.find(
            context,
            translationPrefs.getString("active_api_profile", null)
        )
        val endpoint = translationPrefs.getString(
            TranslationApiProfiles.endpointKey(profile.id), profile.defaultEndpoint
        ).orEmpty().lowercase()
        val model = translationPrefs.getString(
            TranslationApiProfiles.modelKey(profile.id), profile.defaultModel
        ).orEmpty().lowercase()
        val (ahead, prefetch) = when {
            profile.id == "gemini" || "generativelanguage.googleapis.com" in endpoint || "gemini" in model -> 34 to 10
            profile.id == "glm" || "bigmodel.cn" in endpoint || model.startsWith("glm-") -> 22 to 7
            else -> 12 to 4
        }
        return JSONObject().put("behind", 2).put("ahead", ahead).put("prefetch", prefetch).toString()
    }

    fun cancel() {
        mainHandler.post {
            generation++
            job?.cancel()
        }
    }

    fun translate(requestId: Int, payload: String) {
        if (payload.length > 250000) return
        mainHandler.post {
            job?.cancel()
            val requestGeneration = ++generation
            fun report(data: JSONObject) {
                mainHandler.post {
                    if (requestGeneration == generation) onResult(requestId, data)
                }
            }
            job = scope.launch {
                try {
                    val mode = context.translationPrefs().getString("mode", "off")
                    if (mode == "off") {
                        report(JSONObject().put("status", "off"))
                        return@launch
                    }
                    val outcome = supplements.translate(payload,
                        onTranslationRequired = { report(JSONObject().put("status", "working")) }) { report(it) }
                    val status = when (outcome) {
                        SupplementOutcome.COMPLETE -> "done"
                        SupplementOutcome.NOT_NEEDED -> "skipped"
                        SupplementOutcome.DISABLED -> "off"
                    }
                    report(JSONObject().put("status", status))
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    val message = when (error) {
                        is IllegalArgumentException -> error.message.orEmpty().take(180)
                        is SocketTimeoutException -> "翻译连接超时，请检查网络或 API 服务"
                        is IOException -> "翻译服务网络连接失败：${error.message.orEmpty().take(120)}"
                        else -> "补充翻译暂不可用：${error.message.orEmpty().take(120)}"
                    }
                    report(JSONObject().put("status", "error").put("message", message))
                }
            }
        }
    }
}
