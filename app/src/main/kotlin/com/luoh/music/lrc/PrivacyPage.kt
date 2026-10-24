package com.luoh.music.lrc

import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat

/** 分段的隐私说明页，替代原来一次性塞进对话框的长文本。 */
class PrivacyPage : NativePage() {
    override fun onCreate(state: Bundle?) {
        ThemePrefs.apply(
            getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE)
                .getString(ThemePrefs.KEY, ThemePrefs.FOLLOW)
        )
        super.onCreate(state)
        val content = NativeUi.screen(this, "隐私说明")
        content.addView(NativeUi.text(uiContext, "本应用如何处理你的数据", 12f))
        getString(R.string.privacy_summary).split("\n\n").forEach { paragraph ->
            val body = NativeUi.column(uiContext)
            body.addView(NativeUi.text(uiContext, paragraph, 14f).apply {
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setLineSpacing(NativeUi.dp(context, 4).toFloat(), 1f)
            })
            content.addView(NativeUi.card(uiContext).apply { addView(body) })
        }
    }
}
