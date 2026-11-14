package com.luoh.music.lrc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Window changes live only while the home presentation is visible. */
@Composable
internal fun HomeImmersiveWindow(enabled: Boolean, keepAwake: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = view.context.popActivity()?.window
        if (!enabled || window == null) onDispose { } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousBehavior = controller.systemBarsBehavior
            val insets = ViewCompat.getRootWindowInsets(view)
            val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
            val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller.systemBarsBehavior = previousBehavior
                if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars())
                if (navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }
    DisposableEffect(view, enabled && keepAwake) {
        val old = view.keepScreenOn
        if (enabled && keepAwake) view.keepScreenOn = true
        onDispose { view.keepScreenOn = old }
    }
}
