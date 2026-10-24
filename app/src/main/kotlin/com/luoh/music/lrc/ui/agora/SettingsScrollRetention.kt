// Adapted from newo-ether/Agora @ 239eaa93 (GPL-3.0). Package/resources changed for lyrics-android.
package com.luoh.music.lrc.ui.agora

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalSettingsScrollRetention = compositionLocalOf<SettingsScrollRetention?> { null }
internal val LocalSettingsScrollPath = staticCompositionLocalOf<List<Any?>> { emptyList() }
internal val LocalSettingsScrollRetentionActive = compositionLocalOf { true }

/** Retains only scroll owners, never page composition or editor drafts, for one Settings session. */
internal class SettingsScrollRetention {
    private val states = object : LinkedHashMap<Pair<List<Any?>, Boolean>, Any>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Pair<List<Any?>, Boolean>, Any>,
        ): Boolean = size > 64
    }

    fun scrollState(path: List<Any?>): ScrollState? = states[path to false] as? ScrollState
    fun lazyListState(path: List<Any?>): LazyListState? = states[path to true] as? LazyListState

    fun retain(path: List<Any?>, state: ScrollState) {
        states[path to false] = state
    }

    fun retain(path: List<Any?>, state: LazyListState) {
        states[path to true] = state
    }
}

@Composable
internal fun rememberSettingsScrollState(): ScrollState {
    val retention = LocalSettingsScrollRetention.current ?: return rememberScrollState()
    val path = LocalSettingsScrollPath.current
    // Each composition gets its own owner, even when Back interrupts an outgoing page's animation.
    val state = remember(retention, path) { ScrollState(retention.scrollState(path)?.value ?: 0) }
    if (LocalSettingsScrollRetentionActive.current) SideEffect { retention.retain(path, state) }
    return state
}

@Composable
internal fun rememberSettingsLazyListState(): LazyListState {
    val retention = LocalSettingsScrollRetention.current ?: return rememberLazyListState()
    val path = LocalSettingsScrollPath.current
    val state = remember(retention, path) {
        val previous = retention.lazyListState(path)
        LazyListState(
            firstVisibleItemIndex = previous?.firstVisibleItemIndex ?: 0,
            firstVisibleItemScrollOffset = previous?.firstVisibleItemScrollOffset ?: 0,
        )
    }
    if (LocalSettingsScrollRetentionActive.current) SideEffect { retention.retain(path, state) }
    return state
}
