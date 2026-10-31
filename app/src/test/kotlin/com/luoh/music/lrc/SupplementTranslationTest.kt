package com.luoh.music.lrc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SupplementTranslationTest {
    @Test fun chineseSkipsBeforeLoadingAnyLanguageModelOrRequiringApiConfiguration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("supplement_translation", Context.MODE_PRIVATE)
        val translator = SupplementTranslation(context)
        for (mode in listOf("offline", "api")) {
            prefs.edit().clear().putString("mode", mode).commit()
            for (text in listOf("让歌词陪着你", "讓歌詞陪著你", "𠮷，2026！♪", "")) {
                val payload = JSONArray().put(JSONObject().put("id", 0).put("text", text)).toString()
                val result = translator.translate(payload, onTranslationRequired = { fail("No progress prompt for Chinese") }) {
                    fail("Do not emit a placeholder or duplicate Chinese translation")
                }
                assertEquals(SupplementOutcome.NOT_NEEDED, result)
            }
        }
    }
}
