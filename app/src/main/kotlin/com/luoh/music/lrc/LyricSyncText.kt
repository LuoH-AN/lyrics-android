package com.luoh.music.lrc

import java.util.Locale
import kotlin.math.abs

internal object LyricSyncText {
    fun format(offsetMs: Int): String {
        if (offsetMs == 0) return "未调整"
        val seconds = String.format(Locale.ROOT, "%.1f", abs(offsetMs) / 1000f)
        return if (offsetMs > 0) "已提前 $seconds 秒" else "已延后 $seconds 秒"
    }
}
