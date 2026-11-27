package com.luoh.music.lrc.data.translation

import android.content.Context
import android.content.SharedPreferences

internal fun Context.translationPrefs(): SharedPreferences =
    getSharedPreferences("supplement_translation", Context.MODE_PRIVATE)
