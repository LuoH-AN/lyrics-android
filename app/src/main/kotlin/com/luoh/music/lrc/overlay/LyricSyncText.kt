package com.luoh.music.lrc.overlay

import com.luoh.music.lrc.R
import android.content.Context
import java.text.NumberFormat
import kotlin.math.abs

internal object LyricSyncText {
    fun format(context: Context, offsetMs: Int): String {
        if (offsetMs == 0) return context.getString(R.string.no_adjustment)
        val seconds = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
            isGroupingUsed = false
        }.format(abs(offsetMs.toLong()) / 1000.0)
        return if (offsetMs > 0) context.getString(R.string.lyrics_sync_earlier, seconds) else context.getString(R.string.lyrics_sync_later, seconds)
    }
}
