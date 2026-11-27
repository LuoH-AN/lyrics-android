package com.luoh.music.lrc.ui.library

import androidx.compose.runtime.Composable
import com.luoh.music.lrc.ui.navigation.ComposeRoute

@Composable
internal fun LibraryDestination(route: ComposeRoute, onNavigate: (ComposeRoute) -> Unit, onBack: () -> Unit) {
    when (route.kind) {
        "translation" -> TranslationComposeScreen(onNavigate, onBack)
        "profiles" -> ApiProfilesComposeScreen(onBack)
        "offsets" -> OffsetMemoriesComposeScreen(onBack)
        "sources" -> SourcesComposeScreen(route, onNavigate, onBack)
        "custom" -> CustomLibraryComposeScreen(onNavigate, onBack)
        "cached" -> CachedLibraryComposeScreen(onNavigate, onBack)
        "editor", "cached-editor" -> CustomEditorComposeScreen(route, onBack)
        "privacy" -> PrivacyComposeScreen(onBack)
    }
}
