package com.luoh.music.lrc

/** Skipping Chinese lyrics is a successful decision, not an empty translation response. */
enum class SupplementOutcome { COMPLETE, NOT_NEEDED, DISABLED }

internal object TranslationText {
    fun hasForeignLetters(text: String): Boolean = text.codePoints().anyMatch { codePoint ->
        Character.isLetter(codePoint) && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN
    }
}
