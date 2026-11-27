package com.luoh.music.lrc.ui.home.pop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

/** Ask Android for 120 Hz while the visible lyrics play, restoring the window on exit/pause. */
@Composable
internal fun PopDisplayRefresh(enabled: Boolean) {
    val view = LocalView.current
    val orientation = LocalConfiguration.current.orientation
    DisposableEffect(view, enabled, orientation) {
        val window = view.context.popActivity()?.window
        val display = view.display
        if (!enabled || window == null || display == null) {
            onDispose { }
        } else {
            val current = display.mode
            // Keep the physical resolution. Prefer 120 Hz, or the closest supported rate.
            val mode = display.supportedModes.filter {
                it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
            }.minByOrNull { abs(it.refreshRate - 120f) } ?: current
            val oldMode = window.attributes.preferredDisplayModeId
            val oldRate = window.attributes.preferredRefreshRate
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = mode.modeId
                preferredRefreshRate = mode.refreshRate
            }
            onDispose {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = oldMode
                    preferredRefreshRate = oldRate
                }
            }
        }
    }
}

internal fun Context.popActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        val base = context.baseContext
        if (base === context) return null
        context = base
    }
    return context as? Activity
}
