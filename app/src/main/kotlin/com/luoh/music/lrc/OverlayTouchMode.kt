package com.luoh.music.lrc

import android.view.WindowManager

/** A locked lyric window passes every touch through, including its transparent areas. */
internal fun applyOverlayTouchMode(
    params: WindowManager.LayoutParams,
    locked: Boolean,
    maximumObscuringOpacity: Float?,
) {
    params.flags = if (locked) {
        params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    } else {
        params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    }
    params.alpha = if (locked && maximumObscuringOpacity != null) {
        maximumObscuringOpacity.coerceIn(0f, 1f)
    } else 1f
}
