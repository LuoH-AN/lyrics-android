package com.luoh.music.lrc

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat

/** Load the shared variable family once; lyric rows do not decode fonts individually. */
object AppTypography {
    private var medium: Typeface? = null
    private var bold: Typeface? = null
    fun medium(context: Context): Typeface = medium ?: ResourcesCompat.getFont(context, R.font.app_font)
        .let { font -> if (Build.VERSION.SDK_INT >= 28) Typeface.create(font, 500, false)
            else Typeface.create(font, Typeface.NORMAL) }.also { medium = it }
    fun bold(context: Context): Typeface = bold ?: Typeface.create(
        ResourcesCompat.getFont(context, R.font.app_font), Typeface.BOLD
    ).also { bold = it }
}
